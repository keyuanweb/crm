package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crm.common.OutboundUrlValidator;
import com.crm.common.RateLimitExceededException;
import com.crm.config.AiStatus;
import com.crm.security.RateLimitStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 日预算闸的语义（104-ai-content-generation，U7 / FR-015 / FR-017 / plan D8）。
 *
 * <p><b>U7 问的是键的形状，而"键名写错"是一种没有任何行为用例能发现的缺陷</b>：它不报错、不改变响应，
 * 只是让该共用一个桶的两次调用各数一份，或者让两个概念悄悄合并成一个。故本类直接断言 {@link AiTokenBudget#key} 的字面量输出——它是包级可见的（照 {@code
 * RateLimitKeys} 的先例）， 就是为了让这条断言有可能存在。
 *
 * <p><b>为什么键族必须避开 022 的 {@code ai:ignore:}</b>：{@code SuggestionService} 用 {@code ai:ignore:}
 * 记"用户忽略过的建议"（TTL 90 天）。两族若混用，一次"忽略建议"会给预算 <b>记账</b>，而症状是"预算莫名其妙被扣掉"——两个不相干的功能通过一个字符串前缀耦合起来。
 *
 * <p>本类用裸 {@code mock(RateLimitStore)}（照 {@code MfaStateStoreTest} 的论述）：问的是"键与调用形状"，
 * 用具名断言比用功能替身更直接。⚠️ 也正因为是裸 mock，{@code usage} 未打桩时返回 0 ⇒ 走"未耗尽"分支： <b>在裸 mock
 * 上看到放行，不能当作"闸在工作"的证据</b>，那条由下面的 429 用例（先喂出已用量）承担。
 */
class AiTokenBudgetTest {

  private static final long USER = 42L;

  private static final int BUDGET = 100_000;

  /** 与 {@code AiTokenBudget.key} 的取值无关，只为把"有没有用上 store"与"键对不对"分开断言。 */
  private static RateLimitStore store() {
    return mock(RateLimitStore.class);
  }

  private static AiStatus statusWithBudget(int budget) {
    return new AiStatus(
        true,
        "https://api.anthropic.com",
        "sk-test",
        "claude-opus-5",
        4096,
        60,
        budget,
        new OutboundUrlValidator("api.anthropic.com"));
  }

  private static AiTokenBudget gate(RateLimitStore store, int budget) {
    return new AiTokenBudget(store, statusWithBudget(budget));
  }

  // ===== U7：键的形状 =====

  @Test
  @DisplayName("U7 键形：ai:gen:budget:user:{userId}:{yyyyMMdd}（plan D8），且与 022 的 ai:ignore: 互斥")
  void budgetKeyShape() {
    String key = AiTokenBudget.key(USER, LocalDate.of(2026, 9, 27));

    assertThat(key).isEqualTo("ai:gen:budget:user:42:20260927");
    assertThat(key)
        .as("前缀必须是 ai:gen:budget:（plan D8）；改它等于把当天已记的用量全部作废")
        .startsWith("ai:gen:budget:");
    assertThat(key)
        .as("撞上 022 的 ai:ignore: ⇒ 一次「忽略建议」会被记成预算，两个不相干的功能通过字符串耦合")
        .doesNotContain("ai:ignore:");
  }

  @Test
  @DisplayName("U7 键按用户与日期分桶：不同用户、不同日期都换键（「每日」由键本身表达）")
  void budgetKeySeparatesUserAndDay() {
    LocalDate day = LocalDate.of(2026, 9, 27);
    assertThat(AiTokenBudget.key(USER, day)).isNotEqualTo(AiTokenBudget.key(USER + 1, day));
    assertThat(AiTokenBudget.key(USER, day)).isNotEqualTo(AiTokenBudget.key(USER, day.plusDays(1)));
    // 零填充：2026-09-07 不能写成 202697（BASIC_ISO_DATE 会补零，抄成手拼字符串就会不一致）
    assertThat(AiTokenBudget.key(USER, LocalDate.of(2026, 9, 7))).endsWith(":20260907");
  }

  // ===== 查：出站之前的判定 =====

  @Test
  @DisplayName("未耗尽 ⇒ 放行，且读的正是同一个键（键形一致才算真的共用一个桶）")
  void belowBudgetAllows() {
    RateLimitStore store = store();
    when(store.usage(anyString())).thenReturn(BUDGET - 1L);

    gate(store, BUDGET).check(USER);

    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    verify(store).usage(key.capture());
    assertThat(key.getValue()).isEqualTo(AiTokenBudget.key(USER, LocalDate.now()));
  }

  @Test
  @DisplayName("已用量达到预算 ⇒ 429，且 Retry-After 在 (0, 86400] 内（FR-017 的受控 429）")
  void exhaustedBudgetRejectsWithRetryAfter() {
    RateLimitStore store = store();
    when(store.usage(anyString())).thenReturn((long) BUDGET);

    assertThatThrownBy(() -> gate(store, BUDGET).check(USER))
        .isInstanceOf(RateLimitExceededException.class)
        .satisfies(
            ex ->
                assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds())
                    .as("耗尽的是一个「今天」，Retry-After 应是到明天零点的秒数，而不是某个窗口长度")
                    .isBetween(1L, 86_400L));
  }

  @Test
  @DisplayName("预算 <= 0 ⇒ 闸关闭：一个 Redis 调用都不发生（不是「查了再放行」）")
  void nonPositiveBudgetDisablesGateEntirely() {
    RateLimitStore store = store();

    gate(store, 0).check(USER);
    gate(store, 0).charge(USER, 999);

    verifyNoInteractions(store);
  }

  @Test
  @DisplayName("未配置预算闸也照样拒绝：闸的判据来自配置值，与 AiStatus 别的项无关")
  void gateUsesItsOwnConfigValueOnly() {
    RateLimitStore store = store();
    when(store.usage(anyString())).thenReturn(5L);

    // 预算是 5，已用 5 ⇒ 恰好耗尽（边界取 >= 而不是 >：预算 5 就是「最多 5」）
    assertThatThrownBy(() -> gate(store, 5).check(USER))
        .isInstanceOf(RateLimitExceededException.class);
  }

  // ===== 记：成功之后的记账 =====

  @Test
  @DisplayName("记账：写同一个键，量为 input+output，窗口到明天零点（不估算、不预扣）")
  void chargeWritesUsageToTheSameKey() {
    RateLimitStore store = store();

    gate(store, BUDGET).charge(USER, 1_234);

    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> amount = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> window = ArgumentCaptor.forClass(Long.class);
    verify(store).charge(key.capture(), amount.capture(), window.capture());
    assertThat(key.getValue())
        .as("查与记必须是同一个键；写歪一处就等于开了两个桶，而两边各自看都对")
        .isEqualTo(AiTokenBudget.key(USER, LocalDate.now()));
    assertThat(amount.getValue()).isEqualTo(1_234L);
    assertThat(window.getValue()).isBetween(1L, 86_400L);
  }

  @Test
  @DisplayName("记账不会把「没花钱」写成一次写入（0 量在 store 层被忽略，见 RateLimitStoreTest）")
  void chargeIgnoresNonPositiveAmountAtStoreLevel() {
    RateLimitStore store = store();
    // 本类不在这一层判 0（判定在 store），但必须证明 0 不会被本类放大成别的数
    gate(store, BUDGET).charge(USER, 0);
    verify(store).charge(anyString(), eq(0L), anyLong());
  }

  // ===== Retry-After 的算术 =====

  @Test
  @DisplayName("secondsUntilTomorrow：正午 ⇒ 43200，零点整 ⇒ 86400，23:59:59.5 ⇒ 至少 1")
  void secondsUntilTomorrowArithmetic() {
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    LocalDate day = LocalDate.of(2026, 9, 27);

    assertThat(AiTokenBudget.secondsUntilTomorrow(day.atTime(12, 0).atZone(zone)))
        .isEqualTo(43_200L);
    assertThat(AiTokenBudget.secondsUntilTomorrow(day.atStartOfDay(zone))).isEqualTo(86_400L);
    // 向下取整会把「还剩 400ms」报成 0，而调用方判的是 > 0 ⇒ 该被拒的请求会被放行一次（同 RateLimitStore）
    assertThat(AiTokenBudget.secondsUntilTomorrow(day.atTime(23, 59, 59, 500_000_000).atZone(zone)))
        .as("不足 1 秒时必须报 1，不能报 0")
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("userId 为 null ⇒ 立刻拒绝（不许静默不受约束的调用）")
  void nullUserIdIsRejected() {
    RateLimitStore store = store();

    assertThatThrownBy(() -> gate(store, BUDGET).check(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("userId");
    verify(store, never()).usage(anyString());
  }

  @Test
  @DisplayName("Retry-After 用的是『到明天零点』而不是某个窗口：与 RateLimitStore.record 的窗口语义无关")
  void retryAfterIsNotAWindowLength() {
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    ZonedDateTime now = LocalDate.of(2026, 9, 27).atTime(9, 30, 15).atZone(zone);
    assertThat(AiTokenBudget.secondsUntilTomorrow(now))
        .as("两个读数必须在同一个 now 上取：取第二个 now 就会让断言变成一条有时差的等式")
        .isEqualTo(86_400L - now.toLocalTime().toSecondOfDay());
  }
}
