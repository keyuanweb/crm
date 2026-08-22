package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.knowledge.ArticleRequest;
import com.crm.dto.knowledge.ArticleResponse;
import com.crm.service.KnowledgeArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 知识库接口（015，FR-C06~C08）。 */
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "客户服务")
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
public class KnowledgeArticleController {

  private final KnowledgeArticleService articleService;

  public KnowledgeArticleController(KnowledgeArticleService articleService) {
    this.articleService = articleService;
  }

  @GetMapping
  @Operation(summary = "文章分页列表（关键字/分类/状态筛选；客服管理员可含草稿）")
  public ApiResponse<PageResult<ArticleResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "false") boolean includeDraft,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(
        articleService.page(keyword, category, status, includeDraft, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "创建文章（默认草稿，ADMIN/SUPPORT）")
  public ApiResponse<ArticleResponse> create(@Valid @RequestBody ArticleRequest request) {
    return ApiResponse.ok(articleService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "编辑文章（ADMIN/SUPPORT）")
  public ApiResponse<ArticleResponse> update(
      @PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
    return ApiResponse.ok(articleService.update(id, request));
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "发布文章")
  public ApiResponse<ArticleResponse> publish(@PathVariable Long id) {
    return ApiResponse.ok(articleService.publish(id));
  }

  @PostMapping("/{id}/unpublish")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "下线文章")
  public ApiResponse<ArticleResponse> unpublish(@PathVariable Long id) {
    return ApiResponse.ok(articleService.unpublish(id));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
  @Operation(summary = "删除文章（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    articleService.delete(id);
    return ApiResponse.ok();
  }
}
