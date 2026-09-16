package com.crm.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重新生成恢复码的请求（082，contracts/auth-mfa.md §5）。
 *
 * <p>与 {@link MfaSetupRequest} 不同，这里的口令是**必填**的：重新生成会把用户手里那张纸上的码 全部作废——若允许只凭一个已登录的会话就做，那么任何一次 XSS /
 * 会话窃取都能让合法用户 在真正的急用时刻（认证器丢了）发现自己的恢复码全是废纸。生成密钥那一步没有这个后果， 故那一步的口令可选。
 */
@Data
public class MfaRegenerateRequest {

  @NotBlank(message = "密码不能为空")
  private String password;
}
