package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customer.ImportResult;
import com.crm.dto.lead.ConvertRequest;
import com.crm.dto.lead.LeadDetailResponse;
import com.crm.dto.lead.LeadRequest;
import com.crm.dto.lead.LeadResponse;
import com.crm.service.CustomFieldFilterSupport;
import com.crm.service.LeadExcelService;
import com.crm.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 线索接口（contracts/leads.md）。 */
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "线索管理")
public class LeadController {

  private final LeadService leadService;
  private final LeadExcelService leadExcelService;
  private final CustomFieldFilterSupport customFieldFilterSupport;

  public LeadController(
      LeadService leadService,
      LeadExcelService leadExcelService,
      CustomFieldFilterSupport customFieldFilterSupport) {
    this.leadService = leadService;
    this.leadExcelService = leadExcelService;
    this.customFieldFilterSupport = customFieldFilterSupport;
  }

  @GetMapping
  @Operation(summary = "分页查询线索列表（支持 cf_<fieldId> 自定义字段筛选）")
  public ApiResponse<PageResult<LeadResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(defaultValue = "false") boolean poolOnly,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize,
      @RequestParam Map<String, String> params) {
    List<Long> cfMatchedIds =
        customFieldFilterSupport.matchEntityIds(
            "LEAD", customFieldFilterSupport.parseFilters(params));
    return ApiResponse.ok(
        leadService.page(keyword, status, source, ownerId, poolOnly, cfMatchedIds, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "线索详情（含跟进时间线）")
  public ApiResponse<LeadDetailResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(leadService.detail(id));
  }

  @PostMapping
  @Operation(summary = "创建线索")
  public ApiResponse<LeadResponse> create(@Valid @RequestBody LeadRequest request) {
    return ApiResponse.ok(leadService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑线索")
  public ApiResponse<LeadResponse> update(
      @PathVariable Long id, @Valid @RequestBody LeadRequest request) {
    return ApiResponse.ok(leadService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除线索（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    leadService.delete(id);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/assign")
  @Operation(summary = "分配线索给指定销售")
  public ApiResponse<LeadResponse> assign(@PathVariable Long id, @RequestParam Long ownerId) {
    return ApiResponse.ok(leadService.assign(id, ownerId));
  }

  @PostMapping("/{id}/claim")
  @Operation(summary = "从线索池领取线索")
  public ApiResponse<LeadResponse> claim(@PathVariable Long id) {
    return ApiResponse.ok(leadService.claim(id));
  }

  @PostMapping("/{id}/convert")
  @Operation(summary = "转化线索为客户+商机")
  public ApiResponse<LeadDetailResponse> convert(
      @PathVariable Long id, @Valid @RequestBody ConvertRequest request) {
    return ApiResponse.ok(leadService.convert(id, request));
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Excel 批量导入线索（FR-L11）")
  public ApiResponse<ImportResult> importLeads(@RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    return ApiResponse.ok(leadExcelService.importLeads(file.getInputStream()));
  }

  @GetMapping("/export")
  @Operation(summary = "按当前筛选条件导出线索（FR-L11）")
  public ResponseEntity<byte[]> exportLeads(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String source) {
    byte[] content = leadExcelService.exportLeads(keyword, status, source);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=leads-" + java.time.LocalDate.now() + ".xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(content);
  }

  @GetMapping("/template")
  @Operation(summary = "下载线索导入模板（FR-L11）")
  public ResponseEntity<byte[]> importTemplate() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lead-import-template.xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(leadExcelService.generateTemplate());
  }
}
