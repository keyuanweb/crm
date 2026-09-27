package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import com.crm.common.AiGenerationException;
import com.crm.common.AiNotConfiguredException;
import com.crm.common.ErrorCode;
import com.crm.common.RateLimitExceededException;
import com.crm.config.AiClientFactory;
import com.crm.config.AiStatus;
import com.crm.support.AnthropicTestResponses;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * 出网点的判门、错误映射、截断与用量（104-ai-content-generation，U2–U7 / FR-003 / FR-016 / FR-018）。
 *
 * <p><b>U2 是"未配置 ⇒ 零出站、零记录"的正面断言</b>（FR-003 的假绿通道 1：只断响应码的话， 一个"先发出去、失败了再回
 * 409"的实现照样绿）。故本条断的是<b>调用形状</b>：客户端工厂一次都没被问过、 审计一次都没被写过。
 *
 * <p><b>本类不问"提示词里有什么"</b>（那是 {@code AiPromptCatalogTest} 与 {@code AiContentIT} 的事），
 * 只问"两段怎么出去、用量怎么回来、失败怎么映射"。分开放是因为本类的桩全是 SDK 类型， 任何一条断言失败都指向"出网点本身"，而不是"装配错了"。
 *
 * <p>⚠️ <b>日志内容也在本类断言</b>（U4）：{@code AiContentService} 的告警是唯一可能把提示词原文带出去的地方，
 * 靠"读代码看着像不含"是不够的——本仓已有"注释里声明的东西没有任何东西看着"的先例。故用 logback 的 {@code ListAppender} 直接读日志事件。
 *
 * <p>⚠️ <b>桩一律写成"先造值、再 when"两行</b>（不是 {@code when(...).thenReturn(造值())} 一行）。理由不是排版： SDK 的 builder
 * 对每个必填字段都抛 {@code IllegalStateException}（{@code citations} / {@code cacheCreation} / {@code
 * body}），而那种异常<b>抛在 {@code when(...)} 的实参位置上</b>会把 Mockito 的桩留在"未完成"状态—— 症状是同一条线程上<b>后面</b>的用例集体报
 * {@code UnfinishedStubbing}，甚至波及别的测试类（本类初版即是： 13 个 error 里只有 6 个是被测代码相关的）。两行式让这类失败落在自己那条用例上。
 */
@ExtendWith(MockitoExtension.class)
class AiContentServiceTest {

  /** 哨兵：提示词与响应体里都塞进来，好让"它出现在了不该出现的地方"可判定。 */
  private static final String PROMPT_SENTINEL = "哨兵客户资料-13900001111";

  private static final String SYSTEM_SENTINEL = "哨兵系统前缀";

  private static final long USER = 7L;

  /** 上游线的取值（照 wire 写，不用 SDK 的枚举类型：桩要像响应体，不像调用方的写法）。 */
  private static final String END_TURN = "end_turn";

  private static final String MAX_TOKENS = "max_tokens";

  @Mock private AiStatus aiStatus;

  @Mock private AiClientFactory clientFactory;

  @Mock private AiTokenBudget tokenBudget;

  @Mock private AuditService auditService;

  @Mock private AnthropicClient client;

  @Mock private MessageService messageService;

  private AiContentService service;

  @BeforeEach
  void setUp() {
    service = new AiContentService(aiStatus, clientFactory, tokenBudget, auditService);
  }

  private static AiContentService.AiRequest request() {
    return new AiContentService.AiRequest(
        "email-draft", USER, "CUSTOMER", 1L, SYSTEM_SENTINEL, PROMPT_SENTINEL);
  }

  /**
   * 已配置：判门通过、工厂给出客户端、模型与上限有值。未打桩的用例不要调它（MockitoExtension 是严格模式）。
   *
   * <p>模型名与上限也在这里打桩，不是凑数：{@code MessageCreateParams} 是 Kotlin 类， {@code model(null)} 会抛 {@code
   * NullPointerException("Parameter specified as non-null is null")}——
   * 而且它抛在<b>建参</b>那一步，于是"上游返回错误"的用例会红成"根本没发出请求"，报出来的异常类型 与被测的错误映射完全无关。
   */
  private void configured() {
    when(aiStatus.isConfigured()).thenReturn(true);
    when(clientFactory.client()).thenReturn(Optional.of(client));
    when(client.messages()).thenReturn(messageService);
    when(aiStatus.model()).thenReturn("claude-opus-5");
    when(aiStatus.maxTokens()).thenReturn(4096);
  }

  // ===== 桩的构造：走 wire JSON，不走 builder =====

  /**
   * 一条上游响应 / 一个上游异常。
   *
   * <p>真正的构造在 {@link AnthropicTestResponses}——SDK 的 wire 形状（{@code citations} / {@code
   * cacheCreation} / {@code body} 这些必填字段）是<b>一处</b>事实，单测与 {@code AiContentIT} 共用，避免两份副本在 SDK
   * 升级时各烂各的。 本类的两个常量是<b>转发</b>，仅为让下面的用例读起来仍是 {@code END_TURN} / {@code MAX_TOKENS}。
   */
  private static Message reply(
      String text, String stopReason, long inputTokens, long outputTokens) {
    return AnthropicTestResponses.reply(text, stopReason, inputTokens, outputTokens);
  }

  private static Message reply(
      List<String> texts, String stopReason, long inputTokens, long outputTokens) {
    return AnthropicTestResponses.reply(texts, stopReason, inputTokens, outputTokens);
  }

  private static RateLimitException upstreamRateLimit() {
    return AnthropicTestResponses.rateLimit();
  }

  private static InternalServerException upstreamServerError() {
    return AnthropicTestResponses.serverError(500);
  }

  private static BadRequestException upstreamBadRequest() {
    return AnthropicTestResponses.badRequest();
  }

  // ===== U2：未配置 ⇒ 零出站、零记录 =====

  @Test
  @DisplayName("U2 未配置 ⇒ AI_NOT_CONFIGURED(409)，且客户端工厂与审计都没被碰过")
  void unconfiguredMeansZeroEgressAndZeroAudit() {
    when(aiStatus.isConfigured()).thenReturn(false);

    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(AiNotConfiguredException.class)
        .satisfies(
            ex ->
                assertThat(((AiNotConfiguredException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AI_NOT_CONFIGURED));

    // 这是本项最值钱的两行：判门在**出站之前**是结构性事实，不是"记得先判门"
    verifyNoInteractions(clientFactory);
    verifyNoInteractions(auditService);
    // 预算闸也在出站之前，未配置时不该被碰（省掉一次 Redis 往返，也省掉"配置没开却先扣额度"）
    verifyNoInteractions(tokenBudget);
  }

  @Test
  @DisplayName("U2-b 已配置但工厂给不出客户端 ⇒ 同样是 409（两道门的第二道兜底）")
  void configurationWithoutClientIsStillRejected() {
    when(aiStatus.isConfigured()).thenReturn(true);
    when(clientFactory.client()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(AiNotConfiguredException.class);
    verifyNoInteractions(auditService);
  }

  // ===== 预算闸在出站之前（FR-015/FR-017） =====

  @Test
  @DisplayName("预算闸与判门同侧：check 在取客户端之前，耗尽时一个出站动作都不发生")
  void budgetGateRunsBeforeEgress() {
    long retryAfter = 3600L;
    when(aiStatus.isConfigured()).thenReturn(true);
    doThrow(new RateLimitExceededException(retryAfter)).when(tokenBudget).check(USER);

    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(RateLimitExceededException.class)
        .satisfies(
            ex ->
                assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds())
                    .as("Retry-After 原样透传：它由闸算出（到明天零点），不是出网点另算一个数")
                    .isEqualTo(retryAfter));
    verifyNoInteractions(clientFactory);
    verifyNoInteractions(auditService);
  }

  @Test
  @DisplayName("预算记在请求带的 userId 上（与「能看什么」同一次身份解析）")
  void budgetUsesTheRequestsUserId() {
    configured();
    Message ok = reply("正文", END_TURN, 10, 20);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(ok);

    service.generate(request());

    verify(tokenBudget).check(USER);
    verify(tokenBudget).charge(USER, 30L);
    InOrder order = inOrder(tokenBudget, clientFactory);
    order.verify(tokenBudget).check(USER);
    order.verify(clientFactory).client();
  }

  // ===== U3 / U4：上游错误的映射 =====

  @Test
  @DisplayName("U3 上游 429（RateLimitException）⇒ AI_UPSTREAM_UNAVAILABLE(503)，不是「你请求太快」")
  void upstreamRateLimitMapsToUpstreamUnavailable() {
    configured();
    RateLimitException fromUpstream = upstreamRateLimit();
    when(messageService.create(any(MessageCreateParams.class))).thenThrow(fromUpstream);

    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(AiGenerationException.class)
        .satisfies(
            ex ->
                assertThat(((AiGenerationException) ex).getErrorCode())
                    .as("上游忙 ≠ 客户端太快：复用 RATE_LIMITED 会把排查方向整个引偏")
                    .isEqualTo(ErrorCode.AI_UPSTREAM_UNAVAILABLE));
  }

  @Test
  @DisplayName("U3-b IO 异常（连不上 / 超时）同样映射为 503")
  void ioFailureMapsToUpstreamUnavailable() {
    configured();
    AnthropicIoException ioFailure = new AnthropicIoException("connection reset");
    when(messageService.create(any(MessageCreateParams.class))).thenThrow(ioFailure);

    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(AiGenerationException.class)
        .satisfies(
            ex ->
                assertThat(((AiGenerationException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AI_UPSTREAM_UNAVAILABLE));
  }

  @Test
  @DisplayName("U4 上游 5xx ⇒ 503；上游 4xx ⇒ 422（重试同一输入通常同样失败，故两码分开）")
  void upstreamStatusCodesSplitIntoTwoCodes() {
    configured();
    AnthropicServiceException serverError = upstreamServerError();
    when(messageService.create(any(MessageCreateParams.class))).thenThrow(serverError);
    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(AiGenerationException.class)
        .satisfies(
            ex ->
                assertThat(((AiGenerationException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AI_UPSTREAM_UNAVAILABLE));

    AnthropicServiceException badRequest = upstreamBadRequest();
    when(messageService.create(any(MessageCreateParams.class))).thenThrow(badRequest);
    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(AiGenerationException.class)
        .satisfies(
            ex ->
                assertThat(((AiGenerationException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AI_GENERATION_REJECTED));
  }

  @Test
  @DisplayName("U4-b 失败路径：不写审计、不记账，且日志与异常里都不含提示词原文")
  void failurePathsWriteNothingAndLeakNothing() {
    configured();
    AnthropicIoException ioFailure = new AnthropicIoException("哨兵异常文本-" + PROMPT_SENTINEL);
    when(messageService.create(any(MessageCreateParams.class))).thenThrow(ioFailure);

    Logger logger = (Logger) LoggerFactory.getLogger(AiContentService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(() -> service.generate(request()))
          .isInstanceOf(AiGenerationException.class);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).as("上游异常必须留下一条告警（否则线上只能看到 503 而没有任何线索）").isNotEmpty();
    assertThat(appender.list)
        .as("日志是唯一可能把提示词带出去的地方：它只能记能力名与异常类型")
        .allSatisfy(
            event -> {
              assertThat(event.getFormattedMessage()).doesNotContain(PROMPT_SENTINEL);
              assertThat(event.getFormattedMessage()).doesNotContain(SYSTEM_SENTINEL);
            });
    verifyNoInteractions(auditService);
    verify(tokenBudget, never()).charge(anyLong(), anyLong());
  }

  // ===== U5：截断 =====

  @Test
  @DisplayName("U5 上游因 max_tokens 截断 ⇒ truncated=true，仍按成功返回（不得当完整结果）")
  void truncatedResponseIsFlagged() {
    configured();
    Message truncated = reply("半截的正文", MAX_TOKENS, 100, 4096);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(truncated);

    AiContentService.AiGeneration generation = service.generate(request());

    assertThat(generation.truncated()).isTrue();
    assertThat(generation.text()).isEqualTo("半截的正文");
  }

  @Test
  @DisplayName("U5-b 空输出且非截断 ⇒ 422（拒答不当成成功）；空输出但截断 ⇒ 照旧成功")
  void blankOutputIsRejectedUnlessTruncated() {
    configured();
    Message blank = reply("   ", END_TURN, 10, 0);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(blank);
    assertThatThrownBy(() -> service.generate(request()))
        .isInstanceOf(AiGenerationException.class)
        .satisfies(
            ex ->
                assertThat(((AiGenerationException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AI_GENERATION_REJECTED));

    Message blankButTruncated = reply("", MAX_TOKENS, 10, 0);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(blankButTruncated);
    assertThat(service.generate(request()).truncated()).as("截断的空正文仍有意义（上限调大即可续写），不该被当成拒答").isTrue();
  }

  // ===== U6：用量与审计 detail =====

  @Test
  @DisplayName("U6 token 读数取自 SDK 的 usage（FR-016：不得估算），且审计只落元数据")
  void usageComesFromTheSdkAndAuditHasMetadataOnly() {
    configured();
    Message ok = reply("正文", END_TURN, 1234, 567);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(ok);

    AiContentService.AiGeneration generation = service.generate(request());

    assertThat(generation.inputTokens()).isEqualTo(1234L);
    assertThat(generation.outputTokens()).isEqualTo(567L);
    assertThat(generation.durationMillis()).isGreaterThanOrEqualTo(0L);

    ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
    verify(auditService)
        .record(
            org.mockito.ArgumentMatchers.eq(AiContentService.AUDIT_ACTION),
            org.mockito.ArgumentMatchers.eq("CUSTOMER"),
            org.mockito.ArgumentMatchers.eq(1L),
            detail.capture());
    assertThat(detail.getValue())
        .as("审计只放元数据：能力名 / token / 耗时")
        .contains("capability=email-draft")
        .contains("inputTokens=1234")
        .contains("outputTokens=567")
        .as("提示词原文与客户数据一律不进审计 detail（FR-016）")
        .doesNotContain(PROMPT_SENTINEL)
        .doesNotContain(SYSTEM_SENTINEL);
    assertThat(AiContentService.auditDetail("email-draft", 1234, 567, 12))
        .as("detail 的构造是包级可见的纯函数——它若被改宽，上面那条捕获断言是唯一会红的地方")
        .isEqualTo("capability=email-draft; inputTokens=1234; outputTokens=567; durationMs=12");
  }

  @Test
  @DisplayName("U6-b 送出去的两段：system 是稳定前缀、user 才含资料，且模型/上限取自配置")
  void requestShapeKeepsSystemAndUserSeparate() {
    configured();
    when(aiStatus.model()).thenReturn("claude-opus-5");
    when(aiStatus.maxTokens()).thenReturn(4096);
    Message ok = reply("正文", END_TURN, 1, 1);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(ok);

    service.generate(request());

    ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
    verify(messageService).create(params.capture());
    assertThat(params.getValue().system().orElseThrow().asString()).isEqualTo(SYSTEM_SENTINEL);
    assertThat(params.getValue().messages()).hasSize(1);
    assertThat(params.getValue().messages().get(0).content().asString()).isEqualTo(PROMPT_SENTINEL);
    assertThat(params.getValue().maxTokens()).isEqualTo(4096L);
    assertThat(params.getValue().model().asString()).isEqualTo("claude-opus-5");
  }

  @Test
  @DisplayName("U6-c 记的是 input+output 的和（计费口径只此一处）")
  void chargeSumsInputAndOutput() {
    configured();
    Message ok = reply("正文", END_TURN, 11, 22);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(ok);

    service.generate(request());

    verify(tokenBudget).charge(USER, 33L);
    verify(tokenBudget, never()).charge(anyLong(), org.mockito.ArgumentMatchers.eq(11L));
  }

  @Test
  @DisplayName("U6-d 文本块拼接：多个块按序换行拼接（不乱序、不把非文本块字符串化）")
  void multipleTextBlocksAreJoinedInOrder() {
    configured();
    Message twoBlocks = reply(List.of("第一段", "第二段"), END_TURN, 1, 2);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(twoBlocks);

    assertThat(service.generate(request()).text()).isEqualTo("第一段\n第二段");
  }

  @Test
  @DisplayName("审计落点沿用请求里的 entityType/entityId（邮件草稿在保存前没有实体）")
  void auditUsesTheRequestsAnchor() {
    configured();
    Message ok = reply("正文", END_TURN, 1, 1);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(ok);

    service.generate(
        new AiContentService.AiRequest("email-draft", USER, "CUSTOMER", null, "s", "u"));

    verify(auditService)
        .record(
            org.mockito.ArgumentMatchers.eq(AiContentService.AUDIT_ACTION),
            org.mockito.ArgumentMatchers.eq("CUSTOMER"),
            org.mockito.ArgumentMatchers.isNull(),
            anyString());
  }
}
