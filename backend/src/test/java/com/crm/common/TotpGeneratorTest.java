package com.crm.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * TOTP 纯逻辑（082）。
 *
 * <p><b>⚠️ 本测试在覆盖率门禁之外</b>：{@code pom.xml} 的 JaCoCo 配置排除了 {@code com/crm/common/**}， 所以删掉本文件时
 * {@code mvn -B verify} 仍然 {@code BUILD SUCCESS}、覆盖率也不会掉一格。 但它是 {@link TotpGenerator}
 * 正确性的<b>唯一</b>防线——下面是 RFC 的官方向量， 不是我们自己算出来又自己认下来的值。评审时请不要把它当冗余删掉。
 *
 * <p><b>为什么必须对官方向量</b>：TOTP 的失败形态是"算出来的码与认证器 App 不一致"， 而那种不一致在端到端测试里表现为"验证码错误"，与"用户抄错码"完全同形。只有对官方向量
 * 逐条比对，才能把"实现错了"与"用户错了"分开。用自己实现的另一半算期望值则什么也证明不了。
 */
class TotpGeneratorTest {

  /** RFC 6238 附录 B 与 RFC 4226 附录 D 共用的种子：ASCII "12345678901234567890"（20 字节）。 */
  private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  /**
   * RFC 6238 附录 B 的官方向量（SHA1 行，8 位码）。
   *
   * <p>第一条同时钉住了"左补零"：{@code 07081804} 若漏掉补零会得到 {@code 7081804}（7 位）。
   */
  @ParameterizedTest(name = "RFC 6238 附录 B：T={0}s ⇒ {1}")
  @CsvSource({
    "59,          94287082",
    "1111111109,  07081804",
    "1111111111,  14050471",
    "1234567890,  89005924",
    "2000000000,  69279037",
    "20000000000, 65353130",
  })
  void matchesRfc6238AppendixBVectors(long epochSecond, String expected) {
    long timeStep = TotpGenerator.timeStepOf(epochSecond, 30);

    assertEquals(expected, TotpGenerator.codeAt(RFC_SEED, timeStep, 8), "T=" + epochSecond);
  }

  /**
   * RFC 4226 附录 D 的官方 HOTP 向量（6 位码，计数器 0~9）。
   *
   * <p>TOTP 就是"计数器 = 时间步"的 HOTP，所以这组向量等价地钉住了 6 位分支—— 而 6 位是本项目实际使用的位数（8 位的官方向量不能替代它）。
   */
  @ParameterizedTest(name = "RFC 4226 附录 D：计数器={0} ⇒ {1}")
  @CsvSource({
    "0, 755224",
    "1, 287082",
    "2, 359152",
    "3, 969429",
    "4, 338314",
    "5, 254676",
    "6, 287922",
    "7, 162583",
    "8, 399871",
    "9, 520489",
  })
  void matchesRfc4226AppendixDVectors(long counter, String expected) {
    assertEquals(expected, TotpGenerator.codeAt(RFC_SEED, counter, 6), "counter=" + counter);
  }

  @Test
  @DisplayName("timeStepOf：60s 内落在同一步，跨过 30s 边界步进一格")
  void timeStepBucketsByThirtySeconds() {
    assertEquals(1L, TotpGenerator.timeStepOf(30));
    assertEquals(1L, TotpGenerator.timeStepOf(59));
    assertEquals(2L, TotpGenerator.timeStepOf(60));
    // 1111111109 / 30 = 37037036.97 → 向下取整为 37037036（不是四舍五入）
    assertEquals(37037036L, TotpGenerator.timeStepOf(1111111109));
  }

  /**
   * 负纪元秒必须向下取整，与 RFC 的公式一致。
   *
   * <p>当前部署不会拿负数调用它，但 {@code /} 对负数朝零取整、RFC 的公式向下取整——这个差异 在此处是一个静默偏一格的陷阱，故用断言把它钉在公式那一侧（见 {@code
   * Math#floorDiv} 的用法）。
   */
  @Test
  @DisplayName("timeStepOf：负纪元秒按 floor 而非朝零截断")
  void timeStepOfFloorsNegativeEpochSeconds() {
    assertEquals(-1L, TotpGenerator.timeStepOf(-1));
    assertEquals(-2L, TotpGenerator.timeStepOf(-31));
  }

  @Test
  @DisplayName("codeAt：位数超出 RFC 的 6~8 一律拒绝（而不是静默按 6 位算）")
  void rejectsOutOfRangeDigits() {
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.codeAt(RFC_SEED, 0L, 5));
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.codeAt(RFC_SEED, 0L, 9));
  }

  @Test
  @DisplayName("codeAt：空密钥拒绝，不返回一个看起来正常的码")
  void rejectsEmptyKey() {
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.codeAt(new byte[0], 0L, 6));
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.codeAt(null, 0L, 6));
  }

  @Test
  @DisplayName("randomSecret：32 个字符、解回 20 字节、两次调用不同")
  void randomSecretIsTwentyBytesOfBase32() {
    String first = TotpGenerator.randomSecret();
    String second = TotpGenerator.randomSecret();

    assertEquals(32, first.length(), "20 字节 × 8 / 5 = 32 个 Base32 字符");
    assertEquals(20, new Base32().decode(first).length);
    assertNotEquals(first, second, "两次调用取到同一把密钥意味着随机源没接上");
    assertTrue(first.matches("[A-Z2-7]{32}"), "应为无填充的大写 Base32，实际：" + first);
  }

  /**
   * 大小写、空格、填充都不是密钥内容：宽容它们，否则"用户抄对了却验证不了"。
   *
   * <p>四行输入全都解到同一把密钥——断言比长度更强，它能同时排除"宽容过头把字符改掉了"。
   */
  @ParameterizedTest(name = "宽容写法：{0}")
  @ValueSource(
      strings = {"JBSWY3DPEHPK3PXP", "jbs wy3dpehpk3pxp", "JBSWY3DPEHPK3PXP=", "jBsWy3DpEHPK3pXp"})
  void decodeSecretToleratesCaseSpacesAndPadding(String written) {
    assertArrayEquals(
        TotpGenerator.decodeSecret("JBSWY3DPEHPK3PXP"),
        TotpGenerator.decodeSecret(written),
        "写法变体必须解到同一把密钥：" + written);
  }

  /** 与项目其它地方一致：Base32 的 0/1/8/9 是**抄错**的信号，不能静默丢弃。 */
  @ParameterizedTest(name = "拒绝非 Base32 字符：{0}")
  @ValueSource(strings = {"0189", "JBSW-Y3DP", "JBSWY3DP!"})
  void decodeSecretRejectsNonBase32(String written) {
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.decodeSecret(written));
  }

  @Test
  @DisplayName("decodeSecret：null 与空白拒绝（而不是当成空密钥继续跑）")
  void decodeSecretRejectsBlank() {
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.decodeSecret(null));
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.decodeSecret("   "));
    assertThrows(IllegalArgumentException.class, () -> TotpGenerator.decodeSecret("===="));
  }
}
