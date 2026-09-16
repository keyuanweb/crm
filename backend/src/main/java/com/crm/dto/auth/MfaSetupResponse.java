package com.crm.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定第一步的响应：密钥 + otpauth 链接 + 二维码（082，contracts/auth-mfa.md §1）。
 *
 * <p><b>{@code secret} 只在这里明文出现一次</b>：落库的是 AES-256-GCM 密文，此后任何接口都不回显它 （见 {@code
 * MfaSecretEncryptionService}）。它同时是"能生成这个账号动态码的种子"， 与恢复码一样属于持有凭证。
 *
 * <p>{@code enabled} 恒为 {@code false} 并且**是刻意带上的**：客户端的绑定向导要据此确认"这一步只生成、
 * 不启用"（FR-M04：校验通过前账号不得进入已启用状态）。响应不带它，前端就只能靠"没报错"来推断状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponse {

  /** Base32 密钥（无填充）。认证器 App 无法扫码时手工录入的就是它。 */
  private String secret;

  /** {@code otpauth://totp/...} 链接，可直接生成本页二维码或交给 App 解析。 */
  private String otpauthUrl;

  /** 二维码 PNG 的 data URL（与图形验证码同一形态：{@code data:image/png;base64,}）。 */
  private String qrCodeDataUrl;

  /** 恒为 {@code false}：本步骤只生成待绑定密钥，不改变启用状态。 */
  private boolean enabled;
}
