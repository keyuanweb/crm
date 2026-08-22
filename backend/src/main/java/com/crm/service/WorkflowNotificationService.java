package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.PageResult;
import com.crm.dto.workflow.NotificationResponse;
import com.crm.entity.WorkflowNotification;
import com.crm.repository.WorkflowNotificationMapper;
import com.crm.security.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 站内通知服务（013，NOTIFY 动作产出）。 */
@Service
public class WorkflowNotificationService {

  private final WorkflowNotificationMapper notificationMapper;

  public WorkflowNotificationService(WorkflowNotificationMapper notificationMapper) {
    this.notificationMapper = notificationMapper;
  }

  /** 写入通知（供 WorkflowEngine 调用）。 */
  public void notify(Long userId, String message) {
    WorkflowNotification n = new WorkflowNotification();
    n.setUserId(userId);
    n.setMessage(message);
    n.setRead(false);
    notificationMapper.insert(n);
  }

  public PageResult<NotificationResponse> list(long page, long pageSize) {
    Long userId = SecurityUtil.currentUserId();
    Page<WorkflowNotification> p =
        notificationMapper.selectPage(
            new Page<>(page, pageSize),
            new LambdaQueryWrapper<WorkflowNotification>()
                .eq(WorkflowNotification::getUserId, userId)
                .orderByDesc(WorkflowNotification::getId));
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public void markRead(Long id) {
    Long userId = SecurityUtil.currentUserId();
    notificationMapper.update(
        null,
        new LambdaUpdateWrapper<WorkflowNotification>()
            .eq(WorkflowNotification::getId, id)
            .eq(WorkflowNotification::getUserId, userId)
            .set(WorkflowNotification::getRead, true));
  }

  private NotificationResponse toResponse(WorkflowNotification n) {
    NotificationResponse resp = new NotificationResponse();
    resp.setId(n.getId());
    resp.setMessage(n.getMessage());
    resp.setRead(n.getRead());
    resp.setCreatedAt(n.getCreatedAt());
    return resp;
  }
}
