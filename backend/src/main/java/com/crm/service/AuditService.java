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

  /** 系统主体在 audit_log.actor_id 上的哨兵值；真实用户 id 自增从 1 开始，不会碰撞。 */
  private static final Long SYSTEM_ACTOR_ID = 0L;

  private static final String SYSTEM_ACTOR_NAME = "system";

  private final AuditLogMapper auditLogMapper;
  private final UserMapper userMapper;

  public AuditService(AuditLogMapper auditLogMapper, UserMapper userMapper) {
    this.auditLogMapper = auditLogMapper;
    this.userMapper = userMapper;
  }

  public void record(String action, String entityType, Long entityId, String detail) {
    write(SecurityUtil.currentUserId(), null, action, entityType, entityId, detail);
  }

  /**
   * 以「系统」为主体写审计（1.3-sla-escalation）。
   *
   * <p><b>为什么需要它</b>：调度线程没有 SecurityContext，{@link #record} 取到的 actorId 为 {@code null}，会产生
   * <b>无主体的审计行</b>——既无法归因到人，也无法与「用户被删除后 actor_id 悬空」区分。
   *
   * <p><b>为什么用哨兵 0 而不是 NULL</b>：{@code actor_id} 是 BIGINT DEFAULT NULL（无外键），0 不可能与真实用户 id 碰撞（自增主键从
   * 1 开始），且在审计列表里明确显示为 system。detail 前缀 {@code [system]} 让直接读表的人也能一眼分辨。
   */
  public void recordAsSystem(String action, String entityType, Long entityId, String detail) {
    write(SYSTEM_ACTOR_ID, SYSTEM_ACTOR_NAME, action, entityType, entityId, "[system] " + detail);
  }

  private void write(
      Long actorId,
      String actorName,
      String action,
      String entityType,
      Long entityId,
      String detail) {
    try {
      AuditLog entry = new AuditLog();
      entry.setActorId(actorId);
      if (actorName != null) {
        entry.setActorName(actorName);
      } else if (actorId != null) {
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
