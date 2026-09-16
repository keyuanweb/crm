package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.TotpGenerator;
import com.crm.dto.auth.MfaDisableRequest;
import com.crm.dto.auth.MfaEnableResponse;
import com.crm.dto.auth.MfaSetupResponse;
import com.crm.entity.AuditLog;
import com.crm.entity.User;
import com.crm.repository.AuditLogMapper;
import com.crm.repository.UserMapper;
import com.crm.service.MfaSecretEncryptionService;
import com.crm.service.MfaService;
import com.crm.service.RecoveryCodeService;
import com.crm.support.FixedClockTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 2FA 绑定生命周期在<b>真实 H2</b> 上走一遍（082 第 8 步）：setup → enable → status → regenerate → disable → 管理员重置。
 *
 * <p><b>本类管的是"语句真的落到库里了吗"</b>，那是纯 Mockito 用例结构上问不到的： {@code @TableLogic} 的软删、{@code used} 列在 H2 里是
 * BOOLEAN、{@code .set(col, null)} 生成的 {@code SET col = NULL} 到底写没写进去。
 *
 * <p><b>最重要的一条断言是"禁用后密钥列真的是 NULL"</b>（{@code secretColumnIsCleared}）。 它必须<b>直接重读那一列</b>，而不是断言"再登录时要
 * 2FA"——后者在密钥仍留在库里时同样成立， 而残留的密钥一旦泄漏，攻击者算出的是用户<b>将来重新绑定</b>时会用到的那把（用户通常复用同一个 App
 * 条目）。这条性质没有任何行为层信号，只有数据层能看见。
 *
 * <p><b>为什么用 {@link FixedClockTestSupport} 而不是真时钟</b>：动态码由时间步决定，而"算码"与"验码" 之间若跨过 30
 * 秒边界，用例会以极低概率随机红。冻结时钟把这条 flake 直接消掉， 代价是本类自成一个 Spring 上下文（见 {@code FixedClockTestSupport}
 * 的"代价如实记"）。 本类<b>不需要</b> Redis：注册生命周期一次都不碰 {@code MfaStateStore}（那是登录路径的事，第 9 步）。
 *
 * <h2>本类还要守 {@code version} 那一列（085 的回归）</h2>
 *
 * <p>{@link com.crm.entity.User} 带 {@code @Version}。本服务的每一次写入都必须是<b>定向</b>的 （{@code
 * LambdaUpdateWrapper} 且实体传 {@code null}）；一旦有人把它改回 {@code updateById(entity)}， MyBatis-Plus
 * 的乐观锁插件就会生成 {@code SET version = version + 1 WHERE id = ? AND version = ?} —— 于是"用户启用
 * 2FA"这个操作会消费掉管理端对该用户的并发编辑令牌，让管理员下次编辑这个用户时收到 409， 而失败原因指着一个与 2FA 无关的地方。
 *
 * <p>{@code MfaServiceTest} 用调用形状断言钉住这件事（{@code verify(userMapper, never()).updateById(any())}），
 * 但调用形状看不见"真的写进去了什么"；本类在 {@code fullLifecycle} 与 {@code adminResetClearsEverythingAndIsAudited}
 * 里各读一次 {@code version} 比前后值 —— 两次观测都覆盖了那条性质，且各自是独立的证据层。
 */
class MfaLifecycleIT extends FixedClockTestSupport {

  @Autowired private MfaService mfaService;
  @Autowired private MfaSecretEncryptionService secrets;
  @Autowired private RecoveryCodeService recoveryCodes;
  @Autowired private UserMapper userMapper;
  @Autowired private AuditLogMapper auditLogMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("全流程：setup 不解锁启用态，enable 解锁并下发恢复码，disable 把三列清空")
  void fullLifecycle() {
    long userId = createUser("mfa_flow");
    int versionBefore = userMapper.selectById(userId).getVersion();

    MfaSetupResponse setup = mfaService.setup(userId, null);
    // FR-M04：这一步只生成。若 enabled 为 true，用户扫的是哪个账号的码就已经不重要了 —— 账号已经受保护。
    assertThat(setup.isEnabled()).isFalse();
    assertThat(setup.getSecret()).matches("[A-Z2-7]{16,}");
    assertThat(setup.getOtpauthUrl()).startsWith("otpauth://totp/");
    assertThat(setup.getQrCodeDataUrl()).startsWith("data:image/png;base64,");
    assertThat(userMapper.selectById(userId).getTwoFactorEnabled()).isFalse();
    assertThat(userMapper.selectById(userId).getTotpSecretEncrypted()).isNotNull();

    LocalDateTime expectedEnabledAt = LocalDateTime.now(clock);
    MfaEnableResponse enable = mfaService.enable(userId, codeFor(setup.getSecret()));
    assertThat(enable.isEnabled()).isTrue();
    assertThat(enable.getEnabledAt()).isEqualTo(expectedEnabledAt);
    assertThat(enable.getRecoveryCodes()).hasSize(10);

    User afterEnable = userMapper.selectById(userId);
    assertThat(afterEnable.getTwoFactorEnabled()).isTrue();
    assertThat(afterEnable.getTwoFactorEnabledAt()).isEqualTo(expectedEnabledAt);
    assertThat(mfaService.status(userId).isEnabled()).isTrue();
    assertThat(mfaService.status(userId).getRecoveryCodesRemaining()).isEqualTo(10);

    mfaService.disable(userId, disableRequest(codeFor(setup.getSecret()), null));

    assertSecretColumnIsCleared(userId);
    assertThat(mfaService.status(userId).isEnabled()).isFalse();
    assertThat(mfaService.status(userId).getRecoveryCodesRemaining()).isZero();
    // 上面三次写入（setup / enable / disable）都不许自增 version —— 理由见类 javadoc 的"version 那一列"。
    assertThat(userMapper.selectById(userId).getVersion()).isEqualTo(versionBefore);
  }

  @Test
  @DisplayName("库里存的是密文：不含明文密钥，形如 b64:b64，且能解回原值")
  void storedSecretIsCiphertextThatDecryptsBack() {
    long userId = createUser("mfa_cipher");
    MfaSetupResponse setup = mfaService.setup(userId, null);

    String stored = userMapper.selectById(userId).getTotpSecretEncrypted();
    assertThat(stored).doesNotContain(setup.getSecret());
    assertThat(stored).matches("[A-Za-z0-9+/]+={0,2}:[A-Za-z0-9+/]+={0,2}");
    assertThat(secrets.decrypt(stored)).isEqualTo(setup.getSecret());
  }

  @Test
  @DisplayName("重复 setup 换掉未启用的密钥：只有最后那一把能通过 enable")
  void repeatedSetupReplacesThePendingSecret() {
    long userId = createUser("mfa_resetup");
    String first = mfaService.setup(userId, null).getSecret();
    String second = mfaService.setup(userId, null).getSecret();

    assertThat(second).isNotEqualTo(first);
    assertThatThrownBy(() -> mfaService.enable(userId, codeFor(first)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_CODE_INVALID);
    assertThat(mfaService.enable(userId, codeFor(second)).isEnabled()).isTrue();
  }

  @Test
  @DisplayName("启用后不能再 setup（MFA_ALREADY_ENABLED），否则重扫一次就能换掉在用密钥")
  void setupIsRejectedOnceEnabled() {
    long userId = createUser("mfa_enabled_setup");
    String secret = mfaService.setup(userId, null).getSecret();
    mfaService.enable(userId, codeFor(secret));

    assertThatThrownBy(() -> mfaService.setup(userId, null))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_ALREADY_ENABLED);
    assertThat(secrets.decrypt(userMapper.selectById(userId).getTotpSecretEncrypted()))
        .isEqualTo(secret);
  }

  @Test
  @DisplayName("恢复码：可用它关闭 2FA；用过的那个码此后是死的，而新一批的码仍然有效")
  void recoveryCodeDisablesAndIsConsumedOnce() {
    long userId = createUser("mfa_recovery");
    String secret = mfaService.setup(userId, null).getSecret();
    List<String> codes = mfaService.enable(userId, codeFor(secret)).getRecoveryCodes();

    mfaService.disable(userId, disableRequest(null, codes.get(0)));
    assertSecretColumnIsCleared(userId);
    assertThat(recoveryCodes.countRemaining(userId)).isZero();

    // 重新绑定：用过的那个旧码必须是死的 —— 否则它等于一张永不过期的备用钥匙。
    String secret2 = mfaService.setup(userId, null).getSecret();
    List<String> codes2 = mfaService.enable(userId, codeFor(secret2)).getRecoveryCodes();
    assertThatThrownBy(() -> mfaService.disable(userId, disableRequest(null, codes.get(0))))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.RECOVERY_CODE_INVALID);
    // 而新一批的码仍然有效：一次失败不该把逃生通道一起关上。
    mfaService.disable(userId, disableRequest(null, codes2.get(0)));
    assertSecretColumnIsCleared(userId);
  }

  @Test
  @DisplayName("关闭之后可以重新绑定并再次启用（逃生通道是通的）")
  void canReEnableAfterDisabling() {
    long userId = createUser("mfa_rebind");
    String first = mfaService.setup(userId, null).getSecret();
    mfaService.enable(userId, codeFor(first));
    mfaService.disable(userId, disableRequest(codeFor(first), null));

    String second = mfaService.setup(userId, null).getSecret();
    mfaService.enable(userId, codeFor(second));

    assertThat(mfaService.status(userId).isEnabled()).isTrue();
    assertThat(secrets.decrypt(userMapper.selectById(userId).getTotpSecretEncrypted()))
        .isEqualTo(second);
  }

  @Test
  @DisplayName("管理员重置：清密钥与恢复码、幂等，且写一条 MFA_RESET 审计")
  void adminResetClearsEverythingAndIsAudited() {
    long userId = createUser("mfa_admin_reset");
    int versionBefore = userMapper.selectById(userId).getVersion();
    String secret = mfaService.setup(userId, null).getSecret();
    mfaService.enable(userId, codeFor(secret));
    assertThat(auditCount(userId, "MFA_ENABLE")).isEqualTo(1);

    mfaService.resetByAdmin(userId);
    assertSecretColumnIsCleared(userId);
    // 管理员重置是全仓最容易写成 updateById 的一处（它读了一个 User 对象、手里正好有一个实体）。
    assertThat(userMapper.selectById(userId).getVersion()).isEqualTo(versionBefore);
    assertThat(recoveryCodes.countRemaining(userId)).isZero();
    assertThat(auditCount(userId, "MFA_RESET")).isEqualTo(1);

    // 幂等：本来就没开也成功（契约 §7 的错误只有 403/404）。
    mfaService.resetByAdmin(userId);
    assertThat(auditCount(userId, "MFA_RESET")).isEqualTo(2);
  }

  @Test
  @DisplayName("管理员重置不影响密码：重置后该用户仍能用原口令通过口令校验")
  void adminResetDoesNotTouchThePassword() {
    long userId = createUser("mfa_reset_keeps_password");
    String secret = mfaService.setup(userId, null).getSecret();
    mfaService.enable(userId, codeFor(secret));

    mfaService.resetByAdmin(userId);

    assertThat(passwordEncoder.matches("pass1234", userMapper.selectById(userId).getPasswordHash()))
        .isTrue();
    assertThat(userMapper.selectById(userId).getEnabled()).isTrue();
  }

  @Test
  @DisplayName("关闭 2FA 也写 MFA_DISABLE 审计，且实体 id 指向被操作的那个用户")
  void disableIsAuditedAgainstTheUser() {
    long userId = createUser("mfa_disable_audit");
    String secret = mfaService.setup(userId, null).getSecret();
    mfaService.enable(userId, codeFor(secret));

    mfaService.disable(userId, disableRequest(codeFor(secret), null));

    assertThat(auditCount(userId, "MFA_DISABLE")).isEqualTo(1);
  }

  // ===== 辅助 =====

  /**
   * 断言"密钥列真的是 NULL"——重读那一列，而不是断言某个行为。
   *
   * <p>写成 {@code assertThat(userMapper.selectById(id).getTwoFactorEnabled()).isFalse()} 是不够的：
   * 它只说明"没启用"，而一个"没启用但密钥还留着"的账号同样满足它。
   */
  private void assertSecretColumnIsCleared(long userId) {
    User user = userMapper.selectById(userId);
    assertThat(user.getTotpSecretEncrypted()).isNull();
    assertThat(user.getTwoFactorEnabled()).isFalse();
    assertThat(user.getTwoFactorEnabledAt()).isNull();
  }

  /** 用被冻结的时钟算当前时间步的动态码 —— 与 {@code TotpService} 校验时用的是同一个时钟。 */
  private String codeFor(String base32Secret) {
    long step = TotpGenerator.timeStepOf(clock.instant().getEpochSecond(), 30);
    return TotpGenerator.codeAt(TotpGenerator.decodeSecret(base32Secret), step, 6);
  }

  private long auditCount(long entityId, String action) {
    return auditLogMapper.selectCount(
        new LambdaQueryWrapper<AuditLog>()
            .eq(AuditLog::getEntityId, entityId)
            .eq(AuditLog::getAction, action));
  }

  private static MfaDisableRequest disableRequest(String code, String recoveryCode) {
    MfaDisableRequest request = new MfaDisableRequest();
    request.setPassword("pass1234");
    request.setCode(code);
    request.setRecoveryCode(recoveryCode);
    return request;
  }

  private long createUser(String username) {
    User user = new User();
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode("pass1234"));
    user.setDisplayName(username);
    user.setRole("SUPPORT");
    user.setEnabled(true);
    user.setTokenVersion(0);
    userMapper.insert(user);
    return user.getId();
  }
}
