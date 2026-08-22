package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.marketing.CampaignRequest;
import com.crm.dto.marketing.CampaignResponse;
import com.crm.dto.marketing.ChannelRoiResponse;
import com.crm.service.MarketingCampaignService;
import com.crm.service.MarketingRoiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/** 营销活动接口（014，FR-M01~M07）。 */
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
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "创建活动（ADMIN/SALES）")
  public ApiResponse<CampaignResponse> create(@Valid @RequestBody CampaignRequest request) {
    return ApiResponse.ok(campaignService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "编辑活动（ADMIN/SALES）")
  public ApiResponse<CampaignResponse> update(
      @PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
    return ApiResponse.ok(campaignService.update(id, request));
  }

  @PostMapping("/{id}/start")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "开始活动（PLANNING→RUNNING）")
  public ApiResponse<CampaignResponse> start(@PathVariable Long id) {
    return ApiResponse.ok(campaignService.start(id));
  }

  @PostMapping("/{id}/end")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "结束活动（RUNNING→ENDED）")
  public ApiResponse<CampaignResponse> end(@PathVariable Long id) {
    return ApiResponse.ok(campaignService.end(id));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "删除活动（有归因数据时拒绝）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    campaignService.delete(id);
    return ApiResponse.ok();
  }
}
