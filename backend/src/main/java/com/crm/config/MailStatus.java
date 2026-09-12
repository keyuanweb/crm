package com.crm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 邮件通道配置状态（030 / 一期诚信修复）：判断 SMTP 是否真正可用。
 *
 * <p>背景——{@link MailConfig} 在 {@code crm.mail.host} 为空时返回 null bean，而此前各发送路径在 null 时
 * <b>静默跳过发送、却把记录标记为 SENT</b>，导致界面上的"已发送 / 打开率 / 点击率"全部基于从未发出的邮件。
 *
 * <p>约定：所有发送路径必须先查本类；未配置时标记 {@code SKIPPED}（而非 SENT）并写入 {@link
 * #NOT_CONFIGURED_MESSAGE}。本类是"邮件是否真发出"的唯一判据，不要在别处重复判断 host 是否为空。
 */
@Component
public class MailStatus {

  /** 未配置 SMTP 时写入发送记录的统一说明。 */
  public static final String NOT_CONFIGURED_MESSAGE = "SMTP 未配置（crm.mail.host 为空），邮件未发送";

  private final boolean configured;

  public MailStatus(@Value("${crm.mail.host:}") String host) {
    this.configured = StringUtils.hasText(host);
  }

  /** SMTP 是否已配置；只有 true 才代表邮件可能真正发出。 */
  public boolean isConfigured() {
    return configured;
  }
}
