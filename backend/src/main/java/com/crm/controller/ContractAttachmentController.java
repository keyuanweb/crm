package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.contract.AttachmentResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 合同附件接口（008，FR-CT07）。 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/attachments")
@Tag(name = "合同附件")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class ContractAttachmentController {

  private final ContractAttachmentService attachmentService;

  public ContractAttachmentController(ContractAttachmentService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @GetMapping
  @Operation(summary = "合同附件列表")
  public ApiResponse<List<AttachmentResponse>> list(@PathVariable Long contractId) {
    return ApiResponse.ok(attachmentService.list(contractId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "上传附件（≤20MB，类型白名单）")
  public ApiResponse<AttachmentResponse> upload(
      @PathVariable Long contractId, @RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(attachmentService.upload(contractId, file));
  }

  @GetMapping("/{attachmentId}/download")
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
  @Operation(summary = "删除附件（物理删除文件+记录）")
  public ApiResponse<Void> delete(@PathVariable Long contractId, @PathVariable Long attachmentId) {
    attachmentService.delete(contractId, attachmentId);
    return ApiResponse.ok();
  }
}
