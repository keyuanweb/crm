package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.suggestion.SmartSuggestion;
import com.crm.dto.suggestion.SuggestionSummary;
import com.crm.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 智能建议接口（022-ai-assistant，contracts/smart-suggestions.md）。 */
@RestController
@RequestMapping("/api/v1/suggestions")
@Tag(name = "智能建议")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class SuggestionController {

  private final SuggestionService suggestionService;

  public SuggestionController(SuggestionService suggestionService) {
    this.suggestionService = suggestionService;
  }

  @GetMapping
  @Operation(summary = "智能建议列表（按优先级排序，上限 20）")
  public ApiResponse<Map<String, Object>> suggestions(
      @RequestParam(defaultValue = "20") int limit) {
    List<SmartSuggestion> items = suggestionService.suggestions(limit);
    return ApiResponse.ok(Map.of("items", items, "total", items.size()));
  }

  @PostMapping("/{type}/{entityId}/ignore")
  @Operation(summary = "标记建议为已忽略（当前用户）")
  public ApiResponse<Void> ignore(@PathVariable String type, @PathVariable Long entityId) {
    String entityType =
        switch (type) {
          case SmartSuggestion.TYPE_AT_RISK, SmartSuggestion.TYPE_FOLLOWUP -> "CUSTOMER";
          case SmartSuggestion.TYPE_STALLED -> "OPPORTUNITY";
          default -> "LEAD";
        };
    suggestionService.ignore(type, entityType, entityId);
    return ApiResponse.ok();
  }

  @GetMapping("/summary")
  @Operation(summary = "建议摘要计数（首页卡片）")
  public ApiResponse<SuggestionSummary> summary() {
    return ApiResponse.ok(suggestionService.summary());
  }
}
