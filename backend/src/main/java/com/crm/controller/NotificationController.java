package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.notification.NotificationResponse;
import com.crm.security.SecurityUtil;
import com.crm.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心接口（016，FR-S04~S06，仅本人）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 直接撤除，不设权限码。</b>这是批 2
 * 里唯一一个"什么都不接"的模块，理由有三：
 *
 * <p>① 它**本来就只对本人**——四个方法的入参第一项都是 {@code SecurityUtil.currentUserId()}，{@code markRead} 还会
 * 校验通知归属，越权读别人的通知在这条路径上不存在；② 字典里**没有任何 notification:* 码**，也没有对应的菜单
 * （通知是顶栏铃铛，不是菜单项），硬造一个码没有"矩阵承诺"可以兑现，只会多出一个没人勾得对的开关； ③ 而那道类级门**不是在保护数据，只是在按角色名字拦人**：SALES_MANAGER /
 * SALES_REP / SUPPORT_AGENT 等 081 角色因此连**自己的**未读角标都拉不到（顶栏永远显示 0），这是纯粹的锁死。
 *
 * <p>判据对照（见 {@code V80}/{@code V81} 的补授判据）：本类属于"自限范围"而非"无闸门"，撤门不会让任何人看到 别人的数据，因此无需按判据①②补码。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "系统增强")
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
