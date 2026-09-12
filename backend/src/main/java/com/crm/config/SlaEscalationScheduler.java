/**
 * SLA 自动升级调度器（1.3-sla-escalation）。
 *
 * <p>默认每 10 分钟扫描一次：进行中且 deadline 进入预警窗口的工单按 WARNING/OVERDUE 分级升级， 详见 {@link
 * com.crm.service.SlaEscalationService}。与另两个作业一样提供手动入口 （{@code POST
 * /api/v1/sla-policies/escalate-now}），测试与运维都不必等 cron。
 */
package com.crm.config;

import com.crm.service.SlaEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaEscalationScheduler {

  private static final Logger log = LoggerFactory.getLogger(SlaEscalationScheduler.class);

  private final SlaEscalationService slaEscalationService;

  public SlaEscalationScheduler(SlaEscalationService slaEscalationService) {
    this.slaEscalationService = slaEscalationService;
  }

  /** 每 10 分钟执行一次：扫描并按级升级 SLA 即将超时/已超时的工单。 */
  @Scheduled(cron = "${crm.scheduler.sla-cron:0 */10 * * * ?}")
  public void scanAndEscalate() {
    log.info("Starting scheduled SLA escalation scan...");
    try {
      int escalated = slaEscalationService.scanAndEscalate();
      log.info("Scheduled SLA escalation scan completed. escalated={}", escalated);
    } catch (Exception e) {
      log.error("Error during scheduled SLA escalation scan", e);
    }
  }
}
