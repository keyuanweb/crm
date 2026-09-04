/** 定时导出任务调度器（079-scheduled-export）。

每 5 分钟扫描一次待执行的定时导出任务，触发实际导出操作。
 */
package com.crm.config;

import com.crm.service.ScheduledExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledExportScheduler {

  private static final Logger log = LoggerFactory.getLogger(ScheduledExportScheduler.class);

  private final ScheduledExportService scheduledExportService;

  public ScheduledExportScheduler(ScheduledExportService scheduledExportService) {
    this.scheduledExportService = scheduledExportService;
  }

  /** 每 5 分钟执行一次：扫描并执行到期的定时导出任务。 */
  @Scheduled(cron = "${crm.scheduler.export-cron:0 0/5 * * * ?}")
  public void scanAndExecute() {
    log.debug("Scanning for pending scheduled exports...");
    try {
      scheduledExportService.executePendingTasks();
      log.debug("Scheduled export scan completed.");
    } catch (Exception e) {
      log.error("Error during scheduled export scan", e);
    }
  }
}
