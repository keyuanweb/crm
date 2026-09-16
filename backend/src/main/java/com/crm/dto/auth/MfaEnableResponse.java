package com.crm.dto.auth;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定第二步的响应：启用状态、绑定时间与**一次性**恢复码明文（082，contracts/auth-mfa.md §2）。
 *
 * <p>恢复码明文只在本响应出现一次（FR-M05）：落库只有「独立盐 + SHA-256」。这与 {@code secret} 的区别是
 * 恢复码**不可再生**——用户没存下来就只能重新生成一批，而生成会把手里那张纸上的码全部作废。
 *
 * <p>{@code enabledAt} 是无时区的 {@link LocalDateTime}（{@code "2026-09-16T10:23:45"}，不带 {@code Z}）：
 * 仓内所有 DTO 的时间字段都是这个形态，不为一个字段单独引入 {@code OffsetDateTime}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaEnableResponse {

  private boolean enabled;

  private LocalDateTime enabledAt;

  private List<String> recoveryCodes;
}
