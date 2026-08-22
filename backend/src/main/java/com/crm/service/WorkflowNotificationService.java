package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.PageResult;
import com.crm.dto.workflow.NotificationResponse;
import com.crm.entity.Notification;
import com.crm.repository.NotificationMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内通知服务（013，NOTIFY 动作产出）。016 起统一写入 notification 表（type=WORKFLOW）， 保持 013 的
 * /workflows/notifications 接口兼容。
 */
@Service
public class WorkflowNotificationService {

  private final NotificationMapper notificationMapper;

  public WorkflowNotificationService(NotificationMapper notificationMapper) {
    this.notificationMapper = notificationMapper;
  }

  /** 写入通知（供 WorkflowEngine 调用，类型 WORKFLOW）。 */
  public void notify(Long userId, String message) {
    if (userId == null) {
      return;
    }
    Notification n = new Notification();
    n.setUserId(userId);
    n.setType(NotificationService.TYPE_WORKFLOW);
    n.setMessage(message);
    n.setRead(0);
    n.setCreatedAt(LocalDateTime.now());
    notificationMapper.insert(n);
  }

  public PageResult<NotificationResponse> list(long page, long pageSize) {
    Long userId = SecurityUtil.currentUserId();
    Page<Notification> p =
        notificationMapper.selectPage(
            new Page<>(page, pageSize),
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, NotificationService.TYPE_WORKFLOW)
                .orderByDesc(Notification::getId));
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public void markRead(Long id) {
    Long userId = SecurityUtil.currentUserId();
    notificationMapper.update(
        null,
        new LambdaUpdateWrapper<Notification>()
            .eq(Notification::getId, id)
            .eq(Notification::getUserId, userId)
            .set(Notification::getRead, 1));
  }

  private NotificationResponse toResponse(Notification n) {
    NotificationResponse resp = new NotificationResponse();
    resp.setId(n.getId());
    resp.setMessage(n.getMessage());
    resp.setRead(n.getRead() != null && n.getRead() == 1);
    resp.setCreatedAt(n.getCreatedAt());
    return resp;
  }
}
