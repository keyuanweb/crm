package com.crm.dto.auth;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 2FA 状态（082，FR-M13，contracts/auth-mfa.md §4）：账户安全页要展示的三件事。
 *
 * <p><b>本响应不含密钥、也不含任何恢复码</b>——连哈希都不含。它是"看一眼"的接口， 泄不出任何能进门的东西。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaStatusResponse {

  private boolean enabled;

  /** 绑定时间；未启用时为 {@code null}（被全局 {@code non_null} 省略）。 */
  private LocalDateTime enabledAt;

  /** 剩余可用（未消费、未作废）的恢复码数量 —— 用户据此判断"该把这张纸换一张了"。 */
  private int recoveryCodesRemaining;
}
