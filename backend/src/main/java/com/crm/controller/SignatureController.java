package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.signature.SignRequest;
import com.crm.dto.signature.SignatureRecordResponse;
import com.crm.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 电子签署接口（047，FR-S01~S09）。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "电子签署")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class SignatureController {

  private final SignatureService signatureService;

  public SignatureController(SignatureService signatureService) {
    this.signatureService = signatureService;
  }

  @PostMapping("/quotes/{id}/sign")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "签署报价单（APPROVED→SIGNED）")
  public ApiResponse<SignatureRecordResponse> signQuote(
      @PathVariable Long id, @Valid @RequestBody SignRequest request) {
    return ApiResponse.ok(signatureService.signQuote(id, request));
  }

  @GetMapping("/quotes/{id}/signature")
  @Operation(summary = "报价单签署记录")
  public ApiResponse<SignatureRecordResponse> quoteSignature(@PathVariable Long id) {
    return ApiResponse.ok(signatureService.findByBusiness(SignatureService.TYPE_QUOTE, id));
  }

  @PostMapping("/contracts/{id}/sign")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "签署合同（APPROVED→SIGNED，之后可生效）")
  public ApiResponse<SignatureRecordResponse> signContract(
      @PathVariable Long id, @Valid @RequestBody SignRequest request) {
    return ApiResponse.ok(signatureService.signContract(id, request));
  }

  @GetMapping("/contracts/{id}/signature")
  @Operation(summary = "合同签署记录")
  public ApiResponse<SignatureRecordResponse> contractSignature(@PathVariable Long id) {
    return ApiResponse.ok(signatureService.findByBusiness(SignatureService.TYPE_CONTRACT, id));
  }
}
