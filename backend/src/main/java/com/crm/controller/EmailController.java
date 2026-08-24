package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.email.CampaignRequest;
import com.crm.dto.email.EmailTemplateRequest;
import com.crm.dto.email.EmailTemplateResponse;
import com.crm.entity.EmailCampaign;
import com.crm.entity.EmailSendLog;
import com.crm.security.RequirePermission;
import com.crm.service.EmailCampaignService;
import com.crm.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 邮件营销接口（030-email-marketing）。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "邮件营销")
public class EmailController {

  private final EmailTemplateService templateService;
  private final EmailCampaignService campaignService;

  public EmailController(
      EmailTemplateService templateService, EmailCampaignService campaignService) {
    this.templateService = templateService;
    this.campaignService = campaignService;
  }

  // ---------- 模板 ----------

  @GetMapping("/email-templates")
  @RequirePermission("email:manage")
  @Operation(summary = "邮件模板列表")
  public ApiResponse<List<EmailTemplateResponse>> templates(
      @RequestParam(required = false) String category) {
    return ApiResponse.ok(templateService.list(category));
  }

  @PostMapping("/email-templates")
  @RequirePermission("email:manage")
  @Operation(summary = "创建邮件模板")
  public ApiResponse<EmailTemplateResponse> createTemplate(
      @RequestBody EmailTemplateRequest request) {
    return ApiResponse.ok(templateService.create(request));
  }

  @PutMapping("/email-templates/{id}")
  @RequirePermission("email:manage")
  @Operation(summary = "编辑邮件模板")
  public ApiResponse<EmailTemplateResponse> updateTemplate(
      @PathVariable Long id, @RequestBody EmailTemplateRequest request) {
    return ApiResponse.ok(templateService.update(id, request));
  }

  @DeleteMapping("/email-templates/{id}")
  @RequirePermission("email:manage")
  @Operation(summary = "删除邮件模板")
  public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
    templateService.delete(id);
    return ApiResponse.ok(null);
  }

  // ---------- 群发 ----------

  @PostMapping("/email-campaigns")
  @RequirePermission("email:manage")
  @Operation(summary = "创建并发送邮件群发")
  public ApiResponse<EmailCampaign> createCampaign(@RequestBody CampaignRequest request) {
    return ApiResponse.ok(campaignService.createAndSend(request));
  }

  @PostMapping("/email-campaigns/{id}/test")
  @RequirePermission("email:manage")
  @Operation(summary = "测试发送")
  public ApiResponse<Void> testSend(@PathVariable Long id, @RequestBody Map<String, String> body) {
    campaignService.testSend(id, body.get("email"));
    return ApiResponse.ok(null);
  }

  @GetMapping("/email-campaigns")
  @RequirePermission("email:manage")
  @Operation(summary = "群发活动列表")
  public ApiResponse<List<EmailCampaign>> campaigns() {
    return ApiResponse.ok(campaignService.list());
  }

  @GetMapping("/email-campaigns/{id}")
  @RequirePermission("email:manage")
  @Operation(summary = "活动详情（发送记录）")
  public ApiResponse<PageResult<EmailSendLog>> campaignDetail(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(campaignService.detail(id, page, pageSize));
  }
}
