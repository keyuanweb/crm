package com.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.ApiResponse;
import com.crm.dto.report.ReportQuery;
import com.crm.dto.report.ReportResult;
import com.crm.dto.report.ReportRow;
import com.crm.entity.ReportTemplate;
import com.crm.repository.ReportTemplateMapper;
import com.crm.security.SecurityUtil;
import com.crm.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 自定义报表接口（021-custom-reports，contracts/custom-reports.md）。 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "报表")
@PreAuthorize("hasAnyRole('ADMIN','SALES')")
public class ReportController {

  private final ReportService reportService;
  private final ReportTemplateMapper templateMapper;

  public ReportController(ReportService reportService, ReportTemplateMapper templateMapper) {
    this.reportService = reportService;
    this.templateMapper = templateMapper;
  }

  @PostMapping("/query")
  @Operation(summary = "按维度/指标/时间范围聚合查询报表")
  public ApiResponse<ReportResult> query(@Valid @RequestBody ReportQuery query) {
    return ApiResponse.ok(reportService.query(query));
  }

  @GetMapping("/export")
  @Operation(summary = "导出报表为 Excel（xlsx）")
  public ResponseEntity<ByteArrayResource> export(
      @RequestParam String dimension,
      @RequestParam String metric,
      @RequestParam(required = false) String granularity,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String stageFilter)
      throws Exception {
    ReportQuery q = new ReportQuery();
    q.setDimension(dimension);
    q.setMetric(metric);
    q.setGranularity(granularity);
    q.setStartDate(startDate);
    q.setEndDate(endDate);
    q.setStageFilter(stageFilter);
    ReportResult result = reportService.query(q);

    byte[] bytes = toXlsx(result);
    ByteArrayResource resource = new ByteArrayResource(bytes);
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("report.xlsx").build().toString())
        .body(resource);
  }

  // ===== 报表模板（P3，仅 ADMIN） =====

  @GetMapping("/templates")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "报表模板列表")
  public ApiResponse<List<ReportTemplate>> templates() {
    List<ReportTemplate> list =
        templateMapper.selectList(
            new LambdaQueryWrapper<ReportTemplate>().orderByDesc(ReportTemplate::getId));
    return ApiResponse.ok(list);
  }

  @PostMapping("/templates")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "保存报表模板")
  public ApiResponse<ReportTemplate> saveTemplate(@Valid @RequestBody ReportTemplate template) {
    template.setId(null);
    template.setCreatedBy(SecurityUtil.currentUserId());
    templateMapper.insert(template);
    return ApiResponse.ok(template);
  }

  @DeleteMapping("/templates/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "删除报表模板")
  public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
    templateMapper.deleteById(id);
    return ApiResponse.ok();
  }

  @GetMapping("/templates/{id}/run")
  @Operation(summary = "按模板执行报表查询")
  public ApiResponse<ReportResult> runTemplate(@PathVariable Long id) {
    ReportTemplate t = templateMapper.selectById(id);
    if (t == null) {
      throw new com.crm.common.BusinessException(com.crm.common.ErrorCode.NOTIFICATION_NOT_FOUND);
    }
    ReportQuery q = new ReportQuery();
    q.setDimension(t.getDimension());
    q.setMetric(t.getMetric());
    q.setGranularity(t.getGranularity());
    q.setStartDate(t.getStartDate() == null ? null : t.getStartDate().toString());
    q.setEndDate(t.getEndDate() == null ? null : t.getEndDate().toString());
    q.setStageFilter(t.getStageFilter());
    return ApiResponse.ok(reportService.query(q));
  }

  private byte[] toXlsx(ReportResult result) throws Exception {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("报表");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue(result.getDimension().toLowerCase());
      header.createCell(1).setCellValue("数量");
      header.createCell(2).setCellValue("金额（元）");
      header.createCell(3).setCellValue("占比");
      int rowIdx = 1;
      for (ReportRow r : result.getRows()) {
        Row row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(r.getDimensionValue());
        row.createCell(1).setCellValue(r.getCount());
        row.createCell(2).setCellValue(r.getAmount() / 100.0);
        row.createCell(3).setCellValue(Math.round(r.getRatio() * 10000) / 100.0);
      }
      workbook.write(bos);
      return bos.toByteArray();
    }
  }
}
