package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.marketing.CampaignRequest;
import com.crm.dto.marketing.CampaignResponse;
import com.crm.dto.marketing.ChannelRoiResponse;
import com.crm.security.RequirePermission;
import com.crm.service.MarketingCampaignService;
import com.crm.service.MarketingRoiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * 营销活动接口（014，FR-M01~M07）。
 *
 * <p><b>1.5 批 3：五处 {@code hasAnyRole('ADMIN','SALES')} 换成 {@code campaign:create/update/delete}， 并给
 * SALES 补授这三个码——这是本批唯一一处必需的补授（判据①）。</b>原因：那三个码此前只授给了 MARKETING_MANAGER（V75）与
 * MARKETING_SPECIALIST（V75），**SALES 并不持有**；照原样接线会把 SALES 从"能建营销活动"打成
 * 403，正是判据①要防的那件事。补授范围严格等于旧门放行的集合 {ADMIN, SALES}， 没有顺手按菜单扩（SALES_MANAGER
 * 也持有「营销活动」菜单，但它改造前就不能建，本批不改）。
 *
 * <p><b>顺带让两条早已存在的授权变成真的</b>：MARKETING_MANAGER / MARKETING_SPECIALIST 持有这三个码却一直
 * 被角色字面量挡在门外——接线后营销经理真的能建、能改、能结束活动了。
 *
 * <p><b>状态流转（start/end）挂 update 而不另设码</b>：与 {@code /custom-objects/{id}/toggle}、 {@code
 * workflow/rules/{id}/toggle} 同形，"启停/流转"都是编辑的另一种写法。
 *
 * <p><b>两个读接口不动</b>：活动列表与渠道 ROI 改造前就没有闸门，且它们是无 owner 维度的公司级数据 （{@code MarketingRoiService}
 * 里没有任何数据范围过滤，但这正是"全员可见的营销账"的语义）， 不设码——设了等于让营销分析师之外的人打开营销页看到 403。
 */
@RestController
@RequestMapping("/api/v1/campaigns")
@Tag(name = "市场营销")
public class MarketingController {

  private final MarketingCampaignService campaignService;
  private final MarketingRoiService roiService;

  public MarketingController(
      MarketingCampaignService campaignService, MarketingRoiService roiService) {
    this.campaignService = campaignService;
    this.roiService = roiService;
  }

  @GetMapping
  @Operation(summary = "活动分页列表（关键字/渠道/状态筛选）")
  public ApiResponse<PageResult<CampaignResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(campaignService.page(keyword, channel, status, page, pageSize));
  }

  @GetMapping("/channel-roi")
  @Operation(summary = "渠道 ROI 统计")
  public ApiResponse<List<ChannelRoiResponse>> channelRoi() {
    return ApiResponse.ok(roiService.channelRoi());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("campaign:create")
  @Operation(summary = "创建活动")
  public ApiResponse<CampaignResponse> create(@Valid @RequestBody CampaignRequest request) {
    return ApiResponse.ok(campaignService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("campaign:update")
  @Operation(summary = "编辑活动")
  public ApiResponse<CampaignResponse> update(
      @PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
    return ApiResponse.ok(campaignService.update(id, request));
  }

  @PostMapping("/{id}/start")
  @RequirePermission("campaign:update")
  @Operation(summary = "开始活动（PLANNING→RUNNING）")
  public ApiResponse<CampaignResponse> start(@PathVariable Long id) {
    return ApiResponse.ok(campaignService.start(id));
  }

  @PostMapping("/{id}/end")
  @RequirePermission("campaign:update")
  @Operation(summary = "结束活动（RUNNING→ENDED）")
  public ApiResponse<CampaignResponse> end(@PathVariable Long id) {
    return ApiResponse.ok(campaignService.end(id));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("campaign:delete")
  @Operation(summary = "删除活动（有归因数据时拒绝）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    campaignService.delete(id);
    return ApiResponse.ok();
  }
}
