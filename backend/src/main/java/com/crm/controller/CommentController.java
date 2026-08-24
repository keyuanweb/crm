package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.announcement.CommentRequest;
import com.crm.dto.announcement.CommentResponse;
import com.crm.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 评论接口（037-announcements）。 */
@RestController
@RequestMapping("/api/v1/comments")
@Tag(name = "评论协作")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @GetMapping
  @Operation(summary = "评论列表")
  public ApiResponse<PageResult<CommentResponse>> list(
      @RequestParam String entityType,
      @RequestParam Long entityId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "50") long pageSize) {
    return ApiResponse.ok(commentService.list(entityType, entityId, page, pageSize));
  }

  @PostMapping
  @Operation(summary = "发表评论（@提及自动通知）")
  public ApiResponse<CommentResponse> create(@RequestBody CommentRequest request) {
    return ApiResponse.ok(commentService.create(request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除评论（作者/管理员）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    commentService.delete(id);
    return ApiResponse.ok(null);
  }
}
