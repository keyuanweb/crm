package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.call.CallRecordRequest;
import com.crm.dto.call.CallRecordResponse;
import com.crm.dto.call.CallStatsResponse;
import com.crm.security.RequirePermission;
import com.crm.service.CallRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 通话记录接口（061）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 换成权限码。</b>本模块在批 2 里
 * 属于"读也必须设码"的那一类：{@code CallRecordService} 里**一条数据范围过滤都没有**——{@code page} 只按关键词/ 方向/客户/时间过滤，{@code
 * detail} / {@code update} / {@code delete} 都是按主键直取，{@code stats} 更是全表统计
 * （通话次数、总时长）。类级门一撤，任何登录用户都能拉走全部通话记录（客户联系方式、通话时长、备注）并改写它们。
 *
 * <p>字典里原先只有 {@code call_record:create/update/delete} 三个动作码，**没有读码**——而本模块的列表/详情/统计 三个读接口都需要它。故 V82
 * 新增 {@code call_record:read}，与 {@code invoice:read} / {@code ticket:read} / {@code follow_up:read}
 * 同一口径：读与写分家，"能看"不牵连"能开"。
 *
 * <p><b>授予范围</b>（V82）= 改造前那道门事实放行的 ADMIN / SALES / SUPPORT ∪ 已持有 call_record:* 的 SALES_REP /
 * SUPPORT_MANAGER / SUPPORT_AGENT。顺带记录一个既存事实：菜单树里的「通话记录」（{@code call-records}）
 * 与「邮件同步」一样，**没有任何角色持有**——这个模块的入口一直是菜单之外的方式（CTI 自动创建 / 直达路由）。
 */
@RestController
@RequestMapping("/api/v1/call-records")
@Tag(name = "通话记录")
public class CallRecordController {

  private final CallRecordService recordService;

  public CallRecordController(CallRecordService recordService) {
    this.recordService = recordService;
  }

  @GetMapping
  @RequirePermission("call_record:read")
  @Operation(summary = "通话记录列表")
  public ApiResponse<PageResult<CallRecordResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
    LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
    return ApiResponse.ok(
        recordService.page(keyword, direction, customerId, fromTime, toTime, page, pageSize));
  }

  @GetMapping("/{id}")
  @RequirePermission("call_record:read")
  @Operation(summary = "通话记录详情")
  public ApiResponse<CallRecordResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(recordService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("call_record:create")
  @Operation(summary = "录入通话记录（CTI 自动创建也走此端点）")
  public ApiResponse<CallRecordResponse> create(@Valid @RequestBody CallRecordRequest request) {
    return ApiResponse.ok(recordService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("call_record:update")
  @Operation(summary = "编辑通话记录")
  public ApiResponse<CallRecordResponse> update(
      @PathVariable Long id, @Valid @RequestBody CallRecordRequest request) {
    return ApiResponse.ok(recordService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("call_record:delete")
  @Operation(summary = "删除通话记录")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    recordService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/stats")
  @RequirePermission("call_record:read")
  @Operation(summary = "通话统计")
  public ApiResponse<CallStatsResponse> stats(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String direction) {
    LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
    LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
    return ApiResponse.ok(recordService.stats(fromTime, toTime, direction));
  }
}
