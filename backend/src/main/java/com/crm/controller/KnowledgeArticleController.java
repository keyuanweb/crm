package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.knowledge.ArticleRequest;
import com.crm.dto.knowledge.ArticleResponse;
import com.crm.security.RequirePermission;
import com.crm.service.KnowledgeArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/**
 * 知识库接口（015，FR-C06~C08）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 与五个写方法上的 {@code
 * hasAnyRole('ADMIN','SUPPORT')} 已移除。</b>粗粒度那层只认字面角色名，挡住的正是**客服本身**： SUPPORT_MANAGER /
 * SUPPORT_AGENT 在全部知识库接口上恒 403，而这两个角色的菜单里有「知识库」，V75 （第 173、187-188 行）也已把
 * knowledge:create/update/delete 授给它们——081 的客服角色模型在本模块 完全不可用。反向地，类级那层还放进了 SALES：SALES
 * 既无「知识库」菜单、也不持任何一个 knowledge 码。
 *
 * <p>写操作改挂动作码：create→{@code knowledge:create}、update→{@code knowledge:update}、 delete→{@code
 * knowledge:delete}；{@code /publish} 与 {@code /unpublish} 同样挂 {@code knowledge:update}——字典里没有
 * knowledge:publish，而「发布/下线就是改文章的状态」， 与 TicketController 的 transition 挂 ticket:update 是同一口径。
 *
 * <p><b>读接口挂 {@code knowledge:read}。</b>知识库按设计就是共享的参考材料，文章没有「归属客户 /
 * 归属销售」这类可划范围的维度，逐行数据范围在这里本就不适用，{@code KnowledgeArticleService.page} 也 确实没有任何数据范围过滤（无
 * resolveVisibleOwnerIds / EntityAccessService 之类的调用）——正因为没有
 * 范围可依赖，撤掉类级门而不设码就等于把整个知识库对全体登录用户开放，{@code includeDraft=true} 时连 未发布草稿一并带出。字典的 RoleConstants
 * 知识库组原本只有 create/update/delete，没有这个码，故 {@code knowledge:read} 已补入 {@code PERMISSION_DEFS}，授予门放行的
 * ADMIN/SALES/SUPPORT 与持有 'knowledge' 菜单的客服角色，只标在读接口上。
 *
 * <p>⚠️ 遗留角色 SUPPORT 此前**不持有**任何一个 knowledge 码（V46 只授了 customer:* / ticket:*），而它在
 * 改动前能建/改/删/发布文章——V80 已按「保留既有能力」的口径把 knowledge:create/update/delete 补授给它， 否则这次改动会把一个今天可用的能力变成 403。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "客户服务")
public class KnowledgeArticleController {

  private final KnowledgeArticleService articleService;

  public KnowledgeArticleController(KnowledgeArticleService articleService) {
    this.articleService = articleService;
  }

  @GetMapping
  @RequirePermission("knowledge:read")
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
  @RequirePermission("knowledge:create")
  @Operation(summary = "创建文章（默认草稿，ADMIN/SUPPORT）")
  public ApiResponse<ArticleResponse> create(@Valid @RequestBody ArticleRequest request) {
    return ApiResponse.ok(articleService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("knowledge:update")
  @Operation(summary = "编辑文章（ADMIN/SUPPORT）")
  public ApiResponse<ArticleResponse> update(
      @PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
    return ApiResponse.ok(articleService.update(id, request));
  }

  @PostMapping("/{id}/publish")
  @RequirePermission("knowledge:update")
  @Operation(summary = "发布文章")
  public ApiResponse<ArticleResponse> publish(@PathVariable Long id) {
    return ApiResponse.ok(articleService.publish(id));
  }

  @PostMapping("/{id}/unpublish")
  @RequirePermission("knowledge:update")
  @Operation(summary = "下线文章")
  public ApiResponse<ArticleResponse> unpublish(@PathVariable Long id) {
    return ApiResponse.ok(articleService.unpublish(id));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("knowledge:delete")
  @Operation(summary = "删除文章（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    articleService.delete(id);
    return ApiResponse.ok();
  }
}
