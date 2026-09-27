package com.crm.common;

import com.crm.config.AiStatus;

/**
 * AI 文本生成未启用／未配置（104-ai-content-generation）：调用生成端点且 {@code AiStatus.isConfigured()} 为 false 时的唯一出路。
 *
 * <p><b>为什么继承 {@link BusinessException}</b>（照 {@link MailInboundNotConfiguredException}
 * 的判例）：让它落进既有的 {@code handleBusiness}，拿到全仓统一的 {@code ApiResponse} 信封；即使日后有人漏改处理器，父类的处理器仍在，不会掉进
 * {@code Exception} catch-all 变成 500。本处只有一条出路：回 409 + 稳定 code。
 *
 * <p>消息取自 {@link AiStatus#NOT_CONFIGURED_MESSAGE}，不在本类另写一份——两处各写一句会在改一处时悄悄漂移。
 */
public class AiNotConfiguredException extends BusinessException {

  public AiNotConfiguredException() {
    super(ErrorCode.AI_NOT_CONFIGURED, AiStatus.NOT_CONFIGURED_MESSAGE);
  }
}
