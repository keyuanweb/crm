package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.followup.FollowUpRequest;
import com.crm.dto.followup.FollowUpResponse;
import com.crm.security.RequirePermission;
import com.crm.service.FollowUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * 跟进记录接口（contracts/follow-ups.md，FR-015）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 换成三个权限码。</b>跟进记录挂在客户
 * /线索/商机详情的时间线上，三个动作分别接 {@code follow_up:read} / {@code follow_up:create} / {@code
 * follow_up:update} （字典里本就有 create/update/delete 三个码，V75 已授给 SALES_REP / SUPPORT_MANAGER /
 * SUPPORT_AGENT，只是从未被任何 端点引用——"勾了不生效"；{@code follow_up:delete} 至今没有对应端点，本类只有列表/新增/编辑三个动作）。
 *
 * <p><b>读为什么也要设码</b>：{@code FollowUpService.page} 的实体可见性校验是<b>逐参数</b>做的——三个 id 都传了才逐条
 * 校验，<b>一个都不传时它不过滤任何东西</b>，直接分页拉全表。也就是说这条读路径的"数据范围"只在带 id 调用时成立，
 * 类级门一撤，无参调用就能拉走全部客户的全部跟进记录（含沟通内容）。故读码 {@code follow_up:read} 是新码，与 {@code invoice:read} / {@code
 * ticket:read} 同一口径：菜单/功能承诺了这件事的角色才拿得到。
 *
 * <p><b>授予范围</b>（V82）= 改造前那道门事实放行的 ADMIN / SALES / SUPPORT ∪ 已持有 follow_up:* 的 SALES_REP /
 * SUPPORT_MANAGER / SUPPORT_AGENT。VIEWER 刻意不在其中——它是种子数据里唯一"不插任何操作权限"的 角色（V46 注释原话），给它写码会打破这条种子不变式。
 */
@RestController
@RequestMapping("/api/v1/follow-ups")
@Tag(name = "跟进记录")
public class FollowUpController {

  private final FollowUpService followUpService;

  public FollowUpController(FollowUpService followUpService) {
    this.followUpService = followUpService;
  }

  @GetMapping
  @RequirePermission("follow_up:read")
  @Operation(summary = "按客户/线索/商机查询跟进记录（时间线）")
  public ApiResponse<PageResult<FollowUpResponse>> page(
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long leadId,
      @RequestParam(required = false) Long opportunityId,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(followUpService.page(customerId, leadId, opportunityId, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("follow_up:create")
  @Operation(summary = "添加跟进记录")
  public ApiResponse<FollowUpResponse> create(@Valid @RequestBody FollowUpRequest request) {
    return ApiResponse.ok(followUpService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("follow_up:update")
  @Operation(summary = "编辑跟进记录（仅本人或管理员）")
  public ApiResponse<FollowUpResponse> update(
      @PathVariable Long id, @Valid @RequestBody FollowUpRequest request) {
    return ApiResponse.ok(followUpService.update(id, request));
  }
}
