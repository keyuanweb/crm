package com.crm.dto.auth;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重新生成恢复码的响应：**新一批**明文恢复码（082，contracts/auth-mfa.md §5）。
 *
 * <p>旧的一批在本响应产生之前已经全部作废（软删），所以这是用户手上唯一有效的一批。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaRecoveryCodesResponse {

  private List<String> recoveryCodes;
}
