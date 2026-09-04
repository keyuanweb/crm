/** 定时导出任务 Service 接口（079-scheduled-export）。 */
package com.crm.service;

import com.crm.dto.ScheduledExportExecutionResponse;
import com.crm.dto.ScheduledExportRequest;
import com.crm.dto.ScheduledExportResponse;
import java.util.List;

public interface ScheduledExportService {

  ScheduledExportResponse createScheduledExport(ScheduledExportRequest request);

  List<ScheduledExportResponse> getScheduledExports(Long userId);

  ScheduledExportResponse getScheduledExport(Long id);

  void updateStatus(Long id, String status);

  void deleteScheduledExport(Long id);

  List<ScheduledExportExecutionResponse> getExecutions(Long scheduledExportId);

  void executeNow(Long id);

  void executePendingTasks();
}
