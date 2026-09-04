/** 定时导出调度器（079-scheduled-export）：Spring Schedule 定时执行。 */
package com.crm.service;

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

  /** 每分钟检查一次待执行任务。 */
  @Scheduled(cron = "0 * * * * *")
  public void executePendingTasks() {
    log.debug("Checking for pending scheduled exports...");
    try {
      scheduledExportService.executePendingTasks();
    } catch (Exception e) {
      log.error("Error in scheduled export execution", e);
    }
  }
}
