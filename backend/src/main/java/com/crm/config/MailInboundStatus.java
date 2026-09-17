package com.crm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 邮件收信通道配置状态（101 收信侧诚实化）：判断本部署有没有真实的收信源。
 *
 * <p>背景——062 交付的 {@code POST /mail-accounts/{id}/sync} 在没有收信源时也照样生成一条 {@code syncStatus=SYNCED}
 * 的记录（主题"模拟同步邮件"），界面于是无法区分"真的收到了一封邮件"与"点了下按钮"。 本类把"这个部署有没有收信源"从<b>沉默</b>变成<b>显式</b>。
 *
 * <p>约定：收信同步路径必须先查本类；未打开演示开关时抛 {@link com.crm.common.MailInboundNotConfiguredException}（回 409
 * 且不写任何记录）。本类是"收信侧能否生成记录"的 <b>唯一判据</b>；真接 IMAP 时改的也是这里，不要在别处重复判断配置。
 *
 * <p><b>本开关只表示"允许生成演示记录"，不是连通性探测</b>：{@code demo-enabled=true} 而 IMAP 完全不存在 时，端点照样返回 200 并写出一条
 * {@code SIMULATED} 记录——那是演示数据，不是真实收信。
 */
@Component
public class MailInboundStatus {

  /** 未接入收信源时对外返回的统一说明。 */
  public static final String NOT_CONFIGURED_MESSAGE = "未接入收信源（IMAP），同步未执行";

  private final boolean demoEnabled;

  public MailInboundStatus(@Value("${crm.mail.inbound.demo-enabled:false}") boolean demoEnabled) {
    this.demoEnabled = demoEnabled;
  }

  /** 是否允许生成演示用收信记录；缺省 false（即默认部署不产生任何收信记录）。 */
  public boolean isDemoEnabled() {
    return demoEnabled;
  }
}
