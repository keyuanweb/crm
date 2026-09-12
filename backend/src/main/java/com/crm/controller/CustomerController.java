package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerDetailResponse;
import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.customer.ImportResult;
import com.crm.security.RequirePermission;
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

/**
 * 客户接口（contracts/customers.md，FR-001~006）。
 *
 * <p><b>1.5：本 Controller 原先<b>完全没有写权限校验</b></b>——类上无 {@code @PreAuthorize}，写方法上也没有 注解，只有
 * import/export/import-template 三条挂着 {@code hasRole('ADMIN')}。也就是说创建/编辑客户的闸门
 * <b>只有数据范围</b>一层：任何登录用户，只要目标客户落在自己的可见集合内（085 之后的 ALL/DEPT 范围角色尤其 如此），就能改。矩阵里明明有 {@code
 * customer:create/update/delete/import/export}，一个都没接上。
 *
 * <p>唯一例外是<b>删除</b>：{@code CustomerService.delete} 上早已挂着
 * {@code @com.crm.security.RequirePermission("customer:delete")}（服务层，全限定名写法），所以删除一直只对持有
 * 该码的角色开放——这也是 {@code RoleIT.noPermissionReturns403}（SALES 删客户 → 403）此前能通过的原因。
 * 控制器上再挂同一个码只是让端点自描述，行为不变；<b>该码刻意不补授给任何角色</b>：SALES/SUPPORT 在 V46/V75 里都没有它，补授是扩权而非保留能力（详见 V80 §3
 * 的客户段）。
 *
 * <p>本项把写操作接到矩阵上；读操作（列表/流失预警/详情）保持无注解，因为 {@code CustomerService} 有真实的 数据范围过滤（{@code
 * applyDataScopeFilter} + {@code checkViewPermission}），这与联系人同一口径。
 *
 * <p>两处语义说明：
 *
 * <ul>
 *   <li>{@code /import-template} 挂 {@code customer:import}：它是导入的配套动作（拿模板才能导），原先只有 ADMIN 拿得到。挂
 *       import 码后 ADMIN 不变，SALES_MANAGER（矩阵里有 customer:import）也能拿——这正是 "矩阵说能就能"。
 *   <li>{@code /export} 挂 {@code customer:export}：该码在字典里存在，但 <b>V46/V75 没有给任何角色授过</b>，
 *       所以接上之后的效果与改造前（仅 ADMIN）完全一致，区别是这个码从此可以在角色页上勾选， 而不是一个永远勾不出、勾了也不生效的死码。
 * </ul>
 */
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
  @RequirePermission("customer:create")
  @Operation(summary = "创建客户")
  public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
    return ApiResponse.ok(customerService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("customer:update")
  @Operation(summary = "编辑客户")
  public ApiResponse<CustomerResponse> update(
      @PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
    return ApiResponse.ok(customerService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("customer:delete")
  @Operation(summary = "逻辑删除客户")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    customerService.delete(id);
    return ApiResponse.ok();
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequirePermission("customer:import")
  @Operation(summary = "Excel 批量导入客户")
  public ApiResponse<ImportResult> importCustomers(@RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    return ApiResponse.ok(customerExcelService.importCustomers(file.getInputStream()));
  }

  @GetMapping("/export")
  @RequirePermission("customer:export")
  @Operation(summary = "按当前筛选条件导出客户")
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
  @RequirePermission("customer:import")
  @Operation(summary = "下载客户导入模板")
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
