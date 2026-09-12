/**
 * 定时导出任务 Controller（079-scheduled-export）。
 *
 * <p><b>为什么 7 个端点共用一个权限码</b>（FR-G14 的显式决策）：既有 97 条权限码字典中与定时导出相关的只有 `export:scheduled`
 * 一条，<b>没有</b>按读写拆分的读码。故全部 7 个端点声明同一个码——这与 {@link DataRetentionPolicyController} 的情况不同（那里存在
 * `retention:*` 四个码，读端点才只能停在全局认证）。 按 FR-G14"不得新增权限码"，此处不为读端点造新码。
 *
 * <p><b>权限码只回答"能不能用这个功能"，不回答"能不能动别人的任务"</b>：后者由服务层按当前登录用户判定 （FR-G16，见 {@code
 * ScheduledExportServiceImpl#requireOwned}）。两者是两件事：改造前该控制器完全没有权限注解，
 * 授权实际只由"是否登录"决定，任何已登录账号都能读、改、删任意用户的任务。
 */
package com.crm.controller;

import com.crm.dto.ScheduledExportExecutionResponse;
import com.crm.dto.ScheduledExportRequest;
import com.crm.dto.ScheduledExportResponse;
import com.crm.security.RequirePermission;
import com.crm.service.ScheduledExportService;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/scheduled-exports")
public class ScheduledExportController {

  private final ScheduledExportService scheduledExportService;

  public ScheduledExportController(ScheduledExportService scheduledExportService) {
    this.scheduledExportService = scheduledExportService;
  }

  @PostMapping
  @RequirePermission("export:scheduled")
  public ResponseEntity<ScheduledExportResponse> createScheduledExport(
      @Valid @RequestBody ScheduledExportRequest request) {
    return ResponseEntity.ok(scheduledExportService.createScheduledExport(request));
  }

  /** {@code userId} 参数仅用于一致性断言，不作为过滤条件——过滤一律以服务端身份为准（FR-G16）。 */
  @GetMapping
  @RequirePermission("export:scheduled")
  public ResponseEntity<List<ScheduledExportResponse>> getScheduledExports(
      @RequestParam Long userId) {
    return ResponseEntity.ok(scheduledExportService.getScheduledExports(userId));
  }

  @GetMapping("/{id}")
  @RequirePermission("export:scheduled")
  public ResponseEntity<ScheduledExportResponse> getScheduledExport(@PathVariable Long id) {
    return ResponseEntity.ok(scheduledExportService.getScheduledExport(id));
  }

  @PutMapping("/{id}/status")
  @RequirePermission("export:scheduled")
  public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
    scheduledExportService.updateStatus(id, status);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  @RequirePermission("export:scheduled")
  public ResponseEntity<Void> deleteScheduledExport(@PathVariable Long id) {
    scheduledExportService.deleteScheduledExport(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}/executions")
  @RequirePermission("export:scheduled")
  public ResponseEntity<List<ScheduledExportExecutionResponse>> getExecutions(
      @PathVariable Long id) {
    return ResponseEntity.ok(scheduledExportService.getExecutions(id));
  }

  @PostMapping("/{id}/execute-now")
  @RequirePermission("export:scheduled")
  public ResponseEntity<Void> executeNow(@PathVariable Long id) {
    scheduledExportService.executeNow(id);
    return ResponseEntity.ok().build();
  }
}
