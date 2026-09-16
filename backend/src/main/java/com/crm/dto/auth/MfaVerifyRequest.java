package com.crm.dto.auth;

import lombok.Data;

/**
 * 二次验证的请求（082，FR-M07/FR-M08，contracts/auth-mfa.md §3）：一次性票据 + （动态码或恢复码）。
 *
 * <p><b>本类刻意一个校验注解都没有</b>，与同目录的 {@link MfaDisableRequest} 不同。契约给本端点 列出的错误只有 401/429
 * 三类（票据无效、码无效、锁定），其中"请求体不成形"与"票据不存在" 对调用方是同一件事——都得重新走一次密码登录。若在这里挂 {@code @NotBlank}， 一个漏填 {@code
 * mfaToken} 的请求会得到 400 加一串字段级报错，而契约里根本没有 400 这一支。 同理，{@code code}/{@code recoveryCode}
 * 都为空时不是"参数校验失败"，而是"这次验证不能完成"（401）——那条判定归 {@code MfaVerificationService}， 与 {@code
 * MfaService.requireSecondFactor} 保持同一套语义。
 *
 * <p><b>{@code mfaToken} 不是 JWT</b>：它是密码阶段签发、Redis 持有的一次性不透明随机串 （见 {@code MfaStateStore} 的类
 * javadoc）。本类只是它的载体，不做任何解析。
 */
@Data
public class MfaVerifyRequest {

  /** 密码阶段拿到的一次性票据。缺失/过期/已消费一律 401 {@code MFA_TICKET_INVALID}。 */
  private String mfaToken;

  /** 动态码（6 位十进制）；与 {@link #recoveryCode} 二者提供其一。同时提供时恢复码优先。 */
  private String code;

  /** 恢复码（8 位无歧义字符）；与 {@link #code} 二者提供其一。 */
  private String recoveryCode;
}
