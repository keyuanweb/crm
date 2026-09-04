/** 定时导出任务 Controller（079-scheduled-export）。 */
package com.crm.controller;

import com.crm.dto.ScheduledExportExecutionResponse;
import com.crm.dto.ScheduledExportRequest;
import com.crm.dto.ScheduledExportResponse;
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
  public ResponseEntity<ScheduledExportResponse> createScheduledExport(
      @Valid @RequestBody ScheduledExportRequest request) {
    return ResponseEntity.ok(scheduledExportService.createScheduledExport(request));
  }

  @GetMapping
  public ResponseEntity<List<ScheduledExportResponse>> getScheduledExports(
      @RequestParam Long userId) {
    return ResponseEntity.ok(scheduledExportService.getScheduledExports(userId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ScheduledExportResponse> getScheduledExport(@PathVariable Long id) {
    return ResponseEntity.ok(scheduledExportService.getScheduledExport(id));
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
    scheduledExportService.updateStatus(id, status);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteScheduledExport(@PathVariable Long id) {
    scheduledExportService.deleteScheduledExport(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}/executions")
  public ResponseEntity<List<ScheduledExportExecutionResponse>> getExecutions(
      @PathVariable Long id) {
    return ResponseEntity.ok(scheduledExportService.getExecutions(id));
  }

  @PostMapping("/{id}/execute-now")
  public ResponseEntity<Void> executeNow(@PathVariable Long id) {
    scheduledExportService.executeNow(id);
    return ResponseEntity.ok().build();
  }
}
