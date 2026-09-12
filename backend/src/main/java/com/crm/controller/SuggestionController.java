package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.suggestion.SmartSuggestion;
import com.crm.dto.suggestion.SuggestionSummary;
import com.crm.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能建议接口（022-ai-assistant，contracts/smart-suggestions.md）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 撤除，不设权限码。</b>与通知中心同一形态： ①
 * 建议列表的全部数据来自 {@code CustomerService.atRiskCustomers} / {@code FollowUpMapper} / {@code
 * SalesOpportunityMapper} / {@code LeadMapper}，前三者本身已按数据范围过滤，第四条线索同样受角色的可见范围 约束——撤门不会让任何人看到范围外的数据；②
 * 字典里没有任何 {@code suggestion:*} 码，也没有"智能建议"之外可对应的 菜单动作，硬造一个只会多出一个没有承诺可兑现的开关；③ 那道门按角色名字拦人，把 SUPPORT /
 * SUPPORT_* 与 081 的 SALES_* 全部挡在「智能建议」菜单（V46/V75 已授给 ADMIN / SALES / SALES_MANAGER /
 * SALES_REP）之外——菜单点得进、 接口恒 403。
 *
 * <p>{@code ignore} 写的是 Redis 里<b>按用户</b>的忽略集合——键 {@code ai:ignore:{userId}:}，成员 {@code
 * entityType:entityId}，读写都用 {@code SecurityUtil.currentUserId()}——不产生跨用户副作用，因此同样不需要权限码。
 */
@RestController
@RequestMapping("/api/v1/suggestions")
@Tag(name = "智能建议")
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
