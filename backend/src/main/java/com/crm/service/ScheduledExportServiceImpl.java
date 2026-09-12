/** 定时导出任务 Service 实现（079-scheduled-export）。 */
package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.ScheduledExportExecutionResponse;
import com.crm.dto.ScheduledExportRequest;
import com.crm.dto.ScheduledExportResponse;
import com.crm.entity.User;
import com.crm.model.entity.ScheduledExport;
import com.crm.model.entity.ScheduledExportExecution;
import com.crm.repository.ScheduledExportExecutionRepository;
import com.crm.repository.ScheduledExportRepository;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScheduledExportServiceImpl implements ScheduledExportService {

  private static final Logger log = LoggerFactory.getLogger(ScheduledExportServiceImpl.class);

  private final ScheduledExportRepository scheduledExportRepository;
  private final ScheduledExportExecutionRepository scheduledExportExecutionRepository;
  private final EmailService emailService;
  private final ExportExecutor exportExecutor;
  private final AuditService auditService;
  private final UserMapper userMapper;

  public ScheduledExportServiceImpl(
      ScheduledExportRepository scheduledExportRepository,
      ScheduledExportExecutionRepository scheduledExportExecutionRepository,
      EmailService emailService,
      ExportExecutor exportExecutor,
      AuditService auditService,
      UserMapper userMapper) {
    this.scheduledExportRepository = scheduledExportRepository;
    this.scheduledExportExecutionRepository = scheduledExportExecutionRepository;
    this.emailService = emailService;
    this.exportExecutor = exportExecutor;
    this.auditService = auditService;
    this.userMapper = userMapper;
  }

  /** 从 SecurityContext 获取当前用户 ID。 */
  private Long currentUserId() {
    var principal = SecurityUtil.currentPrincipal();
    if (principal == null) {
      throw new IllegalStateException("User not authenticated");
    }
    return principal.userId();
  }

  /** 获取用户邮箱。 */
  private String getUserEmail(Long userId) {
    User user = userMapper.selectById(userId);
    return user != null && user.getEmail() != null ? user.getEmail() : "admin@example.com";
  }

  @Override
  public ScheduledExportResponse createScheduledExport(ScheduledExportRequest request) {
    ScheduledExport entity = new ScheduledExport();
    entity.setUserId(currentUserId());
    entity.setEntityType(request.getEntityType());
    entity.setFilterConditions(request.getFilterConditions());
    entity.setExportFormat(request.getExportFormat());
    entity.setCronExpression(request.getCronExpression());
    entity.setStatus("ACTIVE");
    entity.setNextExecutionTime(calculateNextExecutionTime(request.getCronExpression()));
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());

    scheduledExportRepository.insert(entity);
    auditService.record(
        "CREATE",
        "SCHEDULED_EXPORT",
        entity.getId(),
        "Create: entityType=" + entity.getEntityType() + ", cron=" + entity.getCronExpression());
    log.info(
        "Created scheduled export: id={}, entityType={}, cron={}",
        entity.getId(),
        entity.getEntityType(),
        entity.getCronExpression());
    return toResponse(entity);
  }

  /**
   * 定位任务并判定归属（FR-G16）。
   *
   * <p><b>为什么"以空结果代替拒绝"不可接受</b>：空列表与"确实没有数据"无法区分，调用方会把越权当成"暂无任务"， 越权尝试在界面与日志里都不留痕迹。故此处显式失败。
   *
   * <p><b>为什么不做 ADMIN 例外</b>：FR-G16 要求"按当前登录用户限定范围"，未留例外；而管理员的可用性并未因此受损—— 授予 `export:scheduled`
   * 只决定"能不能用这个功能"，看不看得到他人的任务由这里决定。若确需跨用户查看， 应先有一条明确需求再开此口子，而不是在越权判定里顺手放行（那等于把本 FR 关掉的那类读取从管理侧重新打开）。
   *
   * <p><b>覆盖写／执行路径（T063，2026-09-12）</b>：本方法原先只服务三处读取（列表、详情、执行历史）。三个写／执行端点——{@code
   * updateStatus}、{@code deleteScheduledExport}、{@code executeNow}——各自 {@code selectById}
   * 后直接操作，只判"存在"不判"归属"；而权限码 {@code export:scheduled}
   * 只回答"能不能用这个功能"，不回答"能不能动别人的任务"，于是唯一拦截退化为"界面只展示本人任务"。现已统一走本方法。
   *
   * <p><b>一处有意的契约细化</b>：这三个端点在"任务不存在"时原抛 {@link IllegalArgumentException}（全局处理器映为 400
   * 通用错误、无错误码），现与同资源的读端点统一为 404 {@code EXPORT_NOT_FOUND}。属可观测的行为变更，已由 {@code
   * ScheduledExportServiceTest.writeEndpointsReportNotFound} 钉住，非遗漏。
   */
  private ScheduledExport requireOwned(Long id) {
    ScheduledExport entity = scheduledExportRepository.selectById(id);
    if (entity == null) {
      throw new BusinessException(ErrorCode.EXPORT_NOT_FOUND);
    }
    if (!currentUserId().equals(entity.getUserId())) {
      throw new BusinessException(ErrorCode.EXPORT_FORBIDDEN);
    }
    return entity;
  }

  @Override
  public List<ScheduledExportResponse> getScheduledExports(Long userId) {
    Long callerId = currentUserId();
    // 参数值不再参与过滤：过滤一律以服务端身份为准（FR-G16）。改造前这里直接用传入的 userId 查库，
    // 任何已认证用户传他人标识即可读到他人订阅——参数本身必须假定为不可信输入。
    // 不符时显式拒绝而非静默忽略：静默忽略会让"客户端传错 userId"这类缺陷长期不可见（列表看起来总是对的）。
    if (!callerId.equals(userId)) {
      throw new BusinessException(ErrorCode.EXPORT_FORBIDDEN);
    }
    List<ScheduledExport> entities =
        scheduledExportRepository.findByUserIdAndStatus(callerId, "ACTIVE");
    List<ScheduledExportResponse> responses = new ArrayList<>();
    for (ScheduledExport entity : entities) {
      responses.add(toResponse(entity));
    }
    return responses;
  }

  @Override
  public ScheduledExportResponse getScheduledExport(Long id) {
    return toResponse(requireOwned(id));
  }

  @Override
  public void updateStatus(Long id, String status) {
    ScheduledExport entity = requireOwned(id);
    entity.setStatus(status);
    if ("ACTIVE".equals(status)) {
      entity.setNextExecutionTime(calculateNextExecutionTime(entity.getCronExpression()));
    }
    entity.setUpdatedAt(LocalDateTime.now());
    scheduledExportRepository.updateById(entity);
    log.info("Updated scheduled export status: id={}, status={}", id, status);
  }

  @Override
  public void deleteScheduledExport(Long id) {
    ScheduledExport entity = requireOwned(id);
    entity.setStatus("DELETED");
    entity.setUpdatedAt(LocalDateTime.now());
    scheduledExportRepository.updateById(entity);
    auditService.record("DELETE", "SCHEDULED_EXPORT", id, "Delete scheduled export");
    log.info("Deleted scheduled export: id={}", id);
  }

  @Override
  public List<ScheduledExportExecutionResponse> getExecutions(Long scheduledExportId) {
    // 先定归属再取记录：执行记录里有文件路径与行数，越权读取的后果不比详情轻（FR-G16）
    requireOwned(scheduledExportId);
    List<ScheduledExportExecution> entities =
        scheduledExportExecutionRepository.findByScheduledExportIdOrderByExecutedAtDesc(
            scheduledExportId);
    List<ScheduledExportExecutionResponse> responses = new ArrayList<>();
    for (ScheduledExportExecution entity : entities) {
      responses.add(toExecutionResponse(entity));
    }
    return responses;
  }

  @Override
  public void executeNow(Long id) {
    // 归属判定必须排在"是否 ACTIVE"之前：否则非属主可从错误类型的差异（可执行 → 走完导出、
    // 不可执行 → IllegalStateException 映成 400）反推该任务的当前状态，而任务状态本身也是
    // 他人信息的一部分。越权判定一律先于任何与实体状态相关的分支。
    ScheduledExport entity = requireOwned(id);
    if (!"ACTIVE".equals(entity.getStatus())) {
      throw new IllegalStateException("Scheduled export is not active: " + id);
    }
    executeExport(entity);
  }

  @Override
  public void executePendingTasks() {
    List<ScheduledExport> pendingTasks =
        scheduledExportRepository.findByStatusAndNextExecutionTimeLessThanEqual(
            "ACTIVE", LocalDateTime.now());
    for (ScheduledExport task : pendingTasks) {
      try {
        executeExport(task);
      } catch (Exception e) {
        log.error("Failed to execute scheduled export: id={}", task.getId(), e);
      }
    }
  }

  private void executeExport(ScheduledExport task) {
    // Create execution record
    ScheduledExportExecution execution = new ScheduledExportExecution();
    execution.setScheduledExportId(task.getId());
    execution.setExecutedAt(LocalDateTime.now());
    execution.setStatus("SUCCESS");
    execution.setCreatedAt(LocalDateTime.now());

    try {
      // 复用 016 导出逻辑
      String[] result =
          exportExecutor.executeExportWithRowCount(
              task.getEntityType(), task.getFilterConditions(), task.getExportFormat());
      String filePath = result[0];
      String rowCount = result[1];
      execution.setFilePath(filePath);
      try {
        execution.setFileSize(java.nio.file.Files.size(java.nio.file.Paths.get(filePath)));
      } catch (Exception ex) {
        execution.setFileSize(0L);
      }
      execution.setRowCount(Integer.parseInt(rowCount));

      // 通知邮件（一期诚信修复）：真正发出后才记 EMAIL_SENT；邮件是通知而非导出的一部分，
      // 因此单独 try/catch —— SMTP 未配置或发送异常都不应把导出本身判为失败。
      try {
        emailService.sendSimpleEmail(
            getUserEmail(task.getUserId()),
            "定时导出完成 - " + task.getEntityType(),
            "定时导出任务已完成，文件路径：" + filePath);
        execution.setEmailStatus("EMAIL_SENT");
      } catch (Exception mailEx) {
        String mailStatus = emailService.isConfigured() ? "EMAIL_FAILED" : "EMAIL_SKIPPED";
        execution.setEmailStatus(mailStatus);
        log.warn("定时导出通知邮件未发送（{}）: taskId={}, {}", mailStatus, task.getId(), mailEx.getMessage());
      }

      scheduledExportExecutionRepository.insert(execution);

      // Update next execution time
      task.setNextExecutionTime(calculateNextExecutionTime(task.getCronExpression()));
      task.setUpdatedAt(LocalDateTime.now());
      scheduledExportRepository.updateById(task);

      log.info(
          "Executed scheduled export: id={}, rowCount={}", task.getId(), execution.getRowCount());
    } catch (Exception e) {
      execution.setStatus("FAILED");
      execution.setErrorMessage(e.getMessage());
      scheduledExportExecutionRepository.insert(execution);
      log.error("Failed to execute scheduled export: id={}", task.getId(), e);
      throw e;
    }
  }

  private LocalDateTime calculateNextExecutionTime(String cronExpression) {
    try {
      // 使用 Spring 的 CronExpression 解析 cron 表达式
      org.springframework.scheduling.support.CronExpression cron =
          org.springframework.scheduling.support.CronExpression.parse(cronExpression);
      return cron.next(java.time.ZonedDateTime.now()).toLocalDateTime();
    } catch (Exception e) {
      log.warn("Invalid cron expression: {}, using default +1 day", cronExpression);
      return LocalDateTime.now().plusDays(1);
    }
  }

  private ScheduledExportResponse toResponse(ScheduledExport entity) {
    ScheduledExportResponse response = new ScheduledExportResponse();
    response.setId(entity.getId());
    response.setUserId(entity.getUserId());
    response.setEntityType(entity.getEntityType());
    response.setFilterConditions(entity.getFilterConditions());
    response.setExportFormat(entity.getExportFormat());
    response.setCronExpression(entity.getCronExpression());
    response.setStatus(entity.getStatus());
    response.setNextExecutionTime(entity.getNextExecutionTime());
    response.setCreatedAt(entity.getCreatedAt());
    response.setUpdatedAt(entity.getUpdatedAt());
    return response;
  }

  private ScheduledExportExecutionResponse toExecutionResponse(ScheduledExportExecution entity) {
    ScheduledExportExecutionResponse response = new ScheduledExportExecutionResponse();
    response.setId(entity.getId());
    response.setScheduledExportId(entity.getScheduledExportId());
    response.setExecutedAt(entity.getExecutedAt());
    response.setStatus(entity.getStatus());
    response.setFilePath(entity.getFilePath());
    response.setFileSize(entity.getFileSize());
    response.setRowCount(entity.getRowCount());
    response.setEmailStatus(entity.getEmailStatus());
    response.setErrorMessage(entity.getErrorMessage());
    response.setCreatedAt(entity.getCreatedAt());
    return response;
  }
}
