package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.notification.NotificationResponse;
import com.crm.security.SecurityUtil;
import com.crm.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通知中心接口（016，FR-S04~S06，仅本人）。 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "系统增强")
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  @Operation(summary = "通知分页列表（未读优先，可按类型筛选）")
  public ApiResponse<PageResult<NotificationResponse>> page(
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(
        notificationService.page(SecurityUtil.currentUserId(), type, page, pageSize));
  }

  @GetMapping("/unread-count")
  @Operation(summary = "未读计数（顶栏角标）")
  public ApiResponse<Map<String, Long>> unreadCount() {
    return ApiResponse.ok(
        Map.of("unreadCount", notificationService.unreadCount(SecurityUtil.currentUserId())));
  }

  @PostMapping("/{id}/read")
  @Operation(summary = "标记单条已读")
  public ApiResponse<Void> markRead(@PathVariable Long id) {
    notificationService.markRead(SecurityUtil.currentUserId(), id);
    return ApiResponse.ok();
  }

  @PostMapping("/read-all")
  @Operation(summary = "全部标记已读")
  public ApiResponse<Map<String, Long>> markAllRead() {
    long updated = notificationService.markAllRead(SecurityUtil.currentUserId());
    return ApiResponse.ok(Map.of("updated", updated));
  }
}
