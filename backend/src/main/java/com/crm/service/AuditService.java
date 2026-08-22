package com.crm.service;

import com.crm.entity.AuditLog;
import com.crm.entity.User;
import com.crm.repository.AuditLogMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 审计服务（FR-017）：记录关键操作元数据；记录失败不影响主流程。 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditLogMapper auditLogMapper;
  private final UserMapper userMapper;

  public AuditService(AuditLogMapper auditLogMapper, UserMapper userMapper) {
    this.auditLogMapper = auditLogMapper;
    this.userMapper = userMapper;
  }

  public void record(String action, String entityType, Long entityId, String detail) {
    try {
      AuditLog entry = new AuditLog();
      Long actorId = SecurityUtil.currentUserId();
      entry.setActorId(actorId);
      if (actorId != null) {
        User user = userMapper.selectById(actorId);
        entry.setActorName(user == null ? null : user.getUsername());
      }
      entry.setAction(action);
      entry.setEntityType(entityType);
      entry.setEntityId(entityId);
      entry.setDetail(detail);
      entry.setCreatedAt(LocalDateTime.now());
      auditLogMapper.insert(entry);
    } catch (Exception ex) {
      log.warn("Failed to write audit log: {}", ex.getMessage());
    }
  }
}
