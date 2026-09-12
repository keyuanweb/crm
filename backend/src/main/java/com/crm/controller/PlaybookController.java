package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.playbook.ActionCompleteRequest;
import com.crm.dto.playbook.ActionTemplateRequest;
import com.crm.dto.playbook.ActionTemplateResponse;
import com.crm.dto.playbook.ActionViewResponse;
import com.crm.security.RequirePermission;
import com.crm.service.SalesOpportunityActionService;
import com.crm.service.StageActionTemplateService;
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
 * 销售 Playbook 接口（045，FR-P01~P06）。
 *
 * <p><b>1.5 批 3：本类两半的门不同形，因此分两处置。</b>
 *
 * <ul>
 *   <li><b>动作模板（{@code /stage-actions} 四个端点）</b>：改造前是 {@code hasRole('ADMIN')}，换成新码 {@code
 *       playbook:manage}，该码不授给任何角色 ⇒ 范围不变（仅 ADMIN），只是从此勾得出来。 读端点（{@code GET}）与写共用本码：本模块没有只读消费者——「销售
 *       Playbook」菜单（{@code playbook}） 在种子里无人持有，销售实际用的是下面那半。若将来把该页授给只读角色，应拆出 {@code playbook:read}。
 *   <li><b>商机动作（{@code /sales-opportunities/{id}/actions}）</b>：改造前是 {@code
 *       hasAnyRole('ADMIN','SALES')}，换成**商机家族现成的** {@code opportunity:read} / {@code
 *       opportunity:update}，零补授。与 {@code SalesOpportunityController} 的判据一致：这条读写的对象就是 某张商机的阶段动作，复用
 *       opportunity 家族才不会让"商机"与"销售机会"在权限上分家。 副作用同 SalesOpportunityController：VIEWER 持有 {@code
 *       opportunity:read}，现在也读得到动作清单（此前被 角色字面量挡住）；写仍只对持有 {@code opportunity:update} 的角色开放。
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "销售 Playbook")
public class PlaybookController {

  private final StageActionTemplateService templateService;
  private final SalesOpportunityActionService actionService;

  public PlaybookController(
      StageActionTemplateService templateService, SalesOpportunityActionService actionService) {
    this.templateService = templateService;
    this.actionService = actionService;
  }

  // ===== 模板管理（配置面，仅 ADMIN） =====

  @GetMapping("/stage-actions")
  @RequirePermission("playbook:manage")
  @Operation(summary = "动作模板分页列表（按阶段筛选）")
  public ApiResponse<PageResult<ActionTemplateResponse>> page(
      @RequestParam(required = false) String stage,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(templateService.page(stage, page, pageSize));
  }

  @PostMapping("/stage-actions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("playbook:manage")
  @Operation(summary = "创建动作模板")
  public ApiResponse<ActionTemplateResponse> create(
      @Valid @RequestBody ActionTemplateRequest request) {
    return ApiResponse.ok(templateService.create(request));
  }

  @PutMapping("/stage-actions/{id}")
  @RequirePermission("playbook:manage")
  @Operation(summary = "编辑动作模板")
  public ApiResponse<ActionTemplateResponse> update(
      @PathVariable Long id, @Valid @RequestBody ActionTemplateRequest request) {
    return ApiResponse.ok(templateService.update(id, request));
  }

  @DeleteMapping("/stage-actions/{id}")
  @RequirePermission("playbook:manage")
  @Operation(summary = "删除动作模板（逻辑删除）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    templateService.delete(id);
    return ApiResponse.ok();
  }

  // ===== 销售机会动作（按商机家族的码放行） =====

  @GetMapping("/sales-opportunities/{id}/actions")
  @RequirePermission("opportunity:read")
  @Operation(summary = "销售机会当前阶段动作清单（含完成状态）")
  public ApiResponse<List<ActionViewResponse>> listActions(@PathVariable Long id) {
    return ApiResponse.ok(actionService.listForOpportunity(id));
  }

  @PostMapping("/sales-opportunities/{id}/actions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("opportunity:update")
  @Operation(summary = "勾选完成动作")
  public ApiResponse<ActionViewResponse> completeAction(
      @PathVariable Long id, @Valid @RequestBody ActionCompleteRequest request) {
    return ApiResponse.ok(actionService.complete(id, request.getTemplateId()));
  }
}
