package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.task.CalendarResponse;
import com.crm.dto.task.TaskRequest;
import com.crm.dto.task.TaskResponse;
import com.crm.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
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

/** 任务接口（010，FR-T01~T09）。 */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "任务")
@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")
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
  @Operation(summary = "创建任务（归属当前用户）")
  public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
    return ApiResponse.ok(taskService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑任务")
  public ApiResponse<TaskResponse> update(
      @PathVariable Long id, @Valid @RequestBody TaskRequest request) {
    return ApiResponse.ok(taskService.update(id, request));
  }

  @PostMapping("/{id}/toggle")
  @Operation(summary = "完成任务或重新打开（TODO↔DONE）")
  public ApiResponse<TaskResponse> toggle(@PathVariable Long id) {
    return ApiResponse.ok(taskService.toggle(id));
  }

  @DeleteMapping("/{id}")
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
