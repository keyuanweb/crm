package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.announcement.CommentRequest;
import com.crm.dto.announcement.CommentResponse;
import com.crm.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口（037-announcements）。
 *
 * <p><b>1.5：本类刻意保留类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")}，不做接线。</b> 这是批 2
 * 里唯一一处"看清之后决定不动"的地方，理由需要写下来，免得后来者以为是漏改：
 *
 * <p>批 2 其余模块的形态是「类级门拦住的人，矩阵另有承诺」——那些模块有码可接。评论<b>没有</b>：字典里没有任何 {@code comment:*}
 * 码，菜单树里也没有对应菜单（评论挂在客户/线索/商机/工单详情里）。而撤掉这道门、又不设码的 后果是单向扩权：{@code CommentService}
 * 的实体可见性校验只保证"看得见就能评"，于是 VIEWER / MARKETING_* / ANALYST
 * 这些**一个写码都没有**的角色就获得了在可见实体下发言与删除自己评论的能力——矩阵从未承诺过这件事， 而它恰好是 1.5 要消灭的那类"未经授权的扩权"。
 *
 * <p>因此本类维持现状：这类级门不是数据保护（真正的控制在 Service 的 {@code checkEntityVisible} 与"仅作者或 ADMIN 可删"上），它挡住的只是 081
 * 的 SALES_REP / SUPPORT_AGENT 等角色——<b>这是一处已知的、待裁决的锁死</b>， 与"部门管理菜单指向
 * 403"同类：要么给评论建码族并明确授予名单，要么把这些角色从协作场景里排除，二选一， 不宜在权限接线里顺手决定。
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
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
