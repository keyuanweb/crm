package com.crm.integration;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.service.WebhookDeliverer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

/**
 * <b>启动清扫</b>是否真的发生（085-verify-green，FR-V07）。
 *
 * <p><b>为什么必须有这一条</b>：{@code research.md:60} 的既定决策与 {@code plan.md:15} 的技术路线都写明"应用启动时做一次 清扫"，{@code
 * data-model.md:51} 也把"启动清扫"列为 INV-3 的执行机制。但本仓库的实现<b>一度只有周期性清扫</b>
 * ——启动清扫缺失，而"清扫会被触发"这件事在库级用例里<b>完全不可见</b>（那些用例是直接调用 {@code sweepStalePending()}
 * 取证的）。若启动清扫再次被删掉，全仓仍会绿。本用例把这条决策钉住。
 *
 * <p><b>为什么这条能唯一地指向"启动清扫"</b>：cron 被设为 {@code 0 0 0 1 1 ?}（1 月 1 日 00:00:00），在本用例的生存期内
 * <b>永不触发</b>。于是"清扫被调用过"这件事<b>只可能</b>来自启动路径，与周期性触发完全解耦——这是一条<b>互斥</b>的判据， 不是"大概是启动时调的"。又因 {@code
 * ApplicationRunner} 在上下文就绪<b>之前</b>执行完毕，断言无需等待，是确定性的。
 *
 * <p>周期性触发的对应用例见 {@link WebhookSweepScheduleIT}——两者一起才覆盖"两种触发都在"。
 */
@SpringBootTest(properties = "crm.scheduler.webhook-sweep-cron=0 0 0 1 1 ?")
@ActiveProfiles("test")
class WebhookSweepOnStartupIT {

  @MockBean private WebhookDeliverer webhookDeliverer;

  @MockBean private RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void stubRedis() {
    when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));
  }

  @Test
  @DisplayName("应用启动时确实做了一次清扫：ApplicationRunner 触发 sweepStalePending()（FR-V07 / INV-3）")
  void startupSweepIsActuallyTriggered() {
    // 反向验证（已实测）：令 WebhookDeliverySweepScheduler 不再 implements ApplicationRunner 后，
    // 本断言立即失败（Wanted but not invoked）——因为 cron 永不触发，没有任何其它来源能产生这次调用。
    verify(webhookDeliverer, atLeastOnce()).sweepStalePending();
  }
}
