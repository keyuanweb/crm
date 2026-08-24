package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerDetailResponse;
import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.customer.ImportResult;
import com.crm.service.CustomFieldFilterSupport;
import com.crm.service.CustomerExcelService;
import com.crm.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 客户接口（contracts/customers.md，FR-001~006）。 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "客户")
public class CustomerController {

  private final CustomerService customerService;
  private final CustomerExcelService customerExcelService;
  private final CustomFieldFilterSupport customFieldFilterSupport;

  public CustomerController(
      CustomerService customerService,
      CustomerExcelService customerExcelService,
      CustomFieldFilterSupport customFieldFilterSupport) {
    this.customerService = customerService;
    this.customerExcelService = customerExcelService;
    this.customFieldFilterSupport = customFieldFilterSupport;
  }

  @GetMapping
  @Operation(summary = "分页查询客户列表（关键字搜索/状态/自定义字段筛选）")
  public ApiResponse<PageResult<CustomerResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize,
      @RequestParam Map<String, String> params) {
    List<Long> cfMatchedIds =
        customFieldFilterSupport.matchEntityIds(
            "CUSTOMER", customFieldFilterSupport.parseFilters(params));
    return ApiResponse.ok(customerService.page(keyword, status, cfMatchedIds, page, pageSize));
  }

  @GetMapping("/health/at-risk")
  @Operation(summary = "客户流失预警列表（超过 N 天无跟进且无新订单，按健康度升序）")
  public ApiResponse<PageResult<com.crm.dto.customer.CustomerHealthBrief>> atRisk(
      @RequestParam(defaultValue = "45") int daysInactive,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(customerService.atRiskCustomers(daysInactive, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "客户详情（含商机与跟进时间线）")
  public ApiResponse<CustomerDetailResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(customerService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建客户")
  public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
    return ApiResponse.ok(customerService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑客户")
  public ApiResponse<CustomerResponse> update(
      @PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
    return ApiResponse.ok(customerService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "逻辑删除客户")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    customerService.delete(id);
    return ApiResponse.ok();
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Excel 批量导入客户（仅管理员）")
  public ApiResponse<ImportResult> importCustomers(@RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    return ApiResponse.ok(customerExcelService.importCustomers(file.getInputStream()));
  }

  @GetMapping("/export")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "按当前筛选条件导出客户（仅管理员）")
  public ResponseEntity<byte[]> exportCustomers(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    byte[] content = customerExcelService.exportCustomers(keyword, status);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=customers-" + java.time.LocalDate.now() + ".xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(content);
  }

  @GetMapping("/import-template")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "下载客户导入模板（仅管理员）")
  public ResponseEntity<byte[]> importTemplate() {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=customer-import-template.xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(customerExcelService.generateTemplate());
  }
}
