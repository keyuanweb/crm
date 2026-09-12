package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.notification.NotificationResponse;
import com.crm.entity.Notification;
import com.crm.repository.NotificationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 通知服务（016，FR-S04~S07）：列表/已读/未读计数/写入/清理。 */
@Service
public class NotificationService {

  public static final String TYPE_WORKFLOW = "WORKFLOW";
  public static final String TYPE_TICKET_ASSIGN = "TICKET_ASSIGN";
  public static final String TYPE_TICKET_REPLY = "TICKET_REPLY";

  /** SLA 即将超时（1.3-sla-escalation，升级 L1）。 */
  public static final String TYPE_SLA_WARNING = "SLA_WARNING";

  /** SLA 已超时/持续超时（1.3-sla-escalation，升级 L2 及以上）。 */
  public static final String TYPE_SLA_OVERDUE = "SLA_OVERDUE";

  /** 每用户保留通知条数上限。 */
  private static final long MAX_PER_USER = 100;

  private final NotificationMapper notificationMapper;
  private final com.crm.ws.NotificationWebSocketHandler webSocketHandler;

  public NotificationService(
      NotificationMapper notificationMapper,
      com.crm.ws.NotificationWebSocketHandler webSocketHandler) {
    this.notificationMapper = notificationMapper;
    this.webSocketHandler = webSocketHandler;
  }

  public PageResult<NotificationResponse> page(Long userId, String type, long page, long pageSize) {
    LambdaQueryWrapper<Notification> qw =
        new LambdaQueryWrapper<Notification>()
            .eq(Notification::getUserId, userId)
            .orderByDesc(Notification::getRead)
            .orderByDesc(Notification::getId);
    if (StringUtils.hasText(type)) {
      qw.eq(Notification::getType, type.trim());
    }
    Page<Notification> p = notificationMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  public long unreadCount(Long userId) {
    Long count =
        notificationMapper.selectCount(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getRead, 0));
    return count == null ? 0 : count;
  }

  @Transactional
  public void markRead(Long userId, Long id) {
    Notification notification = require(id);
    if (!notification.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    notification.setRead(1);
    notificationMapper.updateById(notification);
  }

  @Transactional
  public long markAllRead(Long userId) {
    Notification update = new Notification();
    update.setRead(1);
    return notificationMapper.update(
        update,
        new LambdaQueryWrapper<Notification>()
            .eq(Notification::getUserId, userId)
            .eq(Notification::getRead, 0));
  }

  /** 写入通知（内部调用），并实时推送给目标用户（026）。 */
  public void notify(Long userId, String type, String message, String entityType, Long entityId) {
    if (userId == null) {
      return;
    }
    Notification notification = new Notification();
    notification.setUserId(userId);
    notification.setType(type);
    notification.setMessage(message);
    notification.setRead(0);
    notification.setEntityType(entityType);
    notification.setEntityId(entityId);
    notification.setCreatedAt(LocalDateTime.now());
    notificationMapper.insert(notification);
    cleanup(userId);
    // 026：WebSocket 实时推送（未读计数）
    try {
      webSocketHandler.notifyUser(
          userId,
          new com.crm.ws.NotificationPushPayload(
              notification.getId(), type, message, unreadCount(userId)));
    } catch (Exception ex) {
      // 推送失败不影响通知落库
    }
  }

  /** 清理：每用户保留最近 MAX_PER_USER 条。 */
  private void cleanup(Long userId) {
    List<Notification> old =
        notificationMapper.selectList(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getId)
                .last("LIMIT 1000, 1000"));
    if (!old.isEmpty()) {
      notificationMapper.deleteBatchIds(old.stream().map(Notification::getId).toList());
    }
  }

  private Notification require(Long id) {
    Notification notification = notificationMapper.selectById(id);
    if (notification == null) {
      throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
    return notification;
  }

  private NotificationResponse toResponse(Notification notification) {
    NotificationResponse resp = new NotificationResponse();
    resp.setId(notification.getId());
    resp.setType(notification.getType());
    resp.setMessage(notification.getMessage());
    resp.setRead(notification.getRead() != null && notification.getRead() == 1);
    resp.setEntityType(notification.getEntityType());
    resp.setEntityId(notification.getEntityId());
    resp.setCreatedAt(notification.getCreatedAt());
    return resp;
  }
}
