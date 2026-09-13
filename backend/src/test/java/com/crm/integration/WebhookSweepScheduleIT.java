package com.crm.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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
 * 清扫的<b>周期触发</b>是否真的生效（085-verify-green，FR-V07）。
 *
 * <p><b>为什么必须有这一条</b>：清扫的<b>库级效果</b>已由 {@code
 * IntegrationHubIT#stalePendingDeliveriesAreSweptToFailed} 取证，但那一条是<b>直接调用</b> {@code
 * sweepStalePending()} —— 它证明"清扫逻辑正确"，**不证明"清扫会被触发"**。二者之间的缺口是真实存在的：cron 表达式写错、{@code @Scheduled}
 * 被误删、或 {@code @EnableScheduling} 缺失，上述库级用例**全都仍然为绿**，而 FR-V07 在生产静默失效（中断残留的记录
 * 永远停在"投递中"）。这是本仓库已经踩过的同类坑——"接线错误整批也能全仓绿"。
 *
 * <p><b>为什么断言是 {@code atLeast(2)} 而不是 {@code atLeastOnce()}</b>：启动清扫（见 {@link
 * WebhookSweepOnStartupIT}）已经会贡献<b>一次</b>调用，故"至少一次"已无法区分"周期性触发也在工作"。{@code atLeast(2)}
 * 的含义正是"除启动那一次之外，<b>还有</b>额外的调用"，即周期性触发确实在跑。若哪天 {@code @Scheduled} 被删掉而启动清扫还在，本用例会失败——这正是我们要的判别力。
 *
 * <p><b>为什么不能复用 {@code AbstractIntegrationTest}</b>：本类需要覆盖调度周期（把 cron 压到每秒），而该基类的上下文由默认配置构建
 * （改属性会另建上下文，复用它反而更绕）；且本类<b>不碰数据库</b>——{@code WebhookDeliverer} 整个被 mock 掉，清扫不会真的执行。故按 {@code
 * WebSocketHandshakeIT} 的先例，以最小上下文单独启动。
 */
@SpringBootTest(properties = "crm.scheduler.webhook-sweep-cron=*/1 * * * * ?")
@ActiveProfiles("test")
class WebhookSweepScheduleIT {

  /** 等待上限（毫秒）：cron 为每秒一次，10 秒是触发周期的 10 倍，足够容下任何瞬时抖动。 */
  private static final long AWAIT_MILLIS = 10_000L;

  @MockBean private WebhookDeliverer webhookDeliverer;

  @MockBean private RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void stubRedis() {
    when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));
  }

  @Test
  @DisplayName("清扫的周期触发确实生效：@Scheduled 接线被触发（除启动那次外还有额外调用）（FR-V07）")
  void scheduledSweepIsActuallyTriggered() {
    // 反向验证（已实测）：把 cron 改为永不触发的 "0 0 0 1 1 ?" 后，本断言在 10 秒后失败——此时只剩启动那一次调用，
    // 达不到 atLeast(2)。即本用例钉的是"周期性触发"，而不是"碰巧通过"。
    verify(webhookDeliverer, timeout(AWAIT_MILLIS).atLeast(2)).sweepStalePending();
  }
}
