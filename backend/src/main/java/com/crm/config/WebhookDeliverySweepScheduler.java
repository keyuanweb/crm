/**
 * 投递记录清扫：<b>启动扫一次 + 周期性清扫</b>（085-verify-green，FR-V07）。
 *
 * <p>把<b>中断残留</b>的在途（PENDING）投递记录判定为终态。085 把记录的生命周期提前到"派发"那一刻
 * （FR-V05），于是产生一个新的失败形态：进程在投递完成前终止时，该记录会永远停在"投递中"——那只是把 "记录丢失"换成了"记录永远在途"。本类负责给它一个可判定的归宿。
 *
 * <p><b>两种触发是两个互补的缺口，缺一不可</b>：
 *
 * <ul>
 *   <li><b>只做启动清扫</b>会漏掉：进程崩溃后<b>很快</b>重启（早于 {@code STALE_PENDING_AFTER} 阈值）时，
 *       该残留当次不会被判定；此后若再无重启，它就一直悬空——这恰恰违背 data-model.md 的 INV-3 （"不存在永远悬空的 PENDING"）。
 *   <li><b>只做周期性清扫</b>会漏掉：重启后那段悬空期最长可达一个调度周期（默认 5 分钟）。而 research.md:60 的既定决策与 plan.md:15
 *       的技术路线都写明"**应用启动时做一次清扫**"， data-model.md:51 也把"启动清扫"列为 INV-3 的执行机制。
 * </ul>
 *
 * <p><b>【订正，085，2026-09-13】</b>本类此前<b>只有</b>周期性清扫，缺启动清扫，与上述既定决策不符；
 * 而原注释把两者写成"<i>为什么是周期性清扫，而不是只在启动时扫一次</i>"的<b>二选一</b>，据此只保留了后者。 那个二选一的框架本身就是错的——两者互补而非互斥。订正依据见
 * {@code specs/085-verify-green/tasks.md} 的 T042 订正记录。
 *
 * <p>本类只负责<b>触发</b>，判定逻辑在 {@link com.crm.service.WebhookDeliverer#sweepStalePending()}
 * ——启动/调度属基础设施，数据变更属 Service 层（章程原则二）。
 */
package com.crm.config;

import com.crm.service.WebhookDeliverer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeliverySweepScheduler implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(WebhookDeliverySweepScheduler.class);

  private final WebhookDeliverer webhookDeliverer;

  public WebhookDeliverySweepScheduler(WebhookDeliverer webhookDeliverer) {
    this.webhookDeliverer = webhookDeliverer;
  }

  /**
   * <b>启动清扫</b>（research.md:60 的既定决策、data-model.md 的 INV-3）：应用启动时把上次进程终止留下的在途记录
   * 判定为终态，使重启后<b>立即</b>有归宿，而不必等下一个调度周期。
   *
   * <p>刻意<b>忽略</b> {@code args}：本类只需"被触发"，不读命令行参数。这一点是必须的——集成测试基类 {@code
   * AbstractIntegrationTest#resetDatabase} 会以 {@code null} 重放全部 {@code ApplicationRunner}， 读参会在那里
   * NPE。
   */
  @Override
  public void run(ApplicationArguments args) {
    sweep();
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
