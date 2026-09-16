package com.crm.common;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;

/**
 * TOTP（RFC 6238）纯逻辑：不依赖时钟、不依赖 Spring、不碰任何存储（082-two-factor-auth）。
 *
 * <p><b>为什么没有用第三方 TOTP 库</b>：规格明确要求自研（依赖面收敛）。整个算法就是 RFC 6238 §5.3 的动态截断：{@code HMAC-SHA1(key, 8
 * 字节大端序时间步)} → 取末字节低 4 位为偏移 → 从该偏移起读 4 字节、最高位抹零 → 对 {@code 10^digits} 取模。**没有密钥派生、没有口令哈希**，
 * 所以"自研"在这里的风险面是一个四十行、可对着官方向量逐条核对的函数。
 *
 * <p><b>⚠️ 本类的测试在覆盖率门禁之外，且不可见</b>：{@code pom.xml} 的 JaCoCo 配置排除了 {@code com/crm/common/**}，因此
 * {@code TotpGeneratorTest} 被删掉时<b>构建不会红</b>、覆盖率也不会掉。 而它是本类唯一的正确性防线（RFC 6238 附录 B 的官方测试向量）。这条与
 * {@link OutboundUrlValidator} 的情况相同，在那里已经记过同一个坑。**评审时请把那个测试文件当成承重件， 而不是可以顺手删掉的冗余。**
 *
 * <p><b>本类刻意不含"容错窗口"</b>：{@link #codeAt} 只算给定时间步的那一个码，{@code ±1} 的容错由 调用方（{@code
 * TotpService.matchTimeStep}）逐个时间步调用本类完成。把窗口塞进这里会让"命中的是哪一步" 无从得知，而防重放的键恰恰要用那一步。
 *
 * <p><b>密钥长度</b>：{@link #randomSecret()} 产出 20 字节（160 位）随机密钥的 Base32 编码——RFC 4226 §4 的推荐值，也是所有认证器
 * App 的通用长度。Base32 编码不含填充字符（见 {@link #decodeSecret}）。
 */
public final class TotpGenerator {

  /** RFC 4226 §4 推荐的 HMAC-SHA1 密钥长度（字节）。 */
  static final int SECRET_BYTES = 20;

  /** 默认时间步长（秒）。RFC 6238 §5.2 的 X。 */
  static final int DEFAULT_TIME_STEP_SECONDS = 30;

  /** RFC 6238 §5.2 的 T0：纪元秒起点。 */
  private static final long T0 = 0L;

  private static final String HMAC_ALGORITHM = "HmacSHA1";

  private static final SecureRandom RANDOM = new SecureRandom();

  private TotpGenerator() {}

  /**
   * 生成一个新的随机密钥，返回其 <b>Base32（无填充）</b> 编码。
   *
   * <p>返回编码而不是原始字节：这个值要写进 otpauth:// URI 给认证器 App 扫，Base32 是那条 URI 的既定编码。 编码后长度 32 个字符（20 字节 × 8 /
   * 5），便于人工核对是否被截断。
   */
  public static String randomSecret() {
    byte[] key = new byte[SECRET_BYTES];
    RANDOM.nextBytes(key);
    return new Base32().encodeToString(key).replace("=", "");
  }

  /**
   * Base32 的字母表：{@code A-Z} 加 {@code 2-7}。
   *
   * <p>刻意抄成字面量而不是从 {@code Base32} 反射出来：下面那道校验存在的全部理由就是"不信任 commons-codec
   * 的宽容行为"，从它那里取字母表会让这道校验与自己要防的东西同源。
   */
  private static final Pattern BASE32_ALPHABET = Pattern.compile("[A-Z2-7]+");

  /**
   * 解析 Base32 密钥为原始字节。
   *
   * <p><b>刻意宽容两件事</b>：① 大小写——认证器 App 与用户手工抄录时常把字母写成小写；② 缺失或多余的 填充 {@code
   * =}，以及嵌在中间的空格。这两者都不是密钥内容，拒掉它们只会让"用户抄对了密钥却验证不了"。
   *
   * <p><b>⚠️ 为什么必须自己逐字符校验，不能靠 {@code Base32.decode}</b>：commons-codec 的 {@code Base32}
   * <b>默认丢弃字母表之外的字符而不报错</b>——实测 {@code decode("JBSW-Y3DP")} 会静默跳过 {@code -} 并解出 5 个字节，{@code
   * decode("JBSWY3DP!")} 同理。这正是本方法要防的那种失败： 密钥被静默改成了另一把，表现为"码永远不对"，而调用方拿到的是一个形状完全正常的字节数组，
   * 没有任何线索指向"你抄错了一个字符"。（这条是实测出来的，不是从文档推断的——第一版实现就信了 {@code Base32.decode} 会抛，被 {@code
   * TotpGeneratorTest.decodeSecretRejectsNonBase32} 当场证伪。）
   *
   * <p><b>刻意不宽容</b>：非 Base32 字符（如 {@code 0}/{@code 1}/{@code 8}/{@code 9}，以及误输入的 {@code -}／{@code
   * !}）一律抛 {@link IllegalArgumentException}。它们是**抄错了**的信号。
   *
   * @throws IllegalArgumentException 密钥为空或含非 Base32 字符
   */
  public static byte[] decodeSecret(String base32Secret) {
    if (base32Secret == null) {
      throw new IllegalArgumentException("TOTP 密钥为空");
    }
    String normalized =
        base32Secret.replaceAll("\\s", "").replace("=", "").toUpperCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("TOTP 密钥为空");
    }
    if (!BASE32_ALPHABET.matcher(normalized).matches()) {
      throw new IllegalArgumentException(
          "TOTP 密钥含非 Base32 字符（合法字母表为 A-Z 与 2-7）：" + normalized.replaceAll("[A-Z2-7]", "·"));
    }
    byte[] decoded;
    try {
      decoded = new Base32().decode(normalized);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("TOTP 密钥不是合法的 Base32 编码", ex);
    }
    if (decoded.length == 0) {
      throw new IllegalArgumentException("TOTP 密钥不是合法的 Base32 编码");
    }
    return decoded;
  }

  /**
   * 纪元秒 → 时间步：{@code floor((epochSecond - T0) / timeStepSeconds)}。
   *
   * <p>用 {@link Math#floorDiv} 而不是 {@code /}：纪元秒在当前部署里不会为负，但除法对负数的截断方向是 朝零取整，而 RFC 的公式是向下取整；用
   * floorDiv 使本方法对全定义域都与公式一致，从而不必依赖一个 "它不会为负"的外部假设（那种假设在此处毫无收益，却会在有人拿它算历史时间步时静默偏一格）。
   */
  public static long timeStepOf(long epochSecond, int timeStepSeconds) {
    if (timeStepSeconds <= 0) {
      throw new IllegalArgumentException("时间步长必须为正数：" + timeStepSeconds);
    }
    return Math.floorDiv(epochSecond - T0, timeStepSeconds);
  }

  /** 用默认 30 秒时间步把纪元秒折算为时间步。 */
  public static long timeStepOf(long epochSecond) {
    return timeStepOf(epochSecond, DEFAULT_TIME_STEP_SECONDS);
  }

  /**
   * 计算给定时间步的动态码（RFC 6238 §5.3 动态截断），左补零到 {@code digits} 位。
   *
   * @param key HMAC 密钥原始字节，非空
   * @param timeStep 时间步（见 {@link #timeStepOf}）
   * @param digits 位数，RFC 允许 6~8
   * @throws IllegalArgumentException 密钥为空、位数不在 6~8，或运行环境不支持 HmacSHA1
   */
  public static String codeAt(byte[] key, long timeStep, int digits) {
    if (key == null || key.length == 0) {
      throw new IllegalArgumentException("TOTP 密钥为空");
    }
    if (digits < 6 || digits > 8) {
      throw new IllegalArgumentException("TOTP 位数必须为 6~8：" + digits);
    }

    // 8 字节大端序时间步（RFC 4226 §5.2 的计数器）。用 ByteBuffer 而不是手写位移，
    // 是为了让"大端序"这件事由 API 表达，而不是由一串 << 的书写顺序表达。
    byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array();

    byte[] hash;
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      hash = mac.doFinal(counter);
    } catch (java.security.GeneralSecurityException ex) {
      // HmacSHA1 是 JDK 的必备算法，走到这里说明运行环境被裁剪过。包成 IllegalArgumentException
      // 会把它伪装成调用方的参数问题，故保持为 IllegalStateException（环境问题）。
      throw new IllegalStateException("运行环境不支持 " + HMAC_ALGORITHM, ex);
    }

    // 动态截断：取末字节低 4 位作偏移，从该处读 4 字节、最高位抹零（避免符号扩展）。
    int offset = hash[hash.length - 1] & 0x0F;
    int binary =
        ((hash[offset] & 0x7F) << 24)
            | ((hash[offset + 1] & 0xFF) << 16)
            | ((hash[offset + 2] & 0xFF) << 8)
            | (hash[offset + 3] & 0xFF);

    int modulus = 1;
    for (int i = 0; i < digits; i++) {
      modulus *= 10;
    }
    return String.format(Locale.ROOT, "%0" + digits + "d", binary % modulus);
  }
}
