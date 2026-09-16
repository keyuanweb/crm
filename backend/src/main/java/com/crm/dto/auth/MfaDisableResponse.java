package com.crm.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关闭 2FA 的响应（082，FR-M09，contracts/auth-mfa.md §6）：固定 {@code {"disabled": true}}。
 *
 * <p>没有失败态：本接口要么成功、要么以 {@code ApiResponse.fail} 带错误码返回 （口令错 {@code INVALID_CREDENTIALS}、第二个凭据错
 * {@code MFA_CODE_INVALID} / {@code RECOVERY_CODE_INVALID}）。所以这个布尔值永远是 {@code true}，
 * 它存在的理由是**契约里写了这个字段**，而不是它携带了信息—— 删掉它会让前端拿到的形状与契约不符，而前端已经按 {@code data.disabled} 写好了分支。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaDisableResponse {

  private boolean disabled;
}
