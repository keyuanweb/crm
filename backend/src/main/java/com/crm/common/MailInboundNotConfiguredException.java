package com.crm.common;

import com.crm.config.MailInboundStatus;

/**
 * 收信源未接入（101 收信侧诚实化）：调用同步端点且未打开演示开关时的唯一出路。
 *
 * <p><b>为什么与发信侧的 {@link MailNotConfiguredException}（裸 {@code RuntimeException}）不同</b>：那处的
 * <b>调用方有权选择</b>——营销群发可以降级为记 {@code SKIPPED} 并继续，事务性邮件则让请求失败；它是一个 <b>可以被处理</b>的信号。本处只有一条出路：回一个受控状态码
 * + 稳定 code。
 *
 * <p>继承 {@link BusinessException} 让它落进既有的 {@code handleBusiness}，拿到全仓统一的 {@code ApiResponse}
 * 信封；即使日后有人漏改处理器，父类的处理器仍在（100 的 {@code RateLimitExceededException} 已是这个形），不会掉进 {@code Exception}
 * catch-all 变成 500。
 */
public class MailInboundNotConfiguredException extends BusinessException {

  public MailInboundNotConfiguredException() {
    super(ErrorCode.MAIL_INBOUND_NOT_CONFIGURED, MailInboundStatus.NOT_CONFIGURED_MESSAGE);
  }
}
