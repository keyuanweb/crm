package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.contract.AttachmentResponse;
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.security.RequirePermission;
import com.crm.service.ContractAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 合同附件接口（008，FR-CT07）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES')")} 换成合同自己的读/写码，零补授。</b>附件是
 * 合同的一部分：列表与下载挂 {@code contract:read}，上传与删除挂 {@code contract:update}。字典里没有 attachment
 * 一族，也不该有——新造一族只会让"能看合同"与"能看合同附件"这两个本该同进同出的能力出现分叉，管理员要在两处 勾同一件事。
 *
 * <p>零补授的原因：改造前那道门放行的 ADMIN / SALES 在 V46/V80 里本就持有这两个码；顺带受益的是 SALES_MANAGER / SALES_REP /
 * FINANCE_*——他们持有 {@code contract:read} / {@code contract:update} 与「合同」菜单，此前点上传 是 403。
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/attachments")
@Tag(name = "合同附件")
public class ContractAttachmentController {

  private final ContractAttachmentService attachmentService;

  public ContractAttachmentController(ContractAttachmentService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @GetMapping
  @RequirePermission("contract:read")
  @Operation(summary = "合同附件列表")
  public ApiResponse<List<AttachmentResponse>> list(@PathVariable Long contractId) {
    return ApiResponse.ok(attachmentService.list(contractId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("contract:update")
  @Operation(summary = "上传附件（≤20MB，类型白名单）")
  public ApiResponse<AttachmentResponse> upload(
      @PathVariable Long contractId, @RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(attachmentService.upload(contractId, file));
  }

  @GetMapping("/{attachmentId}/download")
  @RequirePermission("contract:read")
  @RateLimit(
      scope = "export-download",
      limit = 30,
      windowSeconds = 60,
      by = RateLimitDimension.USER)
  @Operation(summary = "下载附件")
  public ResponseEntity<Resource> download(
      @PathVariable Long contractId, @PathVariable Long attachmentId) {
    ContractAttachmentService.DownloadResult result =
        attachmentService.download(contractId, attachmentId);
    String encoded =
        URLEncoder.encode(result.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
        .contentType(
            result.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(result.contentType()))
        .body(result.resource());
  }

  @DeleteMapping("/{attachmentId}")
  @RequirePermission("contract:update")
  @Operation(summary = "删除附件（物理删除文件+记录）")
  public ApiResponse<Void> delete(@PathVariable Long contractId, @PathVariable Long attachmentId) {
    attachmentService.delete(contractId, attachmentId);
    return ApiResponse.ok();
  }
}
