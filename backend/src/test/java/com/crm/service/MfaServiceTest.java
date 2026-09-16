package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.MfaDisableRequest;
import com.crm.dto.auth.MfaEnableResponse;
import com.crm.dto.auth.MfaSetupResponse;
import com.crm.dto.auth.MfaStatusResponse;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link MfaService} 的<b>调用形状</b>用例（082 第 8 步）。
 *
 * <p><b>本类存在的第一理由：085 的回归拦在这里。</b> {@code User} 带 {@code @Version}，只要有一处 2FA 的用户列写入走 {@code
 * updateById(实体)}，MyBatis-Plus 的乐观锁插件就会生成 {@code SET version = version + 1} —— 于是"用户自己启用
 * 2FA"会消费掉管理端对该用户的并发编辑令牌，管理员下次编辑<b>必然</b>收到 409。 这条因果链在行为层几乎不可见（要两个会话真的撞在一起），所以只能钉调用形状：下面每个写入路径都断言
 * {@code updateById} <b>从未被调用</b>、且 {@code update} 的实体参数是 {@code null}。
 *
 * <p><b>第二理由：审计动作名。</b> FR-M12 列举的动作名是契约的一部分，改一个字母不会有任何编译或行为信号 （审计列表只是少一行、多一行），故逐个钉死。
 *
 * <p><b>第三理由：定向更新到底写了哪几列。</b> {@code disable} 的三列里若漏掉 {@code totp_secret_encrypted}，
 * 行为层完全看不出来（启用标记已置 false，谁都进不去 2FA 分支），但库里留着的那把密钥是下一次绑定的种子。 {@code MfaLifecycleIT}
 * 从<b>数据</b>侧证明这一点，这里从<b>语句</b>侧证明。
 */
class MfaServiceTest {

  private static final long USER_ID = 42L;

  private static final String USERNAME = "alice";

  private static final String PASSWORD = "pass1234";

  private static final String SECRET = "JBSWY3DPEHPK3PXP";

  private static final String ENCRYPTED = "ZW5jcnlwdGVk:Y2lwaGVy";

  private static final String VALID_CODE = "123456";

  private static final String ISSUER = "CRM";

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC);

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 10, 0, 0);

  private UserMapper userMapper;
  private PasswordEncoder passwordEncoder;
  private TotpService totpService;
  private MfaSecretEncryptionService secrets;
  private MfaQrCodeService qrCodes;
  private RecoveryCodeService recoveryCodes;
  private AuditService auditService;
  private MfaService service;

  /**
   * 见 {@code RecoveryCodeServiceTest#initTableInfo}：{@code LambdaUpdateWrapper.set(User::getX,
   * ...)} 要查 {@code TableInfo}，而纯 Mockito 用例里没有 mapper 扫描那一步。
   */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  void setUp() {
    userMapper = mock(UserMapper.class);
    passwordEncoder = mock(PasswordEncoder.class);
    totpService = mock(TotpService.class);
    secrets = mock(MfaSecretEncryptionService.class);
    qrCodes = mock(MfaQrCodeService.class);
    recoveryCodes = mock(RecoveryCodeService.class);
    auditService = mock(AuditService.class);
    service =
        new MfaService(
            userMapper,
            passwordEncoder,
            totpService,
            secrets,
            qrCodes,
            recoveryCodes,
            auditService,
            CLOCK,
            ISSUER);
  }

  // ===== setup =====

  @Test
  @DisplayName("setup：密钥以**定向更新**写入且只写一列，返回值里的 enabled 恒为 false")
  void setupWritesOnlyTheSecretColumnAndStaysDisabled() {
    User user = user(false, null);
    when(userMapper.selectById(USER_ID)).thenReturn(user);
    when(totpService.generateSecret()).thenReturn(SECRET);
    when(totpService.timeStepSeconds()).thenReturn(30);
    when(secrets.encrypt(SECRET)).thenReturn(ENCRYPTED);
    when(qrCodes.otpauthUrl(anyString(), anyString(), anyString(), anyInt()))
        .thenReturn("otpauth://totp/x");
    when(qrCodes.pngDataUrl(anyString())).thenReturn("data:image/png;base64,AAAA");

    MfaSetupResponse response = service.setup(USER_ID, null);

    assertThat(response.getSecret()).isEqualTo(SECRET);
    assertThat(response.getOtpauthUrl()).isEqualTo("otpauth://totp/x");
    assertThat(response.getQrCodeDataUrl()).isEqualTo("data:image/png;base64,AAAA");
    // FR-M04：这一步只生成、不启用。写成 true 就等于"扫码即生效"，而用户可能扫的是别人的码。
    assertThat(response.isEnabled()).isFalse();

    LambdaUpdateWrapper<User> update = capturedUpdate();
    String setClause = update.getSqlSet();
    Collection<Object> params = update.getParamNameValuePairs().values();
    assertThat(setClause).contains("totp_secret_encrypted");
    // 只写一列：本步不得顺手把 two_factor_enabled 也带上（那会让"只生成"变成"就地启用"）。
    assertThat(setClause).doesNotContain("two_factor_enabled");
    assertThat(params).contains(ENCRYPTED);
    assertThat(params).doesNotContain(Boolean.TRUE);
    // WHERE 那一半：定向更新必须按主键定位。没有它，这一句就是"把所有人的密钥改成同一把"。
    assertThat(update.getSqlSegment()).contains("id");
    // 085：实体非空 ⇒ 乐观锁自增 version ⇒ 管理员下次编辑该用户必 409。
    verify(userMapper, never()).updateById(any());
  }

  @Test
  @DisplayName("setup：二维码里的账号取**用户名**，且与校验侧的时间步长一致")
  void setupBuildsTheOtpauthUrlFromTheUsername() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, null));
    when(totpService.generateSecret()).thenReturn(SECRET);
    when(totpService.timeStepSeconds()).thenReturn(30);
    when(secrets.encrypt(SECRET)).thenReturn(ENCRYPTED);
    when(qrCodes.pngDataUrl(anyString())).thenReturn("data:image/png;base64,AAAA");

    service.setup(USER_ID, null);

    // accountName 用用户名而不是显示名：显示名可改、可重复，而认证器 App 里的条目是对着"哪个账号"配的。
    verify(qrCodes).otpauthUrl(ISSUER, USERNAME, SECRET, 30);
  }

  @Test
  @DisplayName("setup：传了口令就必须对；不传则跳过（契约允许空 body，见 quickstart §b）")
  void setupChecksThePasswordOnlyWhenProvided() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, null));
    when(totpService.generateSecret()).thenReturn(SECRET);
    when(totpService.timeStepSeconds()).thenReturn(30);
    when(secrets.encrypt(SECRET)).thenReturn(ENCRYPTED);
    when(qrCodes.otpauthUrl(anyString(), anyString(), anyString(), anyInt()))
        .thenReturn("otpauth://totp/x");
    when(qrCodes.pngDataUrl(anyString())).thenReturn("data:image/png;base64,AAAA");
    when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);

    service.setup(USER_ID, PASSWORD);
    verify(passwordEncoder).matches(PASSWORD, "hash");

    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
    assertThatThrownBy(() -> service.setup(USER_ID, "wrong"))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("setup：已启用的账号不再发新密钥（否则重扫一次会把在用密钥换掉）")
  void setupIsRejectedForAnAlreadyEnabledAccount() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(true, ENCRYPTED));

    assertThatThrownBy(() -> service.setup(USER_ID, null))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_ALREADY_ENABLED);
    verify(userMapper, never()).update(any(), any());
  }

  // ===== enable =====

  @Test
  @DisplayName("enable：定向更新启用标记与时间，签发恢复码，并写 MFA_ENABLE 审计")
  void enableFlipsTheFlagWithADirectedUpdate() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, ENCRYPTED));
    when(secrets.decrypt(ENCRYPTED)).thenReturn(SECRET);
    when(totpService.matchTimeStep(SECRET, VALID_CODE)).thenReturn(OptionalLong.of(1000L));
    when(recoveryCodes.regenerate(USER_ID)).thenReturn(List.of("AAAA1111", "BBBB2222"));

    MfaEnableResponse response = service.enable(USER_ID, VALID_CODE);

    assertThat(response.isEnabled()).isTrue();
    assertThat(response.getEnabledAt()).isEqualTo(NOW);
    assertThat(response.getRecoveryCodes()).containsExactly("AAAA1111", "BBBB2222");

    LambdaUpdateWrapper<User> update = capturedUpdate();
    String setClause = update.getSqlSet();
    Collection<Object> params = update.getParamNameValuePairs().values();
    assertThat(setClause).contains("two_factor_enabled").contains("two_factor_enabled_at");
    assertThat(params).contains(Boolean.TRUE).contains(NOW);
    // 绑定阶段不得清掉刚写进去的密钥：清了 enable 就永远不可能成功。
    assertThat(setClause).doesNotContain("totp_secret_encrypted");
    verify(userMapper, never()).updateById(any());
    verify(auditService).record(eq("MFA_ENABLE"), eq("USER"), eq(USER_ID), anyString());
  }

  @Test
  @DisplayName("enable：没有待绑定密钥时是 MFA_NOT_ENABLED（先 setup 再 enable 是契约顺序）")
  void enableRequiresAPendingSecret() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, null));

    assertThatThrownBy(() -> service.enable(USER_ID, VALID_CODE))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_NOT_ENABLED);
    verify(userMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("enable：码不对 ⇒ MFA_CODE_INVALID，且不启用、不发恢复码、不写审计")
  void enableWithAWrongCodeTouchesNothing() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, ENCRYPTED));
    when(secrets.decrypt(ENCRYPTED)).thenReturn(SECRET);
    when(totpService.matchTimeStep(SECRET, VALID_CODE)).thenReturn(OptionalLong.empty());

    assertThatThrownBy(() -> service.enable(USER_ID, VALID_CODE))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_CODE_INVALID);
    // 半个启用态是最危险的中间态：标记已开、密钥没有可用凭据，用户被锁在门外而恢复码还是空的。
    verify(userMapper, never()).update(any(), any());
    verifyNoInteractions(recoveryCodes);
    verifyNoInteractions(auditService);
  }

  // ===== status =====

  @Test
  @DisplayName("status：未启用时不查恢复码表，返回 enabled=false 与 0")
  void statusOfADisabledAccountDoesNotQueryRecoveryCodes() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, null));

    MfaStatusResponse status = service.status(USER_ID);

    assertThat(status.isEnabled()).isFalse();
    assertThat(status.getEnabledAt()).isNull();
    assertThat(status.getRecoveryCodesRemaining()).isZero();
    verifyNoInteractions(recoveryCodes);
  }

  @Test
  @DisplayName("status：已启用时返回绑定时间与**剩余**恢复码数量")
  void statusOfAnEnabledAccountReportsRemainingCodes() {
    User user = user(true, ENCRYPTED);
    user.setTwoFactorEnabledAt(NOW);
    when(userMapper.selectById(USER_ID)).thenReturn(user);
    when(recoveryCodes.countRemaining(USER_ID)).thenReturn(7);

    MfaStatusResponse status = service.status(USER_ID);

    assertThat(status.isEnabled()).isTrue();
    assertThat(status.getEnabledAt()).isEqualTo(NOW);
    assertThat(status.getRecoveryCodesRemaining()).isEqualTo(7);
  }

  // ===== regenerateRecoveryCodes =====

  @Test
  @DisplayName("重新生成恢复码：口令必填，且旧批整体作废（走的是同一条 regenerate）")
  void regenerateRequiresThePassword() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(true, ENCRYPTED));
    when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
    when(recoveryCodes.regenerate(USER_ID)).thenReturn(List.of("NEW11111"));

    assertThat(service.regenerateRecoveryCodes(USER_ID, PASSWORD).getRecoveryCodes())
        .containsExactly("NEW11111");
    verify(passwordEncoder).matches(PASSWORD, "hash");

    assertThatThrownBy(() -> service.regenerateRecoveryCodes(USER_ID, null))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("重新生成恢复码：未启用的账号不适用（那段逃生通道还不存在）")
  void regenerateIsRejectedWhenNotEnabled() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, null));

    assertThatThrownBy(() -> service.regenerateRecoveryCodes(USER_ID, PASSWORD))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_NOT_ENABLED);
    verifyNoInteractions(recoveryCodes);
  }

  // ===== disable =====

  @Test
  @DisplayName("disable（动态码）：三列一起清成 NULL，作废全部恢复码，写 MFA_DISABLE 审计")
  void disableClearsAllThreeColumns() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(true, ENCRYPTED));
    when(secrets.decrypt(ENCRYPTED)).thenReturn(SECRET);
    when(totpService.matchTimeStep(SECRET, VALID_CODE)).thenReturn(OptionalLong.of(1000L));
    when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);

    service.disable(USER_ID, disableRequest(VALID_CODE, null));

    LambdaUpdateWrapper<User> update = capturedUpdate();
    String setClause = update.getSqlSet();
    Collection<Object> params = update.getParamNameValuePairs().values();
    assertThat(setClause)
        .contains("two_factor_enabled")
        .contains("totp_secret_encrypted")
        .contains("two_factor_enabled_at");
    assertThat(params).contains(Boolean.FALSE);
    // 漏掉这一列不会有任何行为信号（启用标记已 false，谁都进不去 2FA 分支），
    // 但库里留着的密钥是"下次绑定"的种子 —— 一旦泄漏，攻击者算出的是用户将来会绑的那把。
    assertThat(params).contains((Object) null);
    verify(userMapper, never()).updateById(any());
    verify(recoveryCodes).revokeAll(USER_ID);
    verify(auditService).record(eq("MFA_DISABLE"), eq("USER"), eq(USER_ID), anyString());
  }

  @Test
  @DisplayName("disable（恢复码）：走一次性消费；消费失败即 RECOVERY_CODE_INVALID 且什么也不改")
  void disableWithARecoveryCodeConsumesIt() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(true, ENCRYPTED));
    when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
    when(recoveryCodes.consume(USER_ID, "AAAA1111")).thenReturn(true);

    service.disable(USER_ID, disableRequest(null, "AAAA1111"));

    verify(recoveryCodes).consume(USER_ID, "AAAA1111");
    // 用恢复码关闭时不该再去算动态码：那条分支的密钥可能已经不在用户手里了。
    verifyNoInteractions(totpService);
    verify(userMapper).update(isNull(), any());
  }

  @Test
  @DisplayName("disable：两个凭据都缺 ⇒ MFA_CODE_INVALID（不是 500、不是静默成功）")
  void disableRequiresASecondFactor() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(true, ENCRYPTED));
    when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);

    assertThatThrownBy(() -> service.disable(USER_ID, disableRequest("  ", null)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_CODE_INVALID);
    verify(userMapper, never()).update(any(), any());
    verifyNoInteractions(recoveryCodes);
  }

  @Test
  @DisplayName("disable：口令错 ⇒ 停在这一步，第二个凭据再对也不往下走")
  void disableStopsAtThePassword() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(true, ENCRYPTED));
    when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(false);

    assertThatThrownBy(() -> service.disable(USER_ID, disableRequest(VALID_CODE, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    verifyNoInteractions(totpService);
    verifyNoInteractions(recoveryCodes);
    verify(userMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("disable：未启用的账号不适用（MFA_NOT_ENABLED），不会先查口令")
  void disableIsRejectedWhenNotEnabled() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, null));

    assertThatThrownBy(() -> service.disable(USER_ID, disableRequest(VALID_CODE, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.MFA_NOT_ENABLED);
    verifyNoInteractions(passwordEncoder);
  }

  // ===== resetByAdmin =====

  @Test
  @DisplayName("管理员重置：幂等（本来就没开也成功），清三列、作废恢复码、写 MFA_RESET 审计")
  void adminResetIsIdempotentAndAudited() {
    when(userMapper.selectById(USER_ID)).thenReturn(user(false, null));

    service.resetByAdmin(USER_ID);

    verify(recoveryCodes).revokeAll(USER_ID);
    verify(auditService).record(eq("MFA_RESET"), eq("USER"), eq(USER_ID), anyString());
    verify(userMapper, never()).updateById(any());
    // 重置只清 2FA，**不动密码阶段**：它不能被理解为"给他开一扇门"。
    verifyNoInteractions(passwordEncoder);
    verifyNoInteractions(totpService);
  }

  @Test
  @DisplayName("管理员重置：目标用户不存在 ⇒ USER_NOT_FOUND，且不留审计行")
  void adminResetOnAMissingUserIs404() {
    when(userMapper.selectById(USER_ID)).thenReturn(null);

    assertThatThrownBy(() -> service.resetByAdmin(USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
    verify(userMapper, never()).update(any(), any());
    verifyNoInteractions(auditService);
  }

  // ===== 辅助 =====

  private User user(boolean enabled, String encryptedSecret) {
    User user = new User();
    user.setId(USER_ID);
    user.setUsername(USERNAME);
    user.setPasswordHash("hash");
    user.setEnabled(true);
    user.setTwoFactorEnabled(enabled);
    user.setTotpSecretEncrypted(encryptedSecret);
    return user;
  }

  private static MfaDisableRequest disableRequest(String code, String recoveryCode) {
    MfaDisableRequest request = new MfaDisableRequest();
    request.setPassword(PASSWORD);
    request.setCode(code);
    request.setRecoveryCode(recoveryCode);
    return request;
  }

  /**
   * 取出 {@code update(null, wrapper)} 的 wrapper —— 并**先把两段都求值**，再让调用方读列名与参数表。
   *
   * <p><b>{@code getSqlSegment()} 只给 WHERE，SET 在 {@code getSqlSet()} 里</b>（实测得来：最初三处断言 {@code
   * getSqlSegment()} 含 {@code totp_secret_encrypted} 都失败了，实际返回的只有 {@code (id = ?)}
   * 那一段）。要断言"改了哪几列"就必须读 {@code getSqlSet()}。
   *
   * <p>两段还都是**惰性**的：{@code .set(...)}／{@code .eq(...)} 只是往 MergeSegments 里挂了未求值的段，
   * 列名解析与参数落表发生在求值那一刻（仓库先例 {@code WebhookDelivererTest:194-197}）。所以下面每一条 {@code contains}
   * 断言之前都必须先触发求值 —— 否则断言读的是空表，<b>永远不可能失败</b>。
   *
   * <p>必须是 {@link LambdaUpdateWrapper} 这个具体类型：这三个方法都定义在 {@code AbstractWrapper}／ {@code Update}
   * 上，{@code Wrapper} 顶层不暴露。
   */
  @SuppressWarnings("unchecked")
  private LambdaUpdateWrapper<User> capturedUpdate() {
    ArgumentCaptor<LambdaUpdateWrapper<User>> captor =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(userMapper).update(isNull(), captor.capture());
    LambdaUpdateWrapper<User> wrapper = captor.getValue();
    wrapper.getSqlSet();
    wrapper.getSqlSegment();
    return wrapper;
  }
}
