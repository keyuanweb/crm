/** 定时导出任务 Service 实现（079-scheduled-export）。 */
package com.crm.service;

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

  @Override
  public List<ScheduledExportResponse> getScheduledExports(Long userId) {
    List<ScheduledExport> entities =
        scheduledExportRepository.findByUserIdAndStatus(userId, "ACTIVE");
    List<ScheduledExportResponse> responses = new ArrayList<>();
    for (ScheduledExport entity : entities) {
      responses.add(toResponse(entity));
    }
    return responses;
  }

  @Override
  public ScheduledExportResponse getScheduledExport(Long id) {
    ScheduledExport entity = scheduledExportRepository.selectById(id);
    return entity != null ? toResponse(entity) : null;
  }

  @Override
  public void updateStatus(Long id, String status) {
    ScheduledExport entity = scheduledExportRepository.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("Scheduled export not found: " + id);
    }
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
    ScheduledExport entity = scheduledExportRepository.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("Scheduled export not found: " + id);
    }
    entity.setStatus("DELETED");
    entity.setUpdatedAt(LocalDateTime.now());
    scheduledExportRepository.updateById(entity);
    auditService.record("DELETE", "SCHEDULED_EXPORT", id, "Delete scheduled export");
    log.info("Deleted scheduled export: id={}", id);
  }

  @Override
  public List<ScheduledExportExecutionResponse> getExecutions(Long scheduledExportId) {
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
    ScheduledExport entity = scheduledExportRepository.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("Scheduled export not found: " + id);
    }
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
      String filePath =
          exportExecutor.executeExport(
              task.getEntityType(), task.getFilterConditions(), task.getExportFormat());
      execution.setFilePath(filePath);
      try {
        execution.setFileSize(java.nio.file.Files.size(java.nio.file.Paths.get(filePath)));
      } catch (Exception ex) {
        execution.setFileSize(0L);
      }
      execution.setRowCount(0); // TODO: 从 ExportExecutor 获取行数
      execution.setEmailStatus("EMAIL_SENT");

      // 发送邮件
      emailService.sendSimpleEmail(
          getUserEmail(task.getUserId()),
          "定时导出完成 - " + task.getEntityType(),
          "定时导出任务已完成，文件路径：" + filePath);

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
