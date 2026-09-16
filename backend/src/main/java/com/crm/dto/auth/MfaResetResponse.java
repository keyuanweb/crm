package com.crm.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员重置 2FA 的响应（082，FR-M10，contracts/auth-mfa.md §7）。
 *
 * <p>固定为 {@code {"reset": true}}：本接口**幂等**——对一个本来就没启用 2FA 的用户调用它同样成功。 契约给出的错误只有 403（无权限）与
 * 404（用户不存在），没有"他没开 2FA"这一项： 管理员的意图是"确保这个人现在是单因素"，而"本来就没开"已经满足了这个意图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaResetResponse {

  private boolean reset;
}
