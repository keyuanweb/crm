package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.signature.SignRequest;
import com.crm.dto.signature.SignatureRecordResponse;
import com.crm.security.RequirePermission;
import com.crm.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电子签署接口（047，FR-S01~S09）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 换成文档自己的读/写码，且一个码都不用补。</b>
 *
 * <p>签署是"把文档推进到 SIGNED"的一次状态变更，因此写用文档的编辑码（{@code quote:update} / {@code
 * contract:update}），读用文档的读码（{@code quote:read} / {@code contract:read}）。刻意**不用** {@code
 * quote:approve} / {@code contract:approve}：那两个码在矩阵里属于 ADMIN / SALES_MANAGER / FINANCE_*， 不含
 * SALES，用它就得给 SALES 补一条审批码（一次扩权），而签署本来就是拿着单子的销售在做的事。
 *
 * <p>零补授的原因：改造前那道门放行的 ADMIN / SALES 在 V46/V80 里本就持有这四个码；而 SALES_MANAGER / SALES_REP / FINANCE_*
 * 因为持有 {@code quote:read} / {@code contract:read} 与相应菜单，顺带真的能用签署了——
 * 这正是"让矩阵成真"：他们点得进报价单/合同详情，此前点"签署"却是 403。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "电子签署")
public class SignatureController {

  private final SignatureService signatureService;

  public SignatureController(SignatureService signatureService) {
    this.signatureService = signatureService;
  }

  @PostMapping("/quotes/{id}/sign")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("quote:update")
  @Operation(summary = "签署报价单（APPROVED→SIGNED）")
  public ApiResponse<SignatureRecordResponse> signQuote(
      @PathVariable Long id, @Valid @RequestBody SignRequest request) {
    return ApiResponse.ok(signatureService.signQuote(id, request));
  }

  @GetMapping("/quotes/{id}/signature")
  @RequirePermission("quote:read")
  @Operation(summary = "报价单签署记录")
  public ApiResponse<SignatureRecordResponse> quoteSignature(@PathVariable Long id) {
    return ApiResponse.ok(signatureService.findByBusiness(SignatureService.TYPE_QUOTE, id));
  }

  @PostMapping("/contracts/{id}/sign")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("contract:update")
  @Operation(summary = "签署合同（APPROVED→SIGNED，之后可生效）")
  public ApiResponse<SignatureRecordResponse> signContract(
      @PathVariable Long id, @Valid @RequestBody SignRequest request) {
    return ApiResponse.ok(signatureService.signContract(id, request));
  }

  @GetMapping("/contracts/{id}/signature")
  @RequirePermission("contract:read")
  @Operation(summary = "合同签署记录")
  public ApiResponse<SignatureRecordResponse> contractSignature(@PathVariable Long id) {
    return ApiResponse.ok(signatureService.findByBusiness(SignatureService.TYPE_CONTRACT, id));
  }
}
