package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.task.CalendarResponse;
import com.crm.dto.task.TaskRequest;
import com.crm.dto.task.TaskResponse;
import com.crm.security.RequirePermission;
import com.crm.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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
 * 任务接口（010，FR-T01~T09）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")} 换成写码，读接口不设码。</b>
 *
 * <p><b>读为什么不设码</b>：{@code TaskService} 的每条读路径都硬绑在调用者身上——{@code page} / {@code reminderSummary} /
 * {@code calendar} 第一句都是 {@code eq(TaskItem::getOwnerId, SecurityUtil.currentUserId())}，写路径则走
 * {@code requireOwned}（不是自己的任务一律 403）。任务表里没有"别人的数据"这个概念可泄漏，所以这几个读既不需要 数据范围也不需要权限码。
 *
 * <p><b>写为什么设码</b>：字典里本就有 {@code task:create/update/delete}（V75 已授给 SALES_REP / SUPPORT_MANAGER /
 * SUPPORT_AGENT），而这三个码此前**没有被任何端点引用**——角色页上勾了不生效。接上之后它们才真的管事：管理员可以在 角色页决定"谁能建任务"。{@code
 * toggle}（完成/重开）挂 {@code task:update}：它改的就是任务本身的状态，与 {@code update} 是同一个动作面。
 *
 * <p><b>授予范围</b>（V82）= 改造前那道门事实放行的 ADMIN / SALES / SUPPORT ∪ 持有「任务管理」菜单的角色 （SALES_MANAGER /
 * SALES_REP / SUPPORT_MANAGER / SUPPORT_AGENT）。菜单在这里就是承诺：这五个角色点得进任务页， 若不给写码，就会出现"页面能开、按钮
 * 403"的新版错配——正是 1.5 要消灭的形态。<b>唯一例外是 VIEWER</b>：它同样持有 「任务管理」菜单，但它是种子数据里唯一"不插任何操作权限"的角色（V46
 * 注释原话），给它写码会打破这条不变式； 它仍能打开任务页看到自己的任务（读不设码），只是不能新建。
 */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "任务")
public class TaskController {

  private final TaskService taskService;

  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @GetMapping
  @Operation(summary = "分页查询当前用户任务（关键字/状态/优先级/关联类型筛选）")
  public ApiResponse<PageResult<TaskResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String linkedType,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(taskService.page(keyword, status, priority, linkedType, page, pageSize));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("task:create")
  @Operation(summary = "创建任务（归属当前用户）")
  public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
    return ApiResponse.ok(taskService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("task:update")
  @Operation(summary = "编辑任务")
  public ApiResponse<TaskResponse> update(
      @PathVariable Long id, @Valid @RequestBody TaskRequest request) {
    return ApiResponse.ok(taskService.update(id, request));
  }

  @PostMapping("/{id}/toggle")
  @RequirePermission("task:update")
  @Operation(summary = "完成任务或重新打开（TODO↔DONE）")
  public ApiResponse<TaskResponse> toggle(@PathVariable Long id) {
    return ApiResponse.ok(taskService.toggle(id));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("task:delete")
  @Operation(summary = "逻辑删除任务")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    taskService.delete(id);
    return ApiResponse.ok();
  }

  @GetMapping("/reminder-summary")
  @Operation(summary = "提醒汇总（逾期数/今日到期数）")
  public ApiResponse<Map<String, Long>> reminderSummary() {
    return ApiResponse.ok(taskService.reminderSummary());
  }

  @GetMapping("/calendar")
  @Operation(summary = "按月日历数据")
  public ApiResponse<CalendarResponse> calendar(@RequestParam String month) {
    return ApiResponse.ok(taskService.calendar(month));
  }
}
