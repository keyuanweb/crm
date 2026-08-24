package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.announcement.AnnouncementRequest;
import com.crm.dto.announcement.AnnouncementResponse;
import com.crm.security.RequirePermission;
import com.crm.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 公告接口（037-announcements）。 */
@RestController
@RequestMapping("/api/v1/announcements")
@Tag(name = "公告")
public class AnnouncementController {

  private final AnnouncementService announcementService;

  public AnnouncementController(AnnouncementService announcementService) {
    this.announcementService = announcementService;
  }

  @GetMapping
  @Operation(summary = "公告列表（未过期 + 已读状态）")
  public ApiResponse<PageResult<AnnouncementResponse>> list(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "10") long pageSize) {
    return ApiResponse.ok(announcementService.list(page, pageSize));
  }

  @PostMapping
  @RequirePermission("announcement:manage")
  @Operation(summary = "发布公告")
  public ApiResponse<AnnouncementResponse> create(@RequestBody AnnouncementRequest request) {
    return ApiResponse.ok(announcementService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("announcement:manage")
  @Operation(summary = "编辑公告")
  public ApiResponse<AnnouncementResponse> update(
      @PathVariable Long id, @RequestBody AnnouncementRequest request) {
    return ApiResponse.ok(announcementService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("announcement:manage")
  @Operation(summary = "删除公告")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    announcementService.delete(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/{id}/read")
  @Operation(summary = "标记已读")
  public ApiResponse<Void> markRead(@PathVariable Long id) {
    announcementService.markRead(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/unread-count")
  @Operation(summary = "未读公告数")
  public ApiResponse<Long> unreadCount() {
    return ApiResponse.ok(announcementService.unreadCount());
  }
}
