package com.crm.support;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import java.util.List;
import java.util.Map;

/**
 * 上游响应体的公共构造器（104-ai-content-generation）：把 SDK 的 wire 形状放在**一处**。
 *
 * <p><b>为什么走 JSON 而不是 SDK 的链式 builder</b>：{@code Message} / {@code Usage} / {@code TextBlock} 的
 * builder 对<b>每一个必填字段</b>都抛 {@code IllegalStateException}（{@code citations} · {@code cacheCreation}
 * …），少一个就炸；而那种炸法在 Mockito 里格外难读——异常抛在 {@code when(...)} 的<b>实参</b>位置上，
 * 会把桩留在"未完成"状态，症状变成同一条线程上<b>后面</b>的用例集体报 {@code UnfinishedStubbing}（甚至波及别的测试类）。 走 {@code
 * JsonValue.convert(Message.class)} 则是 SDK 解析真实响应走的那条路：桩因此长得像<b>响应体</b>，
 * 而不是像"我自己写的构造代码"，且参数写错时抛在造值那一行、不牵连别人。
 *
 * <p><b>为什么单独一个类而不是各用例各写一份</b>：这些必填字段是 SDK 的实现细节（2.34.0 实测），不是被测代码的性质。 两份副本会在 SDK
 * 升级时分别以不同的方式烂掉，而"哪份是照着实测写的"无从判断。故 {@code AiContentServiceTest}（单测） 与 {@code
 * AiContentIT}（集成）共用本类，各自只负责"什么时候要什么响应"。
 *
 * <p>⚠️ 唯一的例外是 {@code BadRequestException}：SDK <b>没给它开</b> {@code statusCode(int)}（状态码由类自己钉死 400），
 * 而 {@code InternalServerException} 有。照实写，别为了"形式统一"给它补一个不存在的方法。
 */
public final class AnthropicTestResponses {

  private AnthropicTestResponses() {}

  /** 上游正常收尾（wire 取值，不用 SDK 枚举：桩要像响应体，不像调用方的写法）。 */
  public static final String END_TURN = "end_turn";

  /** 上游因 {@code max_tokens} 截断。 */
  public static final String MAX_TOKENS = "max_tokens";

  /** 单个文本块的响应。 */
  public static Message reply(String text, String stopReason, long inputTokens, long outputTokens) {
    return reply(List.of(text), stopReason, inputTokens, outputTokens);
  }

  /** 多文本块的响应（按序拼接的判据用）。 */
  public static Message reply(
      List<String> texts, String stopReason, long inputTokens, long outputTokens) {
    Map<String, Object> body =
        Map.of(
            "id", "msg_test",
            "type", "message",
            "role", "assistant",
            "model", "claude-opus-5",
            "content",
                texts.stream()
                    .map(
                        t ->
                            Map.<String, Object>of(
                                "type", "text", "text", t, "citations", List.of()))
                    .toList(),
            "stop_reason", stopReason,
            "usage",
                Map.of(
                    "input_tokens", inputTokens,
                    "output_tokens", outputTokens,
                    "cache_creation",
                        Map.of("ephemeral_1h_input_tokens", 0, "ephemeral_5m_input_tokens", 0)));
    return JsonValue.from(body).convert(Message.class);
  }

  /** 错误响应体：三个异常 builder 都要求它非空（缺了直接抛 {@code IllegalStateException}）。 */
  public static JsonValue errorBody() {
    return JsonValue.from(Map.of("type", "error"));
  }

  /** 上游 429。 */
  public static RateLimitException rateLimit() {
    return RateLimitException.builder()
        .headers(Headers.builder().build())
        .body(errorBody())
        .build();
  }

  /** 上游 5xx（状态码可设：{@code statusCode >= 500} 与 4xx 在被测代码里走两条不同的路）。 */
  public static InternalServerException serverError(int statusCode) {
    return InternalServerException.builder()
        .statusCode(statusCode)
        .headers(Headers.builder().build())
        .body(errorBody())
        .build();
  }

  /** 上游 400。⚠️ 状态码由类本身钉死，SDK 未提供 setter（见类 javadoc）。 */
  public static BadRequestException badRequest() {
    return BadRequestException.builder()
        .headers(Headers.builder().build())
        .body(errorBody())
        .build();
  }
}
