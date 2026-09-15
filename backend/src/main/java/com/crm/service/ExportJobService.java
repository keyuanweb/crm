package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.export.ExportJobResponse;
import com.crm.dto.export.ExportRequest;
import com.crm.entity.ExportJob;
import com.crm.repository.ExportJobMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.stereotype.Service;

/** 导出任务服务（016，FR-S08~S10）：任务创建/状态/下载/异步执行。 */
@Service
public class ExportJobService {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_RUNNING = "RUNNING";
  public static final String STATUS_DONE = "DONE";
  public static final String STATUS_FAILED = "FAILED";

  /** 每用户保留导出记录条数上限。 */
  private static final long MAX_PER_USER = 50;

  private static final String EXPORT_DIR = "contract-files/exports";

  private final ExportJobMapper exportJobMapper;
  private final ExportExecutor exportExecutor;
  private final ObjectMapper objectMapper;
  private final AuditService auditService;

  /**
   * 导出在独立线程执行，而 {@code SecurityUtil} 读的是 {@code SecurityContextHolder}（默认 MODE_THREADLOCAL）—— 裸
   * {@code Executors.newFixedThreadPool} 里主体恒为 {@code null}，于是 063 的两处加固**双双退化为空转**： {@code
   * ExportExecutor.mask()} 原样返回（非 ADMIN 不脱敏）、{@code visibleOwnersOrNull()} 返回 null（=不做行级过滤）；
   * 本次新增的自定义字段 HIDDEN 过滤同理够不着。
   *
   * <p>用 {@link DelegatingSecurityContextExecutorService} 把**提交时**的上下文带进执行线程。定时导出 （{@code
   * ScheduledExportServiceImpl}）不在本机制覆盖内——它根本没有请求主体，另见待办。
   */
  private final ExecutorService executorService =
      new DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2));

  public ExportJobService(
      ExportJobMapper exportJobMapper,
      ExportExecutor exportExecutor,
      ObjectMapper objectMapper,
      AuditService auditService) {
    this.exportJobMapper = exportJobMapper;
    this.exportExecutor = exportExecutor;
    this.objectMapper = objectMapper;
    this.auditService = auditService;
  }

  /** 创建导出任务并提交异步执行。注意：不能加 @Transactional —— 事务未提交时异步线程读取不到 job（导出流程会找不到任务）。 */
  public ExportJobResponse create(ExportRequest req) {
    ExportJob job = new ExportJob();
    job.setExportType(req.getExportType());
    job.setFilter(toJson(req.getFilter()));
    job.setStatus(STATUS_PENDING);
    job.setCreatedBy(SecurityUtil.currentUserId());
    exportJobMapper.insert(job);
    auditService.record("EXPORT", "EXPORT_JOB", job.getId(), "创建导出任务：" + req.getExportType());
    cleanup(job.getCreatedBy());
    executorService.submit(() -> exportExecutor.execute(job.getId()));
    return toResponse(exportJobMapper.selectById(job.getId()));
  }

  public PageResult<ExportJobResponse> page(Long userId, long page, long pageSize) {
    LambdaQueryWrapper<ExportJob> qw =
        new LambdaQueryWrapper<ExportJob>()
            .eq(ExportJob::getCreatedBy, userId)
            .orderByDesc(ExportJob::getId);
    Page<ExportJob> p = exportJobMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  public ExportJob require(Long id) {
    ExportJob job = exportJobMapper.selectById(id);
    if (job == null) {
      throw new BusinessException(ErrorCode.EXPORT_NOT_FOUND);
    }
    return job;
  }

  /** 下载鉴权：仅创建人/ADMIN；未完成 → 409。 */
  public Path downloadPath(Long id) {
    ExportJob job = require(id);
    Long userId = SecurityUtil.currentUserId();
    String role =
        SecurityUtil.currentPrincipal() == null ? null : SecurityUtil.currentPrincipal().role();
    if (!job.getCreatedBy().equals(userId) && !"ADMIN".equals(role)) {
      throw new BusinessException(ErrorCode.EXPORT_FORBIDDEN);
    }
    if (!STATUS_DONE.equals(job.getStatus()) || job.getFilePath() == null) {
      throw new BusinessException(ErrorCode.EXPORT_NOT_READY);
    }
    return Paths.get(job.getFilePath());
  }

  public String exportDir() {
    return EXPORT_DIR;
  }

  private void cleanup(Long userId) {
    List<ExportJob> old =
        exportJobMapper.selectList(
            new LambdaQueryWrapper<ExportJob>()
                .eq(ExportJob::getCreatedBy, userId)
                .orderByDesc(ExportJob::getId)
                .last("LIMIT 50, 100"));
    if (!old.isEmpty()) {
      exportJobMapper.deleteBatchIds(old.stream().map(ExportJob::getId).toList());
    }
  }

  private String toJson(Map<String, Object> filter) {
    if (filter == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(filter);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
  }

  private ExportJobResponse toResponse(ExportJob job) {
    ExportJobResponse resp = new ExportJobResponse();
    resp.setId(job.getId());
    resp.setExportType(job.getExportType());
    resp.setStatus(job.getStatus());
    resp.setRowCount(job.getRowCount());
    resp.setErrorMessage(job.getErrorMessage());
    resp.setFileName(
        job.getFilePath() == null ? null : Paths.get(job.getFilePath()).getFileName().toString());
    resp.setCreatedAt(job.getCreatedAt());
    resp.setCompletedAt(job.getCompletedAt());
    return resp;
  }
}
