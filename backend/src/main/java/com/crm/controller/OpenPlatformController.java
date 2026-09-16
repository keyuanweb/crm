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
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.security.RequirePermission;
import com.crm.service.ApiKeyService;
import com.crm.service.CustomerService;
import com.crm.service.LeadService;
import com.crm.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/**
 * 开放平台接口（055）：API Key/Webhook 管理 + 开放端点。
 *
 * <p><b>1.5 批 3 只改了前半：八个 {@code /platform/**} 端点的 {@code hasRole('ADMIN')} 换成新码 {@code
 * open_platform:manage}，该码不授给任何角色。</b>可访问范围与改造前一致（仅 ADMIN）——「开放平台」菜单 在种子里无人持有，API Key 与 Webhook
 * 密钥也不该随手授人。读端点与写共用本码：本模块没有只读消费者 （拿到了密钥本身就是拿到了写能力），若将来有，再拆 {@code open_platform:read}。
 *
 * <p><b>⚠️ 后半（{@code /open/**} 三个开放端点）刻意不加任何 {@code @RequirePermission}。</b>它们走的是 {@code
 * ApiKeyAuthFilter} 建立的主体 {@code ApiKeyPrincipal}（{@code X-API-Key} 头），不是 JWT 的 {@code
 * SecurityUtil.currentPrincipal()}——而 {@code PermissionAspect} 只认后者。挂上权限码的后果不是"更安全"， 而是**把全部 API
 * Key 调用方打成 403**（切面拿不到角色，直接判拒）。它们的鉴权在自己的 {@code requireScope} 里， 按 Key 的 scope 逐个校验，这层不该被权限码覆盖。
 *
 * <p>⚠️ <b>2026-09-17（100-rate-limit-consolidation）</b>：{@code /open/**} 三个端点此前<b>零限制</b>——机器客户端
 * 可以无限打。现按 <b>API_KEY</b> 维度分桶：两个 GET 走 {@code open-api-read}（60/60s），POST 走 {@code
 * open-api-write}（30/60s）。<b>维度必须是 keyId 不是 userId</b>——API Key 主体的 {@code userId} 是密钥创建者，
 * 同一个管理员建的两个密钥会共用一个桶，等于给"多密钥分流"设计的机制失效。 ⚠️ 这里能挂限流，恰恰因为限流<b>不依赖权限码</b>（它读的是 {@code
 * ApiKeyPrincipal}，不是 {@code PermissionAspect} 认的 JWT 主体）——与上面那条"不加
 * {@code @RequirePermission}"的理由不冲突，两者是两套独立的主体解析。
 */
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

  // ===== API Key 管理（平台级，仅 ADMIN） =====

  @PostMapping("/platform/api-keys")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("open_platform:manage")
  @Operation(summary = "创建 API Key（完整值仅本次返回）")
  public ApiResponse<ApiKeyResponse> createApiKey(@Valid @RequestBody ApiKeyRequest request) {
    return ApiResponse.ok(apiKeyService.create(request));
  }

  @GetMapping("/platform/api-keys")
  @RequirePermission("open_platform:manage")
  @Operation(summary = "API Key 列表（仅前缀）")
  public ApiResponse<PageResult<ApiKeyResponse>> apiKeys(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(apiKeyService.page(page, pageSize));
  }

  @PostMapping("/platform/api-keys/{id}/revoke")
  @RequirePermission("open_platform:manage")
  @Operation(summary = "吊销 API Key")
  public ApiResponse<Void> revokeApiKey(@PathVariable Long id) {
    apiKeyService.revoke(id);
    return ApiResponse.ok(null);
  }

  // ===== Webhook 管理（平台级，仅 ADMIN） =====

  @PostMapping("/platform/webhooks")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("open_platform:manage")
  @Operation(summary = "创建 Webhook 订阅（secret 仅本次返回）")
  public ApiResponse<WebhookResponse> createWebhook(@Valid @RequestBody WebhookRequest request) {
    return ApiResponse.ok(webhookService.create(request));
  }

  @GetMapping("/platform/webhooks")
  @RequirePermission("open_platform:manage")
  @Operation(summary = "Webhook 订阅列表")
  public ApiResponse<List<WebhookResponse>> webhooks() {
    return ApiResponse.ok(webhookService.list());
  }

  @PostMapping("/platform/webhooks/{id}/toggle")
  @RequirePermission("open_platform:manage")
  @Operation(summary = "启停 Webhook")
  public ApiResponse<WebhookResponse> toggleWebhook(@PathVariable Long id) {
    return ApiResponse.ok(webhookService.toggle(id));
  }

  @DeleteMapping("/platform/webhooks/{id}")
  @RequirePermission("open_platform:manage")
  @Operation(summary = "删除 Webhook 订阅")
  public ApiResponse<Void> deleteWebhook(@PathVariable Long id) {
    webhookService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/platform/webhooks/{id}/deliveries")
  @RequirePermission("open_platform:manage")
  @Operation(summary = "Webhook 推送记录")
  public ApiResponse<PageResult<DeliveryResponse>> deliveries(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(webhookService.deliveries(id, page, pageSize));
  }

  // ===== 开放端点（X-API-Key 鉴权） =====

  @GetMapping("/open/customers")
  @RateLimit(
      scope = "open-api-read",
      limit = 60,
      windowSeconds = 60,
      by = RateLimitDimension.API_KEY)
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
  @RateLimit(
      scope = "open-api-read",
      limit = 60,
      windowSeconds = 60,
      by = RateLimitDimension.API_KEY)
  @Operation(summary = "开放：线索只读列表（X-API-Key）")
  public ApiResponse<PageResult<?>> openLeads(
      Authentication authentication,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    requireScope(authentication, "lead:read");
    return ApiResponse.ok(leadService.page(null, null, null, null, false, null, page, pageSize));
  }

  @PostMapping("/open/leads")
  @ResponseStatus(HttpStatus.CREATED)
  // ⚠️ 限流挂在这里是安全的，恰恰因为它**不依赖权限码**：本端点的主体是 ApiKeyPrincipal，而
  // RateLimitAspect 按 ApiKeyAuthFilter.ApiKeyPrincipal.keyId() 分桶，不看 PermissionAspect 认的 JWT 主体
  // （正因如此本类刻意不给 /open/** 挂 @RequirePermission，见类注释）。
  @RateLimit(
      scope = "open-api-write",
      limit = 30,
      windowSeconds = 60,
      by = RateLimitDimension.API_KEY)
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
