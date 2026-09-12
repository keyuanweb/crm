package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.search.SearchResponse;
import com.crm.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索接口（032-global-search）。
 *
 * <p><b>本控制器刻意不声明任何权限码</b>（FR-G15 的显式决策，非遗漏）：既有 97 条权限码字典中<b>不存在任何</b> `search:*`
 * 条目——已按迁移文件逐条核对，全库无一命中。按 FR-G14"不得为此次修复新增任何权限码"，此处没有可声明的码；
 * 若为其临时造一个，则所有未持有该新码的用户（即几乎全部用户）搜索会立即失效，而这并非任何一条需求所要求的行为。
 *
 * <p><b>那越权由谁挡</b>：搜索结果的<b>数据范围在服务层按当前登录用户过滤</b>（{@code SearchService}，管理员除外），
 * 与其它列表端点同一套机制。也就是说，此处"不设操作级门禁"不等于"任何人可搜到任何数据"：门禁管的是"能不能用搜索"， 数据范围管的是"能搜到什么"，后者已经生效。
 *
 * <p>该决策由 {@code SecurityHardeningIT#endpointWithoutPermissionCodeMustStayAccessible} 从黑盒方向钉住
 * （搜索端点对无 `search:*` 权限码的用户<b>必须仍可访问</b>），避免后人误以为此处漏加注解而"好心"补上。
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "全局搜索")
public class SearchController {

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping
  @Operation(summary = "下拉即时搜索（各实体 Top 5）")
  public ApiResponse<SearchResponse> search(@RequestParam String keyword) {
    return ApiResponse.ok(searchService.search(keyword));
  }

  @GetMapping("/full")
  @Operation(summary = "搜索结果页（按类型过滤分页）")
  public ApiResponse<SearchResponse> searchFull(
      @RequestParam String keyword, @RequestParam(required = false) String type) {
    return ApiResponse.ok(searchService.searchFull(keyword, type));
  }
}
