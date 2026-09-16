package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.MfaDisableRequest;
import com.crm.dto.auth.MfaEnableResponse;
import com.crm.dto.auth.MfaRecoveryCodesResponse;
import com.crm.dto.auth.MfaSetupResponse;
import com.crm.dto.auth.MfaStatusResponse;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2FA 的注册生命周期：绑定、启用、状态、重新生成恢复码、关闭、管理员重置 （082，FR-M04~FR-M06、FR-M09~FR-M13；contracts/auth-mfa.md
 * §1/§2/§4/§5/§6/§7）。
 *
 * <p><b>登录时的二次验证不在这里</b>（{@code MfaVerificationService}，第 9 步）：那条路径的
 * 每一行都关乎"能不能进来"，与这里"改账号的安全配置"是两套错误处理与两套顺序要求。
 *
 * <h2>所有 {@code user} 列的写入都是定向的，绝不 {@code updateById}</h2>
 *
 * <p>本类每一处写 {@code user} 表都用 {@link LambdaUpdateWrapper} 且实体传 {@code null}。 <b>这不是风格问题</b>：{@code
 * User} 带 {@code @Version}，实体非空时会触发 MyBatis-Plus 的乐观锁 插件，生成 {@code SET version = version + 1 WHERE
 * id = ? AND version = ?} —— 于是"启用 2FA" 这个用户自己的操作，会消费掉管理端对该用户的并发编辑令牌，让管理员下次编辑必然收到 409
 * VERSION_CONFLICT（085 的回归，症状指向一个不存在的原因）。附带收益：不再是整行回写， 不会把"读后到写前"的他人改动静默覆盖回去。
 *
 * <p>{@code updated_at} 不因定向更新而陈旧：它在 {@code V1__init.sql} 里定义为 {@code ON UPDATE
 * CURRENT_TIMESTAMP}，由数据库维护。
 *
 * <h2>审计</h2>
 *
 * <p>FR-M12 列举的四类事件里，本类负责<b>启用、关闭、重置</b>三类（"校验失败"在登录路径上， 用 {@code recordAsSystem}
 * 写——那条路径没有登录主体）。审计走 {@code record(...)}：这三条路径 都在已认证的请求里，操作人就是当前用户（管理员重置时是那个管理员）。
 * <b>「重新生成恢复码」刻意不单独记一条动作</b>：FR-M12 的动作清单里没有它， 而新增一个只在本批存在的动作名会让审计动作集与规格对不上。
 */
@Service
public class MfaService {

  private static final String ENTITY_USER = "USER";

  /** FR-M12 的动作名。仓内既有动作是 {@code RESET_PASSWORD} 这种"动词_名词"形态，这三条同构。 */
  private static final String ACTION_ENABLE = "MFA_ENABLE";

  private static final String ACTION_DISABLE = "MFA_DISABLE";

  private static final String ACTION_RESET = "MFA_RESET";

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;
  private final MfaSecretEncryptionService secrets;
  private final MfaQrCodeService qrCodes;
  private final RecoveryCodeService recoveryCodes;
  private final AuditService auditService;
  private final Clock clock;
  private final String issuer;

  public MfaService(
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      MfaSecretEncryptionService secrets,
      MfaQrCodeService qrCodes,
      RecoveryCodeService recoveryCodes,
      AuditService auditService,
      Clock clock,
      @Value("${crm.security.mfa.issuer:CRM}") String issuer) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.secrets = secrets;
    this.qrCodes = qrCodes;
    this.recoveryCodes = recoveryCodes;
    this.auditService = auditService;
    this.clock = clock;
    this.issuer = issuer;
  }

  /**
   * 绑定第一步：生成一把新密钥、把密文落库，账号<b>保持未启用</b>（FR-M04）。
   *
   * <p>重复调用会换掉上一把未启用的密钥——"重新扫一次"必须是幂等的，否则用户扫错了码就没有回头路。
   *
   * <p><b>密钥在这里落库、而不是等 enable 时再落</b>：本流程是无状态的（两次请求之间没有服务端会话）， 第二次请求只能拿动态码来证明"我真的把它装进 App
   * 了"，而校验它必须有那把密钥。
   *
   * @param password 可选的口令二次确认；传了就必须对（见 {@code MfaSetupRequest}）
   */
  public MfaSetupResponse setup(long userId, String password) {
    User user = requireUser(userId);
    if (isEnabled(user)) {
      throw new BusinessException(ErrorCode.MFA_ALREADY_ENABLED);
    }
    requirePasswordIfProvided(user, password);
    String secret = totpService.generateSecret();
    writeSecret(userId, secrets.encrypt(secret));
    String otpauthUrl =
        qrCodes.otpauthUrl(issuer, user.getUsername(), secret, totpService.timeStepSeconds());
    return new MfaSetupResponse(secret, otpauthUrl, qrCodes.pngDataUrl(otpauthUrl), false);
  }

  /**
   * 绑定第二步：校验动态码，通过则启用并<b>一次性</b>下发恢复码（FR-M05）。
   *
   * <p>{@code @Transactional} 的理由：启用标记与恢复码是<b>一件事</b>。若标记写了而恢复码没落齐， 用户就带着一个已启用的 2FA
   * 和"半张纸"进入下一次登录——而恢复码是他丢了认证器后唯一的出路。 要么两件都成，要么都不成。
   *
   * <p>本步骤<b>不</b>触发失败计数与锁定：那是登录路径的防线（SC-M04 数的是"二次验证"的失败）。 绑定时输错一个码只是输错，用户手上有的是机会，而且此时账号还没被保护起来。
   */
  @Transactional
  public MfaEnableResponse enable(long userId, String code) {
    User user = requireUser(userId);
    if (isEnabled(user)) {
      throw new BusinessException(ErrorCode.MFA_ALREADY_ENABLED);
    }
    String pending = user.getTotpSecretEncrypted();
    if (pending == null || pending.isBlank()) {
      throw new BusinessException(ErrorCode.MFA_NOT_ENABLED, "尚未生成绑定密钥，请先获取绑定二维码");
    }
    if (totpService.matchTimeStep(secrets.decrypt(pending), code).isEmpty()) {
      throw new BusinessException(ErrorCode.MFA_CODE_INVALID);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    userMapper.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, userId)
            .set(User::getTwoFactorEnabled, true)
            .set(User::getTwoFactorEnabledAt, now));
    List<String> codes = recoveryCodes.regenerate(userId);
    auditService.record(ACTION_ENABLE, ENTITY_USER, userId, "启用双因素认证");
    return new MfaEnableResponse(true, now, codes);
  }

  /** 状态与剩余恢复码数量（FR-M13）。未启用时不查恢复码表——那批码在关闭时已全部作废。 */
  public MfaStatusResponse status(long userId) {
    User user = requireUser(userId);
    if (!isEnabled(user)) {
      return new MfaStatusResponse(false, null, 0);
    }
    return new MfaStatusResponse(
        true, user.getTwoFactorEnabledAt(), recoveryCodes.countRemaining(userId));
  }

  /**
   * 重新生成恢复码（FR-M06）：旧的一批整体作废，新的明文只在这里返回一次。
   *
   * <p>口令必填（见 {@code MfaRegenerateRequest}）：这一步会让用户手里那张纸作废，
   * 只凭一个已登录的会话就能做的话，一次会话窃取就能在用户真正需要它的时刻把逃生通道掏空。
   */
  public MfaRecoveryCodesResponse regenerateRecoveryCodes(long userId, String password) {
    User user = requireUser(userId);
    requireEnabled(user);
    requirePassword(user, password);
    return new MfaRecoveryCodesResponse(recoveryCodes.regenerate(userId));
  }

  /**
   * 关闭 2FA（FR-M09）：口令 + 动态码或恢复码，密钥与全部恢复码一并作废。
   *
   * <p>{@code @Transactional}：关闭标记、清字段、作废恢复码是同一件事的三个方面。
   */
  @Transactional
  public void disable(long userId, MfaDisableRequest request) {
    User user = requireUser(userId);
    requireEnabled(user);
    requirePassword(user, request.getPassword());
    requireSecondFactor(user, request.getCode(), request.getRecoveryCode());
    clearTwoFactor(userId);
    recoveryCodes.revokeAll(userId);
    auditService.record(ACTION_DISABLE, ENTITY_USER, userId, "关闭双因素认证");
  }

  /**
   * 管理员重置（FR-M10）：把目标用户打回单因素，密钥与恢复码作废。
   *
   * <p><b>幂等</b>：目标用户本来就没启用 2FA 时同样成功（契约 §7 的错误只有 403/404）—— 管理员的意图是"确保这个人现在是单因素"，而"本来就没开"已经满足它。
   *
   * <p><b>不动密码阶段</b>：重置只清 2FA 状态，目标用户下次登录仍要过验证码与口令。 这一步不能被理解为"给他开一扇门"。
   */
  @Transactional
  public void resetByAdmin(long targetUserId) {
    User user = requireUser(targetUserId);
    clearTwoFactor(targetUserId);
    recoveryCodes.revokeAll(targetUserId);
    auditService.record(
        ACTION_RESET, ENTITY_USER, targetUserId, "管理员重置双因素认证：" + user.getUsername());
  }

  // ===== 内部 =====

  private User requireUser(long userId) {
    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    return user;
  }

  private static boolean isEnabled(User user) {
    return Boolean.TRUE.equals(user.getTwoFactorEnabled());
  }

  private void requireEnabled(User user) {
    if (!isEnabled(user)) {
      throw new BusinessException(ErrorCode.MFA_NOT_ENABLED);
    }
  }

  private void requirePassword(User user, String password) {
    if (password == null || password.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "请输入密码");
    }
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "密码错误");
    }
  }

  private void requirePasswordIfProvided(User user, String password) {
    if (password != null && !password.isBlank()) {
      requirePassword(user, password);
    }
  }

  /**
   * 证明"第二个凭据在我手上"：恢复码优先，其次是动态码。
   *
   * <p>恢复码走 {@link RecoveryCodeService#consume}（条件 UPDATE，一次性）；动态码走 {@code
   * TotpService.matchTimeStep}，<b>不</b>标记时间步：标记防的是"同一个码换出两个会话"， 而这里换来的是"关闭
   * 2FA"，且关闭之后密钥与恢复码全部作废——再重放同一个码也没有任何东西可换。
   */
  private void requireSecondFactor(User user, String code, String recoveryCode) {
    if (recoveryCode != null && !recoveryCode.isBlank()) {
      if (!recoveryCodes.consume(user.getId(), recoveryCode)) {
        throw new BusinessException(ErrorCode.RECOVERY_CODE_INVALID);
      }
      return;
    }
    if (code == null || code.isBlank()) {
      throw new BusinessException(ErrorCode.MFA_CODE_INVALID, "请提供动态码或恢复码");
    }
    String encrypted = user.getTotpSecretEncrypted();
    if (encrypted == null || encrypted.isBlank()) {
      // 已启用却没有密钥 = 数据坏了（密钥由本服务的 setup 写入，enable 不会清它）。
      throw new IllegalStateException("账号已启用 2FA 但库中没有密钥：userId=" + user.getId());
    }
    if (totpService.matchTimeStep(secrets.decrypt(encrypted), code).isEmpty()) {
      throw new BusinessException(ErrorCode.MFA_CODE_INVALID);
    }
  }

  private void writeSecret(long userId, String encryptedSecret) {
    userMapper.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, userId)
            .set(User::getTotpSecretEncrypted, encryptedSecret));
  }

  /**
   * 把账号打回未启用：清三个字段。
   *
   * <p><b>密钥列必须真的被写成 {@code NULL}</b>，不能留着一把"虽然没启用但还在"的密钥—— 启用状态是可以再开的，而"上次关闭时没清掉的密钥"会在下次 setup
   * 之前一直躺在库里， 一旦它泄漏，攻击者算出的是<b>用户将来会绑定的</b>那把密钥的码（若用户复用同一个 App 条目）。 这一条由 {@code MfaLifecycleIT}
   * 直接查列断言。
   */
  private void clearTwoFactor(long userId) {
    userMapper.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, userId)
            .set(User::getTwoFactorEnabled, false)
            .set(User::getTotpSecretEncrypted, null)
            .set(User::getTwoFactorEnabledAt, null));
  }
}
