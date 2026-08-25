package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.lead.LeadRequest;
import com.crm.dto.open.ApiKeyRequest;
import com.crm.dto.open.ApiKeyResponse;
import com.crm.dto.open.DeliveryResponse;
import com.crm.dto.open.WebhookRequest;
import com.crm.dto.open.WebhookResponse;
import com.crm.security.ApiKeyAuthFilter.ApiKeyPrincipal;
import com.crm.service.ApiKeyService;
import com.crm.service.CustomerService;
import com.crm.service.LeadService;
import com.crm.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 开放平台接口（055）：API Key/Webhook 管理 + 开放端点。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "开放平台")
public class OpenPlatformController {

  private final ApiKeyService apiKeyService;
  private final WebhookService webhookService;
  private final CustomerService customerService;
  private final LeadService leadService;

  public OpenPlatformController(
      ApiKeyService apiKeyService,
      WebhookService webhookService,
      CustomerService customerService,
      LeadService leadService) {
    this.apiKeyService = apiKeyService;
    this.webhookService = webhookService;
    this.customerService = customerService;
    this.leadService = leadService;
  }

  // ===== API Key 管理（仅 ADMIN） =====

  @PostMapping("/platform/api-keys")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "创建 API Key（完整值仅本次返回）")
  public ApiResponse<ApiKeyResponse> createApiKey(@Valid @RequestBody ApiKeyRequest request) {
    return ApiResponse.ok(apiKeyService.create(request));
  }

  @GetMapping("/platform/api-keys")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "API Key 列表（仅前缀）")
  public ApiResponse<PageResult<ApiKeyResponse>> apiKeys(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(apiKeyService.page(page, pageSize));
  }

  @PostMapping("/platform/api-keys/{id}/revoke")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "吊销 API Key")
  public ApiResponse<Void> revokeApiKey(@PathVariable Long id) {
    apiKeyService.revoke(id);
    return ApiResponse.ok(null);
  }

  // ===== Webhook 管理（仅 ADMIN） =====

  @PostMapping("/platform/webhooks")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "创建 Webhook 订阅（secret 仅本次返回）")
  public ApiResponse<WebhookResponse> createWebhook(@Valid @RequestBody WebhookRequest request) {
    return ApiResponse.ok(webhookService.create(request));
  }

  @GetMapping("/platform/webhooks")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Webhook 订阅列表")
  public ApiResponse<List<WebhookResponse>> webhooks() {
    return ApiResponse.ok(webhookService.list());
  }

  @PostMapping("/platform/webhooks/{id}/toggle")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "启停 Webhook")
  public ApiResponse<WebhookResponse> toggleWebhook(@PathVariable Long id) {
    return ApiResponse.ok(webhookService.toggle(id));
  }

  @DeleteMapping("/platform/webhooks/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "删除 Webhook 订阅")
  public ApiResponse<Void> deleteWebhook(@PathVariable Long id) {
    webhookService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/platform/webhooks/{id}/deliveries")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Webhook 推送记录")
  public ApiResponse<PageResult<DeliveryResponse>> deliveries(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(webhookService.deliveries(id, page, pageSize));
  }

  // ===== 开放端点（X-API-Key 鉴权） =====

  @GetMapping("/open/customers")
  @Operation(summary = "开放：客户只读列表（X-API-Key）")
  public ApiResponse<PageResult<?>> openCustomers(
      Authentication authentication,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    requireScope(authentication, "customer:read");
    return ApiResponse.ok(customerService.page(keyword, null, null, page, pageSize));
  }
  @GetMapping("/open/leads")
  @Operation(summary = "开放：线索只读列表（X-API-Key）")
  public ApiResponse<PageResult<?>> openLeads(
      Authentication authentication,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    requireScope(authentication, "lead:read");
    return ApiResponse.ok(
        leadService.page(null, null, null, null, false, null, page, pageSize));
  }

  @PostMapping("/open/leads")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "开放：创建线索（需 lead:write）")
  public ApiResponse<?> openCreateLead(
      Authentication authentication, @Valid @RequestBody LeadRequest request) {
    requireScope(authentication, "lead:write");
    return ApiResponse.ok(leadService.create(request));
  }

  private void requireScope(Authentication authentication, String scope) {
    if (authentication == null || !(authentication.getDetails() instanceof ApiKeyPrincipal p)) {
      throw new com.crm.common.BusinessException(com.crm.common.ErrorCode.OPEN_API_KEY_INVALID);
    }
    apiKeyService.requireScope(apiKeyService.getById(p.keyId()), scope);
  }
}
