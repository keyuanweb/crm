package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customer.ImportResult;
import com.crm.dto.lead.ConvertRequest;
import com.crm.dto.lead.LeadDetailResponse;
import com.crm.dto.lead.LeadRequest;
import com.crm.dto.lead.LeadResponse;
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.security.RequirePermission;
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

/**
 * 线索接口（contracts/leads.md）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 已移除。</b>该注解只认三个
 * 字面量角色名（{@code JwtAuthFilter} 发的权限就是 {@code "ROLE_" + role.code}），于是 081 新增的十个角色在 本 Controller
 * 的**全部**接口上恒 403。首当其冲的正是 SALES_MANAGER 与 SALES_REP：两者的菜单里有 「leads」，V75 也给它们授了 {@code
 * lead:create/update/delete/convert}（SALES_MANAGER 另有 {@code
 * lead:assign}）——矩阵承诺了、菜单也下发了，接口却一个都调不通。其余 081 角色（SUPPORT_*、
 * MARKETING_*、FINANCE_*、ANALYST、VIEWER）不持有任何 {@code lead:*} 码，本就不该进这个模块，移除粗粒度 门对它们是收窄而不是放宽。
 *
 * <p>写操作与状态机动作改挂字典里既有的动作码（见各方法上的 {@code @RequirePermission}）。两处码的取舍：
 *
 * <ul>
 *   <li>{@code POST /import} 挂 {@code lead:create}：字典里没有 {@code lead:import}，而导入的语义就是批量 创建——与
 *       ContactController 的导入复用 {@code contact:create} 同一口径，不新造码。
 *   <li>{@code POST /{id}/claim} 挂 {@code lead:assign}：字典里没有 {@code lead:claim}，"从线索池领取"就是
 *       把自己写成负责人，是 assign 的一个特例。
 * </ul>
 *
 * <p>列表、详情与模板三个读接口不设码，理由与 {@code GET /export} 正相反：它们的范围过滤是真的，不靠码 兜底。列表走 {@code LeadService.page} 里的
 * {@code resolveVisibleOwnerIds}（非 ADMIN 按可见 owner 过滤，无主线索引同样不可见），详情走 {@code
 * checkLeadPermission}；{@code GET /template} 是无实体数据的 静态模板，与 ContactController 的 /import-template
 * 同口径，没有行可泄露。
 *
 * <p><b>{@code GET /export} 挂 {@code lead:export}：这条导出没有数据范围过滤。</b> {@code
 * LeadExcelService.exportLeads} 自己拼 wrapper，条件只有 keyword/status/source，且直接 {@code selectList}
 * 取全表，任何人调它都能导出全量线索（含手机号与邮箱）——它在「行」与「调用者」两个 层面都无限制，所以必须有码拦着。字典的「线索管理」组原本只有
 * create/update/delete/convert/assign， 没有 export，故该码已补入 {@code PERMISSION_DEFS}，授予范围＝改造前类级门放行的三个角色
 * （ADMIN/SALES/SUPPORT）；刻意不顺手扩给 SALES_MANAGER / SALES_REP——那个门从未放行它们，而扩大一条 无范围过滤的全量导出不该由接线顺带完成。码名取
 * {@code lead:export} 而非 read：{@code lead:read} 这个 字面量已被开放平台的 API scope
 * 占用（OpenPlatformController），角色权限码沿用同名会有歧义。
 *
 * <p><b>⚠️ SUPPORT 的既有能力靠 V80 补授保留</b>：旧粗粒度门放行了 SUPPORT，而 SUPPORT 在 V46/V75 里一个 {@code lead:*}
 * 码都没有。直接接上动作码会让 SUPPORT 从"能改"变成 403，故 lead:create/update/delete/convert/assign 已按"保留既有能力"的口径补授给
 * SUPPORT——注意 SUPPORT 的菜单里 并没有「leads」，这是一项只存在于 API 层的能力。
 */
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
  @RequirePermission("lead:create")
  @Operation(summary = "创建线索")
  public ApiResponse<LeadResponse> create(@Valid @RequestBody LeadRequest request) {
    return ApiResponse.ok(leadService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("lead:update")
  @Operation(summary = "编辑线索")
  public ApiResponse<LeadResponse> update(
      @PathVariable Long id, @Valid @RequestBody LeadRequest request) {
    return ApiResponse.ok(leadService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("lead:delete")
  @Operation(summary = "删除线索（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    leadService.delete(id);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/assign")
  @RequirePermission("lead:assign")
  @Operation(summary = "分配线索给指定销售")
  public ApiResponse<LeadResponse> assign(@PathVariable Long id, @RequestParam Long ownerId) {
    return ApiResponse.ok(leadService.assign(id, ownerId));
  }

  @PostMapping("/{id}/claim")
  // 字典里没有 lead:claim；领取即把负责人改成自己，属 lead:assign 的特例（不新造码）。
  @RequirePermission("lead:assign")
  @Operation(summary = "从线索池领取线索")
  public ApiResponse<LeadResponse> claim(@PathVariable Long id) {
    return ApiResponse.ok(leadService.claim(id));
  }

  @PostMapping("/{id}/convert")
  @RequirePermission("lead:convert")
  @Operation(summary = "转化线索为客户+商机")
  public ApiResponse<LeadDetailResponse> convert(
      @PathVariable Long id, @Valid @RequestBody ConvertRequest request) {
    return ApiResponse.ok(leadService.convert(id, request));
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  // 字典里没有 lead:import，导入语义即批量创建，故复用 lead:create（同 ContactController 的口径）。
  @RequirePermission("lead:create")
  // P1（`import-excel` 5/60s，按 USER）：与 CustomerController#importCustomers 共用同一个 scope
  // ——「导入」是一个行为，配额该按行为算，不该因为落在哪个 controller 上而分成两个桶。
  @RateLimit(scope = "import-excel", limit = 5, windowSeconds = 60, by = RateLimitDimension.USER)
  @Operation(summary = "Excel 批量导入线索（FR-L11）")
  public ApiResponse<ImportResult> importLeads(@RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    return ApiResponse.ok(leadExcelService.importLeads(file.getInputStream()));
  }

  @GetMapping("/export")
  @RequirePermission("lead:export")
  @RateLimit(
      scope = "export-generate",
      limit = 10,
      windowSeconds = 60,
      by = RateLimitDimension.USER)
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
  @RateLimit(
      scope = "export-download",
      limit = 30,
      windowSeconds = 60,
      by = RateLimitDimension.USER)
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
