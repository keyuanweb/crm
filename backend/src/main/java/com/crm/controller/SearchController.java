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

/** 全局搜索接口（032-global-search）。 */
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
