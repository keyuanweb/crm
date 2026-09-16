package com.crm.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 关闭 2FA 的请求（082，FR-M09，contracts/auth-mfa.md §6）：口令 + （动态码或恢复码）。
 *
 * <p><b>"口令 + 第二个凭据"两者都要</b>，而不是"有会话就行"：关闭 2FA 等于把这把锁卸掉， 而本接口正是"人已经进来了、想把第二因素撤掉"的那一步——只验会话的话，
 * 一个被窃取的会话（或一次借用的电脑）就能永久降级账号安全，且用户**毫无察觉**。
 *
 * <p>为什么允许用恢复码关闭：认证器丢了的人也必须能关掉重绑，否则他会卡在 "验证码进不来 > 无法关闭 > 无法重绑"的环里，唯一的出路就只剩管理员重置（FR-M10）。
 */
@Data
public class MfaDisableRequest {

  @NotBlank(message = "密码不能为空")
  private String password;

  /** 动态码；与 {@link #recoveryCode} 二者提供其一即可。 */
  private String code;

  /** 恢复码；与 {@link #code} 二者提供其一即可。 */
  private String recoveryCode;
}
