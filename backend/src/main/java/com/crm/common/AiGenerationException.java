package com.crm.common;

/**
 * AI 生成失败（104-ai-content-generation）：上游不可用，或上游给出了不可用的输出。
 *
 * <p>两种形态各有一个工厂方法，**不暴露通用构造器**——这样"要不要把上游那句话透给用户"就不是每个调用点各自决定的事：上游的异常文本里可能带上响应体原文
 * （在极端情况下包含请求侧内容），而本仓的规矩是<b>错误消息只放我们自己写的固定文案</b>（FR-004 的同一考虑）。 上游细节进日志，不进响应。
 */
public class AiGenerationException extends BusinessException {

  private AiGenerationException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  /**
   * 上游不可用／超时／限流 ⇒ 503（可重试）。
   *
   * <p>⚠️ 上游回 429 也走这里，**不**映射成 {@code RATE_LIMITED}：那是"你请求太快"，这是"上游现在忙"，
   * 调用方的正确反应不同（前者退避自己、后者稍后重试或走降级路径）。
   */
  public static AiGenerationException upstreamUnavailable() {
    return new AiGenerationException(
        ErrorCode.AI_UPSTREAM_UNAVAILABLE, ErrorCode.AI_UPSTREAM_UNAVAILABLE.getMessage());
  }

  /** 上游拒答，或返回了空内容 ⇒ 422（重试同一输入通常同样失败，故与 503 分开）。 */
  public static AiGenerationException rejected() {
    return new AiGenerationException(
        ErrorCode.AI_GENERATION_REJECTED, ErrorCode.AI_GENERATION_REJECTED.getMessage());
  }
}
