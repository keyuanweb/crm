package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.AuthResponse;
import com.crm.dto.auth.MfaVerifyRequest;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import com.crm.security.UserStateCache;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录路径上的二次验证（082，FR-M07/FR-M08/FR-M11；contracts/auth-mfa.md §3）。
 *
 * <p><b>本类是"能不能进来"的判定点</b>，与 {@code MfaService}（改账号的安全配置）是两套错误处理与两套顺序要求，
 * 故分成两个类而不是一个大服务。这里每一行都在一条未认证的请求路径上。
 *
 * <h2>为什么没有 {@code @Transactional}</h2>
 *
 * <p><b>加了就会静默吞掉每一条失败审计</b>（FR-M12）：失败路径的顺序是"写审计 → 抛异常"， 事务里抛异常即回滚，那条刚刚插入的 {@code
 * MFA_VERIFY_FAILED} 会跟着消失——而它会消失得<b>毫无痕迹</b>： 接口照常返回 401、日志照常有一条 warn、测试里若只断言状态码则照样全绿。 于是"有人在爆破某人的
 * 2FA"这件事在审计里查不到。 本方法的写操作（定向更新两个时间列）各自是单条语句、自身原子，不需要事务。
 *
 * <h2>顺序是安全语义的一部分，不能重排</h2>
 *
 * <ol>
 *   <li><b>只读票据、不消费</b>（{@code findTicketUserId}）：若这一步就消费，第一次输错码票据就没了， "剩余尝试次数"永远没有意义，SC-M04 的"第 6
 *       次"也永远不会发生。
 *   <li><b>锁定检查在验码之前</b>：所以锁定期间<b>即使码是对的也被拒</b>（429）。反过来写（先验码、
 *       通过了就放行）会让锁定只在"继续输错"时生效——而爆破者一旦蒙对就直接进来了，锁定形同虚设。
 *   <li><b>验码</b>：恢复码分支走条件 UPDATE 消费；TOTP 分支命中后必须成功标记该时间步 （{@code setIfAbsent}），标记失败 = 这个码已经被用过 =
 *       本次失败（FR-M08 防重放）。
 *   <li><b>消费票据</b>（{@code getAndDelete}，且必须返回 {@code true}）：这一步才让"一张票据 ⇒ 一个会话"
 *       在并发下成立。注意防重放抓不到"两张<b>不同</b>的有效恢复码配同一张票据"这种组合——那里唯一的护栏就是本步的原子性。
 *   <li><b>重读用户</b>：消费票据之后再读一次。第一次读到的用户只用于验码，而"能不能进来"必须对着
 *       <b>当下</b>的行判——从密码阶段到这里可能已经过了几分钟，期间该账号可能被停用、或被管理员重置了 2FA （FR-M10）。
 *   <li><b>收尾</b>：清零失败计数 → 定向更新 {@code last_login_at} 与 {@code last_2fa_verified_at} → 预热用户状态缓存 →
 *       签发令牌。
 * </ol>
 *
 * <p><b>{@code last_login_at} 不在这里之前的任何一步写</b>（{@code AuthService.login} 的密码阶段也刻意跳过它）：
 * 一次失败的二次验证不该在库里看起来像一次成功登录， 而那个列常被用来判断"这个账号最近活没活跃"。 它在最终成功的那一刻与 {@code last_2fa_verified_at}
 * <b>同一条语句</b>写入，两者时间戳因此必然一致。
 *
 * <p><b>用户列一律定向更新</b>（{@code LambdaUpdateWrapper} + 实体传 {@code null}）：085 的回归护栏，理由见 {@code
 * MfaService} 的类 javadoc。 本方法尤其容易踩——它手里正好有一个从库里读出来的 {@code User} 实体。
 *
 * <p><b>提交的码不进日志、不进审计</b>：审计 detail 只写第几次失败。动态码是短时效的， 而恢复码是长期有效的凭证——两者都不该出现在任何可被读日志的人看到的地方。
 */
@Service
public class MfaVerificationService {

  private static final Logger log = LoggerFactory.getLogger(MfaVerificationService.class);

  private static final String ENTITY_USER = "USER";

  /**
   * FR-M12 列举的四类安全事件里"校验失败"那一类。
   *
   * <p>动作名与 {@code MfaService} 的 {@code MFA_ENABLE} / {@code MFA_DISABLE} / {@code MFA_RESET}
   * 同构（动词_名词）， 且刻意**不以 {@code _FAILED} 之外的形式**命名——审计查询里要能按动作名选出"所有失败的二次验证"。
   */
  private static final String ACTION_VERIFY_FAILED = "MFA_VERIFY_FAILED";

  private final MfaStateStore stateStore;
  private final TotpService totpService;
  private final MfaSecretEncryptionService secrets;
  private final RecoveryCodeService recoveryCodes;
  private final UserMapper userMapper;
  private final UserStateCache userStateCache;
  private final TokenService tokenService;
  private final AuditService auditService;
  private final Clock clock;

  public MfaVerificationService(
      MfaStateStore stateStore,
      TotpService totpService,
      MfaSecretEncryptionService secrets,
      RecoveryCodeService recoveryCodes,
      UserMapper userMapper,
      UserStateCache userStateCache,
      TokenService tokenService,
      AuditService auditService,
      Clock clock) {
    this.stateStore = stateStore;
    this.totpService = totpService;
    this.secrets = secrets;
    this.recoveryCodes = recoveryCodes;
    this.userMapper = userMapper;
    this.userStateCache = userStateCache;
    this.tokenService = tokenService;
    this.auditService = auditService;
    this.clock = clock;
  }

  /**
   * 用一次性的 {@code mfaToken} 提交第二个凭据，成功则签发令牌。
   *
   * @param request 为 {@code null} 时按"没有票据"处理（空请求体与无效票据对用户是同一件事：请重新登录）
   */
  public AuthResponse verify(MfaVerifyRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.MFA_TICKET_INVALID);
    }
    // ① 只看一眼，不消费（理由见类 javadoc 的顺序说明 ①）
    Optional<Long> ticketOwner = stateStore.findTicketUserId(request.getMfaToken());
    if (ticketOwner.isEmpty()) {
      throw new BusinessException(ErrorCode.MFA_TICKET_INVALID);
    }
    long userId = ticketOwner.get();
    User user = userMapper.selectById(userId);
    if (user == null) {
      // 票据指向一个已被删除的用户：对调用方与"票据无效"是同一件事，且不必让他知道那个 id 曾经存在。
      throw new BusinessException(ErrorCode.MFA_TICKET_INVALID);
    }
    if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
      // 票据取得之后、本次提交之前，管理员重置了该账号的 2FA（FR-M10）：票据所凭的前提已经消失，
      // 而**现在**的账号确实不需要第二因素了 —— 让他重新登录，那时登录路径会如实按单因素处理。
      //
      // 这一条与 ⑤ 那一处判据相同，但<b>不能互相替代</b>，两处都必须在：
      //   · 放在这里，是为了在**验码之前**就退出 —— 重置会同时清空密钥列，若不先退出，
      //     验码分支会看到"已启用却没有密钥"并抛出 IllegalStateException（500）：
      //     一次正常的并发操作被报成"数据坏了"，而用户拿到的是一个 500，不知道该做什么。
      //   · 放在 ⑤（消费票据之后重读），是因为从密码阶段到这里可能已经过去几分钟，
      //     上面这次读到的行在验码与消费票据期间还可能被改 —— 那个才是最终判据。
      throw new BusinessException(ErrorCode.MFA_TICKET_INVALID, "该账号的双因素认证状态已变更，请重新登录");
    }

    // ② 锁定检查必须在验码之前（②）
    long lockedSeconds = stateStore.lockRemainingSeconds(userId);
    if (lockedSeconds > 0) {
      throw locked(lockedSeconds);
    }

    // ③ 验码
    boolean usedRecoveryCode = isProvided(request.getRecoveryCode());
    if (!verifySecondFactor(user, request.getCode(), request.getRecoveryCode())) {
      throw failVerification(
          userId, usedRecoveryCode ? ErrorCode.RECOVERY_CODE_INVALID : ErrorCode.MFA_CODE_INVALID);
    }

    // ④ 消费票据：getAndDelete 的原子性才让"一张票据 ⇒ 一个会话"在并发下成立（④）
    if (!stateStore.consumeTicket(request.getMfaToken(), userId)) {
      throw new BusinessException(ErrorCode.MFA_TICKET_INVALID);
    }

    // ⑤ 对着**当下**的行判"能不能进来"（⑤）
    User fresh = userMapper.selectById(userId);
    if (fresh == null || !Boolean.TRUE.equals(fresh.getEnabled())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用");
    }
    if (!Boolean.TRUE.equals(fresh.getTwoFactorEnabled())) {
      // 期间被管理员重置（FR-M10）或用户自己在别处关掉了 2FA：本次票据所凭的前提已经消失。
      // 不在这里"顺手放行"——放行意味着一次本应需要第二因素的验证被静默跳过；让他重新登录，
      // 那时这条路径会如实按单因素处理（账号现在确实是单因素）。
      throw new BusinessException(ErrorCode.MFA_TICKET_INVALID, "该账号的双因素认证状态已变更，请重新登录");
    }

    // ⑥ 收尾
    stateStore.clearFailures(userId);
    LocalDateTime now = LocalDateTime.now(clock);
    userMapper.update(
        null,
        new LambdaUpdateWrapper<User>()
            .eq(User::getId, userId)
            .set(User::getLastLoginAt, now)
            .set(User::getLast2faVerifiedAt, now));
    fresh.setLastLoginAt(now);
    fresh.setLast2faVerifiedAt(now);
    // 预热与登录路径同一份缓存：否则验证成功之后的第一个请求会因缓存未命中而多打一次库
    // （且在集群里可能读到另一个节点上的旧值）。
    int tokenVersion = fresh.getTokenVersion() == null ? 0 : fresh.getTokenVersion();
    userStateCache.put(
        userId,
        new UserStateCache.UserState(Boolean.TRUE.equals(fresh.getEnabled()), tokenVersion));
    log.info("二次验证通过：userId={}", userId);
    return tokenService.issue(fresh);
  }

  // ===== 内部 =====

  /**
   * 第二个凭据是否正确。恢复码优先于动态码（与 {@code MfaService.requireSecondFactor} 同一套语义）。
   *
   * <p>本方法<b>只判真假，不抛业务异常</b>：失败之后要先记数、写审计、再决定回哪个错误码 （第 5 次失败回 429 而不是 401），
   * 那是调用方的事。唯一从本方法抛出去的是"库里的密钥坏了"——那不是"你的码不对"。
   */
  private boolean verifySecondFactor(User user, String code, String recoveryCode) {
    if (isProvided(recoveryCode)) {
      return recoveryCodes.consume(user.getId(), recoveryCode);
    }
    if (!isProvided(code)) {
      // 两个都没给：这不是"码错了"，但用户能做的事完全相同（重开一次验证）。
      // 不单独给它一个错误码——契约里没有第 9 个码，而多一个码就等于多一处要维护的对外承诺。
      return false;
    }
    String encrypted = user.getTotpSecretEncrypted();
    if (!isProvided(encrypted)) {
      // 已启用却没有密钥 = 数据坏了（密钥由 setup 写入，enable/disable 的语义都不会留下这种状态）。
      throw new IllegalStateException("账号已启用 2FA 但库中没有密钥：userId=" + user.getId());
    }
    OptionalLong timeStep = totpService.matchTimeStep(secrets.decrypt(encrypted), code);
    if (timeStep.isEmpty()) {
      return false;
    }
    // 防重放（FR-M08）：命中的**那一步**被标记过 ⇒ 同一个码已经被用掉，本次不算通过。
    // 标记用 setIfAbsent，故并发下的两次提交只有一次能拿到 true。
    return stateStore.markTimeStepUsed(
        user.getId(), timeStep.getAsLong(), totpService.timeStepRetention());
  }

  /**
   * 记一次失败：写审计并返回给调用方的错误码（达阈值则 429）。
   *
   * <p>审计用 {@link AuditService#recordAsSystem}：本路径上<b>没有登录主体</b>（{@code SecurityFilterChain}
   * 把本端点放行了，{@code SecurityContext} 是空的）。用 {@code record(...)} 会写出一条 actorId 为 {@code null}
   * 的"无主体审计行"——那正是 {@code AuditService} 自己的 javadoc 明确要避免的东西： 既归因不到人，也无法与"用户被删除后 actor_id 悬空"区分。
   *
   * <p><b>先写审计、再抛异常</b>，且本方法没有事务（见类 javadoc）：顺序反过来或包上事务，都会让失败审计在某些路径上消失。
   */
  private BusinessException failVerification(long userId, ErrorCode code) {
    int failures = stateStore.recordFailure(userId);
    auditService.recordAsSystem(
        ACTION_VERIFY_FAILED,
        ENTITY_USER,
        userId,
        "二次验证失败（第 " + failures + "/" + stateStore.maxAttempts() + " 次）");
    long lockedSeconds = stateStore.lockRemainingSeconds(userId);
    if (lockedSeconds > 0) {
      return locked(lockedSeconds);
    }
    int remaining = Math.max(0, stateStore.maxAttempts() - failures);
    return new BusinessException(code, code.getMessage() + "，还可尝试 " + remaining + " 次");
  }

  /**
   * 锁定中的响应。
   *
   * <p>把剩余秒数写进 message：契约要求 429 的响应"含剩余锁定秒数"，而错误信封里除 code/message 外没有可放它的位置 ——为一个数字给 {@code
   * ApiResponse} 加一层 data，代价是改全站信封的形状。
   */
  private static BusinessException locked(long seconds) {
    return new BusinessException(
        ErrorCode.MFA_LOCKED, ErrorCode.MFA_LOCKED.getMessage() + "，请 " + seconds + " 秒后再试");
  }

  private static boolean isProvided(String value) {
    return value != null && !value.isBlank();
  }
}
