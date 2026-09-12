/**
 * 数据保留策略调度器（080-data-retention）。
 *
 * <p>每天凌晨 2 点执行一次数据归档扫描，处理到期的保留策略。
 */
package com.crm.config;

import com.crm.service.DataRetentionPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataRetentionScheduler {

  private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

  private final DataRetentionPolicyService dataRetentionPolicyService;

  public DataRetentionScheduler(DataRetentionPolicyService dataRetentionPolicyService) {
    this.dataRetentionPolicyService = dataRetentionPolicyService;
  }

  /** 每天凌晨 2:00 执行一次：扫描并执行到期的数据归档策略。 */
  @Scheduled(cron = "${crm.scheduler.retention-cron:0 0 2 * * ?}")
  public void scanAndArchive() {
    log.info("Starting scheduled data retention archival scan...");
    try {
      dataRetentionPolicyService.executeArchival();
      log.info("Scheduled data retention archival scan completed.");
    } catch (Exception e) {
      log.error("Error during scheduled data retention archival scan", e);
    }
  }
}
