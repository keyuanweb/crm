/**
 * 投递记录清扫调度器（085-verify-green，FR-V07）。
 *
 * <p>周期性把<b>中断残留</b>的在途（PENDING）投递记录判定为终态。085 把记录的生命周期提前到"派发"那一刻
 * （FR-V05），于是产生一个新的失败形态：进程在投递完成前终止时，该记录会永远停在"投递中"——那只是把 "记录丢失"换成了"记录永远在途"。本调度器负责给它一个可判定的归宿。
 *
 * <p><b>为什么是周期性清扫，而不是只在启动时扫一次</b>：只在启动时清扫会留下一个缺口——若进程崩溃后 <b>很快</b>重启（早于 {@code STALE_PENDING_AFTER}
 * 阈值），那条残留当次不会被判定；此后若再无重启， 它就一直悬空。周期性清扫把这个缺口彻底关掉。
 *
 * <p>本类只负责<b>触发</b>，判定逻辑在 {@link com.crm.service.WebhookDeliverer#sweepStalePending()}
 * ——启动/调度属基础设施，数据变更属 Service 层（章程原则二）。
 */
package com.crm.config;

import com.crm.service.WebhookDeliverer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeliverySweepScheduler {

  private static final Logger log = LoggerFactory.getLogger(WebhookDeliverySweepScheduler.class);

  private final WebhookDeliverer webhookDeliverer;

  public WebhookDeliverySweepScheduler(WebhookDeliverer webhookDeliverer) {
    this.webhookDeliverer = webhookDeliverer;
  }

  /** 每 5 分钟执行一次：把中断残留的在途投递记录判定为失败。 */
  @Scheduled(cron = "${crm.scheduler.webhook-sweep-cron:0 */5 * * * ?}")
  public void sweep() {
    try {
      int n = webhookDeliverer.sweepStalePending();
      if (n > 0) {
        log.warn("Webhook delivery sweep: {} 条中断残留的在途记录被判定为失败", n);
      }
    } catch (Exception e) {
      log.error("Webhook delivery sweep failed", e);
    }
  }
}
