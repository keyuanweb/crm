package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.PageResult;
import com.crm.dto.audit.AuditLogResponse;
import com.crm.entity.AuditLog;
import com.crm.repository.AuditLogMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 审计日志查询服务（FR-017：仅管理员可查）。 */
@Service
public class AuditLogService {

  private final AuditLogMapper auditLogMapper;

  public AuditLogService(AuditLogMapper auditLogMapper) {
    this.auditLogMapper = auditLogMapper;
  }

  public PageResult<AuditLogResponse> page(
      String action, String entityType, String actorName, long page, long pageSize) {
    LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(action)) {
      qw.eq(AuditLog::getAction, action.trim().toUpperCase());
    }
    if (StringUtils.hasText(entityType)) {
      qw.eq(AuditLog::getEntityType, entityType.trim().toUpperCase());
    }
    if (StringUtils.hasText(actorName)) {
      qw.like(AuditLog::getActorName, actorName.trim());
    }
    qw.orderByDesc(AuditLog::getId);
    Page<AuditLog> p = auditLogMapper.selectPage(new Page<>(page, pageSize), qw);
    List<AuditLogResponse> items = p.getRecords().stream().map(this::toResponse).toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  private AuditLogResponse toResponse(AuditLog entry) {
    AuditLogResponse resp = new AuditLogResponse();
    resp.setId(entry.getId());
    resp.setActorId(entry.getActorId());
    resp.setActorName(entry.getActorName());
    resp.setAction(entry.getAction());
    resp.setEntityType(entry.getEntityType());
    resp.setEntityId(entry.getEntityId());
    resp.setDetail(entry.getDetail());
    resp.setCreatedAt(entry.getCreatedAt());
    return resp;
  }
}
