package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.export.ExportJobResponse;
import com.crm.dto.export.ExportRequest;
import com.crm.security.SecurityUtil;
import com.crm.service.ExportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 数据导出接口（016，FR-S08~S10）。 */
@RestController
@RequestMapping("/api/v1/exports")
@Tag(name = "系统增强")
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
public class ExportController {

  private final ExportJobService exportJobService;

  public ExportController(ExportJobService exportJobService) {
    this.exportJobService = exportJobService;
  }

  @PostMapping
  @Operation(summary = "创建导出任务（后台执行）")
  public ApiResponse<ExportJobResponse> create(@Valid @RequestBody ExportRequest request) {
    return ApiResponse.ok(exportJobService.create(request));
  }

  @GetMapping
  @Operation(summary = "导出任务历史（分页，本人）")
  public ApiResponse<PageResult<ExportJobResponse>> page(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(exportJobService.page(SecurityUtil.currentUserId(), page, pageSize));
  }

  @GetMapping("/{id}/download")
  @Operation(summary = "下载导出文件（仅创建人/ADMIN，需已完成）")
  public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) throws Exception {
    Path file = exportJobService.downloadPath(id);
    byte[] content = Files.readAllBytes(file);
    String fileName = file.getFileName().toString();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(new ByteArrayResource(content));
  }
}
