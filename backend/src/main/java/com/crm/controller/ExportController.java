package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.export.ExportJobResponse;
import com.crm.dto.export.ExportRequest;
import com.crm.security.RequirePermission;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据导出接口（016，FR-S08~S10）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 只在创建动作上换成 {@code
 * export:create}。</b>读（{@code GET /exports}）与下载不设码：前者按 {@code SecurityUtil.currentUserId()} 过滤、后者在
 * {@code ExportJobService.downloadPath} 里校验创建人/ADMIN，都是真实的数据范围判定 （SystemEnhancementIT 钉着"SUPPORT
 * 下载他人导出 → 403"，那条 403 来自这里而非权限码）。
 *
 * <p><b>授予范围刻意只有 ADMIN / SALES / SUPPORT</b>，即改造前那道门放行的三个角色——虽然「导出中心」菜单由 12 个 角色持有（VIEWER 没有），但
 * {@code ExportExecutor} 的 {@code writeOpportunities} / {@code writeTickets} 两条导出
 * **没有范围过滤**，是整表导出。这与 V80 里 {@code lead:export} 的判断同一条原则：把一份无过滤的全量导出顺手扩给 9
 * 个角色，不该由"权限接线"完成；等这两条导出补上范围过滤，再按菜单扩。已知代价：那 9 个角色打开导出中心点 "新建导出"会 403——与 {@code CommentController}
 * 同类的一处**待裁决的锁死**（见 1.5 报告）。
 */
@RestController
@RequestMapping("/api/v1/exports")
@Tag(name = "系统增强")
public class ExportController {

  private final ExportJobService exportJobService;

  public ExportController(ExportJobService exportJobService) {
    this.exportJobService = exportJobService;
  }

  @PostMapping
  @RequirePermission("export:create")
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
