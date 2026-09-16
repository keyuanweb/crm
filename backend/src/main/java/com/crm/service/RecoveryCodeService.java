package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.entity.UserRecoveryCode;
import com.crm.repository.UserRecoveryCodeMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一次性恢复码：生成、消费、计数、作废（082-two-factor-auth，FR-M05/FR-M06）。
 *
 * <p>恢复码是 2FA 唯一不依赖认证器 App 的逃生通道（规格非目标：短信/邮件找回不做，见 {@code spec.md}）。
 * 它同时是**一次性的**和**高熵的**，这两条合起来决定了本类的两个关键取舍。
 *
 * <h2>为什么每码独立加盐</h2>
 *
 * <p>落库形态是 {@code base64(16 字节随机盐) + ":" + hex(sha256(salt||code))}，每行一个独立的新盐。
 *
 * <p><b>如实记下盐挡得住什么、挡不住什么</b>：盐挡的是**预计算与跨行复用**——攻击者拿到库也无法用一张彩虹表
 * 同时命中所有用户的所有码，每行都必须单独穷举。它挡不住**单行穷举**：字母表 31 符号、码长 8 ⇒ 31^8 ≈ 2^39.6，对离线攻击者仍属可行（把 {@code
 * salt||code} 喂 SHA-256 是快函数）。 也就是说，盐<b>不是</b>这张表的主要防线；主要防线是「恢复码只在**在线**校验路径上被接受， 而在线路径有失败计数与 15
 * 分钟锁定（{@code MfaStateStore}）」。之所以仍然加盐而不是像 {@code ApiKeyService} 那样裸哈希：
 * 那一边必须**按哈希反查**（校验时只有明文，要靠哈希当索引），故不能加盐；这一边不需要反查 ——每行自带盐，反查也无从建索引（见 {@link #consume}
 * 的取舍），于是盐是白拿的收益。
 *
 * <p>比对用 {@link MessageDigest#isEqual}（定长比较，不随前缀匹配长度提前返回）， 避免把"哈希相等"变成一条可被计时观测的旁路。
 *
 * <h2>为什么 {@code consume} 必须是条件 UPDATE</h2>
 *
 * <p>{@code SET used=1, used_at=? WHERE id=? AND used=0} 且要求影响行数恰为 1。等价的劣解是「{@code selectOne} 查未使用行
 * → 判哈希 → {@code updateById}」：两步之间有 TOCTOU 窗口， 并发下同一个恢复码<b>能被消费两次</b>——这正是 SC-M05 禁止的。两种写法在
 * MockMvc（单线程）下可观测行为**相同**，故钉住它的是 {@code RecoveryCodeServiceTest} 的调用形状断言。
 *
 * <p><b>代价如实记</b>：盐在存储串内部，所以无法按哈希建索引，{@link #consume} 只能把该用户**全部未使用行**读出来逐行比对。 行数上界是生成时的 {@code
 * crm.security.mfa.recovery-code-count}（默认 10），且"已使用"的行不会参与比对， 故这是 O(码长) 的小循环，不是可被放大的路径。
 */
@Service
public class RecoveryCodeService {

  /**
   * 恢复码字母表：31 个符号，**刻意与 {@code CaptchaService.CHARS} 逐字符相同**。
   *
   * <p>两处各自持有一份字面量（{@code CaptchaService.CHARS} 是 {@code private}，本类不引它、也不为它开
   * 访问口——那是为本类需求去改别人类的形状）。跨文件同步靠的是这条注释 + {@code RecoveryCodeServiceTest} 把本字面量逐字符钉死：
   * 任何一边漂移都会让那边的测试红，而不是让两套"看起来都像无歧义字符集"的字母表悄悄分家。
   *
   * <p>去掉了 {@code O}/{@code 0}（形似）与 {@code I}/{@code l}/{@code 1}（形似）——恢复码的主要使用场景是**人从屏幕上抄到纸上**，
   * 易混淆字符的代价是一次失败的逃生尝试。
   */
  static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

  /** 恢复码长度（8 位 ⇒ 31^8 ≈ 2^39.6 种）。 */
  static final int CODE_LENGTH = 8;

  /** 盐长度：安全边际远超"防预计算"所需，且 base64 后只有 24 字符。 */
  private static final int SALT_BYTES = 16;

  /** 盐与哈希的分隔符。base64（标准表）不会产出 {@code ':'}，故分割点唯一。 */
  private static final char SEPARATOR = ':';

  private static final HexFormat HEX = HexFormat.of();

  private final UserRecoveryCodeMapper mapper;
  private final Clock clock;
  private final int codeCount;
  private final SecureRandom random = new SecureRandom();

  public RecoveryCodeService(
      UserRecoveryCodeMapper mapper,
      Clock clock,
      @Value("${crm.security.mfa.recovery-code-count:10}") int codeCount) {
    if (codeCount <= 0) {
      throw new IllegalArgumentException("crm.security.mfa.recovery-code-count 必须为正数：" + codeCount);
    }
    this.mapper = mapper;
    this.clock = clock;
    this.codeCount = codeCount;
  }

  /**
   * 作废该用户现有全部恢复码并生成一批新的，返回**明文**（仅此一次）。
   *
   * <p>返回的明文不进日志、不进库：它是"能换一个会话的字符串"，调用方只在生成响应里回显一次。
   *
   * <p>{@code @Transactional} 的理由：本方法 = 一次软删 + N 次插入。若中途失败却没有事务，
   * 用户的旧码已被作废、新码又没落齐，账户会带着<b>一批残缺的恢复码</b>进入下一次登录 —— 而那可能正是他唯一能进门的凭据（认证器丢了、只能靠恢复码的人）。要么全换、要么全不换。
   */
  @Transactional
  public List<String> regenerate(long userId) {
    revokeAll(userId);
    List<String> plain = new ArrayList<>(codeCount);
    for (int i = 0; i < codeCount; i++) {
      String code = randomCode();
      UserRecoveryCode row = new UserRecoveryCode();
      row.setUserId(userId);
      row.setCodeHash(hashOf(code));
      row.setUsed(false);
      mapper.insert(row);
      plain.add(code);
    }
    return plain;
  }

  /**
   * 消费一个恢复码，返回是否**由本次调用成功消费**。
   *
   * <p>{@code false} 覆盖四种情形，调用方对它们都应报 {@code RECOVERY_CODE_INVALID}（都是一个意思：
   * 这个码不能用了）：形不成合法码（空/含字母表外字符/长度不符）、没有匹配的行、 匹配到了但已被别人抢先消费、以及并发下 UPDATE 影响行数不为 1。
   *
   * <p><b>不区分"码不存在"与"码已用过"</b>：对合法用户没有差别，而对猜码的人，任何区分都是一条把 "这个码存在过"告诉他的信号。
   *
   * <p>命中之后立刻停止比对（一次成功消费只允许改一行），但**不**提前返回 true —— 必须等 UPDATE 的 影响行数说话，否则就又回到了"先判断后写入"的 TOCTOU。
   */
  public boolean consume(long userId, String rawCode) {
    String code = normalize(rawCode);
    if (code == null) {
      return false;
    }
    for (UserRecoveryCode row : unusedRows(userId)) {
      if (!matches(code, row.getCodeHash())) {
        continue;
      }
      // 条件 UPDATE：`used=0` 既是判据也是守卫。并发下两个请求都能走到这里，
      // 但只有一行会被改（另一个的 WHERE 不再成立、影响行数为 0）。
      int rows =
          mapper.update(
              null,
              new LambdaUpdateWrapper<UserRecoveryCode>()
                  .eq(UserRecoveryCode::getId, row.getId())
                  .eq(UserRecoveryCode::getUsed, false)
                  .set(UserRecoveryCode::getUsed, true)
                  .set(UserRecoveryCode::getUsedAt, LocalDateTime.now(clock)));
      return rows == 1;
    }
    return false;
  }

  /** 剩余可用（未消费）的恢复码数量。 */
  public int countRemaining(long userId) {
    Long count =
        mapper.selectCount(
            new LambdaQueryWrapper<UserRecoveryCode>()
                .eq(UserRecoveryCode::getUserId, userId)
                .eq(UserRecoveryCode::getUsed, false));
    return count == null ? 0 : count.intValue();
  }

  /**
   * 作废该用户的全部恢复码（关闭 2FA / 管理员重置 / 重新生成时调用）。
   *
   * <p>用<b>软删除</b>而不是物理删除：{@link com.crm.entity.BaseEntity} 的 {@code deleted} 在这里就是
   * "作废"语义，而行本身留下，于是「某个码曾经存在过」在库上仍可追溯。
   *
   * <p>软删**该用户的全部行**（含已消费的），而不是只删 {@code used=0} 的那些：已消费的行本就不持有任何
   * 凭证，删它与不删它对授权没有影响，但"全删"让撤销的后置条件可以用一条<b>不带 {@code used} 谓词</b>的 查询验证
   * ——「该用户不再有任何未软删的恢复码行」。而且信息并没丢：{@code used=1 AND deleted=1} 与 {@code used=0 AND deleted=1}
   * 仍然分得开"用过之后被作废"和"没用过就被作废"。
   *
   * <p>不加 {@code @Transactional}：单条 DELETE 语句自身就是原子的，而唯一的多语句调用方 {@link #regenerate} 已经开了事务。
   */
  public void revokeAll(long userId) {
    mapper.delete(
        new LambdaQueryWrapper<UserRecoveryCode>().eq(UserRecoveryCode::getUserId, userId));
  }

  // ===== 明文 ↔ 存储形态 =====

  /** 一批可用的未消费行。行数上界见类 javadoc（{@code recovery-code-count}）。 */
  private List<UserRecoveryCode> unusedRows(long userId) {
    return mapper.selectList(
        new LambdaQueryWrapper<UserRecoveryCode>()
            .eq(UserRecoveryCode::getUserId, userId)
            .eq(UserRecoveryCode::getUsed, false));
  }

  private String randomCode() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
    }
    return sb.toString();
  }

  /** 生成一份新的盐并算出存储串。每码独立调盐，故同一明文码两次生成得到两串不同的哈希。 */
  private String hashOf(String code) {
    byte[] salt = new byte[SALT_BYTES];
    random.nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt) + SEPARATOR + HEX.formatHex(sha256(salt, code));
  }

  /**
   * 规范化用户输入：丢掉一切分隔符、统一成大写，并要求结果**逐字符都在字母表内**且长度正确。
   *
   * <p>为什么要丢掉空白与连字符：恢复码的使用场景是"认证器丢了、现在必须进门"，输入来源是纸、截图、聊天
   * 记录——里面常见的插入物是空格与分组连字符。把这些当作"码错了"，代价是让一个**本来就着急**的人 反复失败，直至撞上 15 分钟锁定。
   *
   * <p><b>这种丢弃是单射的</b>（不会把两个不同的码映到同一串）：字母表里既没有空白也没有连字符， 故被丢掉的字符<b>不可能</b>是任何一个存储码的一部分 ——
   * 两个不同的存储码在去掉分隔符后仍然不同。 若字母表将来加了连字符，这条论证就失效（那会让 {@code "AB-CD"} 与 {@code "ABCD"} 同形），届时须改回去。
   *
   * <p>返回 {@code null} = 不可能是恢复码，调用方直接拒。字典校验（而不是只查长度）顺带把"超长输入"挡在 哈希之前，长度检查则让"字符合法但长度不对"也不进比对。
   */
  static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (Character.isWhitespace(ch) || ch == '-') {
        continue;
      }
      char upper = Character.toUpperCase(ch);
      if (indexOf(upper) < 0) {
        return null;
      }
      sb.append(upper);
    }
    if (sb.length() != CODE_LENGTH) {
      return null;
    }
    return sb.toString();
  }

  /**
   * 明文是否匹配某个存储串。形态不符一律当作不匹配，**不抛**：库里的行可能是更早版本或人工插入的， 一个 {@code IllegalArgumentException}
   * 会把"这个码不对"升级成"这个接口 500"。
   */
  private static boolean matches(String code, String stored) {
    if (stored == null) {
      return false;
    }
    int sep = stored.indexOf(SEPARATOR);
    if (sep <= 0) {
      return false;
    }
    byte[] salt;
    byte[] expected;
    try {
      salt = Base64.getDecoder().decode(stored.substring(0, sep));
      expected = HEX.parseHex(stored.substring(sep + 1));
    } catch (IllegalArgumentException ex) {
      return false;
    }
    return MessageDigest.isEqual(sha256(salt, code), expected);
  }

  private static byte[] sha256(byte[] salt, String code) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      // SHA-256 是 JDK 必备算法，这里不可达；仍然抛而不是吞，否则会变成一个"所有恢复码都失效"的静默故障。
      throw new IllegalStateException("JVM 不支持 SHA-256", ex);
    }
    digest.update(salt);
    digest.update(code.getBytes(StandardCharsets.UTF_8));
    return digest.digest();
  }

  private static int indexOf(char ch) {
    for (int i = 0; i < ALPHABET.length; i++) {
      if (ALPHABET[i] == ch) {
        return i;
      }
    }
    return -1;
  }
}
