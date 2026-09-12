package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customfield.CustomFieldRequest;
import com.crm.dto.customfield.CustomFieldResponse;
import com.crm.security.RequirePermission;
import com.crm.service.CustomFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/**
 * 自定义字段接口（016，FR-S01）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 撤除，换成 {@code custom_field:read} +
 * 三个写码。</b>
 *
 * <p><b>{@code definitions} 不设码</b>：它是**元数据读**（某实体启用的字段定义，供表单渲染），前端由 {@code useCustomFieldFilters}
 * 用在客户 / 线索 / 商机 / 工单四个列表页上——凡是有这几个菜单的角色都要它。原先的 方法级 {@code hasAnyRole('ADMIN','SALES','SUPPORT')}
 * 因此把 SALES_REP / SUPPORT_AGENT 等角色的自定义字段筛选 静默打没了（页面照常打开，只是筛选栏少了几项，不报错）。给一个"所有实体页面都要"的元数据读设码，只会再造一次
 * 同样的静默缺失；字段的**值**另有一层 {@code field_permission} 在管（那是 3.5 字段级权限的范围，不在本轮）。
 *
 * <p><b>{@code page} 用 {@code custom_field:read}</b>（1.5 新增读码）：字段定义的全量列表是**配置面**（含未启用项、 排序、校验规则），与
 * definitions 不是一回事。授予范围 = 持有「自定义字段」菜单的角色（ADMIN、ANALYST）。
 *
 * <p>写三件套挂 {@code custom_field:create/update/delete}：V75 授给了 ANALYST，而字典里此前根本没有这三个码 （V80 才补进字典），所以
 * ANALYST 手上是有码无门；同时给 ADMIN 补授——ADMIN 靠切面直通，列出来只为让角色页的 勾选状态与实际一致。SystemEnhancementIT 钉着"SUPPORT 建字段
 * → 403"，撤门后这条断言改由 {@code custom_field:create} 满足（SUPPORT 不持有它）。
 */
@RestController
@RequestMapping("/api/v1/custom-fields")
@Tag(name = "系统增强")
public class CustomFieldController {

  private final CustomFieldService customFieldService;

  public CustomFieldController(CustomFieldService customFieldService) {
    this.customFieldService = customFieldService;
  }

  @GetMapping
  @RequirePermission("custom_field:read")
  @Operation(summary = "字段定义分页列表（按实体筛选）")
  public ApiResponse<PageResult<CustomFieldResponse>> page(
      @RequestParam(required = false) String entityType,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(customFieldService.page(entityType, page, pageSize));
  }

  @GetMapping("/definitions")
  @Operation(summary = "某实体启用的字段定义（业务用户读取，供表单渲染）")
  public ApiResponse<List<CustomFieldResponse>> definitions(@RequestParam String entityType) {
    return ApiResponse.ok(customFieldService.listByEntity(entityType));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("custom_field:create")
  @Operation(summary = "创建字段定义")
  public ApiResponse<CustomFieldResponse> create(@Valid @RequestBody CustomFieldRequest request) {
    return ApiResponse.ok(customFieldService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("custom_field:update")
  @Operation(summary = "编辑字段定义")
  public ApiResponse<CustomFieldResponse> update(
      @PathVariable Long id, @Valid @RequestBody CustomFieldRequest request) {
    return ApiResponse.ok(customFieldService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("custom_field:delete")
  @Operation(summary = "删除字段定义（物理清理关联值）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    customFieldService.delete(id);
    return ApiResponse.ok();
  }
}
