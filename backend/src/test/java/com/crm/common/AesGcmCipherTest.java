package com.crm.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AES-256-GCM 加解密（082）。
 *
 * <p><b>⚠️ 本测试在覆盖率门禁之外</b>：{@code pom.xml} 的 JaCoCo 配置排除了 {@code com/crm/common/**}， 删掉本文件时 {@code
 * mvn -B verify} 仍然 {@code BUILD SUCCESS}。但"篡改必须被发现"这条性质 <b>只有这里</b>在断言——集成测试里那列的断言是"不含明文、能解回来"，把
 * GCM 换成 CBC 后它们<b>照样全绿</b> （CBC 也能解回来）。故本文件是承重件。
 */
class AesGcmCipherTest {

  private static final byte[] KEY =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

  private static final byte[] OTHER_KEY =
      "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);

  @Test
  @DisplayName("往返：中文、ASCII 与 20 字节 Base32 密钥串都原样解回")
  void roundTrips() {
    for (String plaintext :
        new String[] {
          "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", "短", "含 emoji 🎉 与换行\n的明文", "",
        }) {
      assertEquals(plaintext, AesGcmCipher.decrypt(KEY, AesGcmCipher.encrypt(KEY, plaintext)));
    }
  }

  @Test
  @DisplayName("格式：base64(iv):base64(ct||tag)，IV 恰好 12 字节，密文比明文长 16 字节（tag）")
  void payloadShapeIsIvColonCiphertext() {
    String plaintext = "JBSWY3DPEHPK3PXP";

    String payload = AesGcmCipher.encrypt(KEY, plaintext);
    String[] parts = payload.split(":", -1);

    assertEquals(2, parts.length, "应恰有一段 IV 与一段密文，实际：" + payload);
    assertEquals(12, Base64.getDecoder().decode(parts[0]).length, "IV 必须是 96 位");
    assertEquals(
        plaintext.getBytes(StandardCharsets.UTF_8).length + 16,
        Base64.getDecoder().decode(parts[1]).length,
        "GCM 的密文段含 128 位认证标签");
  }

  @Test
  @DisplayName("每次加密取新 IV：同一明文两次加密的密文不同（IV 重用会让 GCM 泄露明文异或）")
  void encryptUsesFreshIvEachTime() {
    String first = AesGcmCipher.encrypt(KEY, "same-plaintext");
    String second = AesGcmCipher.encrypt(KEY, "same-plaintext");

    assertNotEquals(first, second, "两次加密逐字相同意味着 IV 是固定的");
    assertNotEquals(first.split(":")[0], second.split(":")[0], "IV 段本身就应该不同，而不只是密文段不同");
    assertEquals(AesGcmCipher.decrypt(KEY, first), AesGcmCipher.decrypt(KEY, second), "两者都应解回同一明文");
  }

  /**
   * 本类存在的理由：密文被改动必须<b>抛</b>，而不是解出一段乱码。
   *
   * <p>逐位翻转密文段的每一个字节——只翻一个字节就足以让认证失败，但"某个位置恰好翻完仍能通过" 这种事只在实现有问题时才会发生，逐位扫一遍才能把那种情况暴露出来。
   */
  @Test
  @DisplayName("篡改密文：翻转密文段任意一位都必须抛，绝不返回半截明文")
  void tamperedCiphertextIsRejected() {
    String payload = AesGcmCipher.encrypt(KEY, "JBSWY3DPEHPK3PXP");
    int separator = payload.indexOf(':');
    byte[] iv = Base64.getDecoder().decode(payload.substring(0, separator));
    byte[] ciphertext = Base64.getDecoder().decode(payload.substring(separator + 1));

    for (int i = 0; i < ciphertext.length; i++) {
      byte[] tampered = ciphertext.clone();
      tampered[i] ^= 0x01;
      String forged =
          Base64.getEncoder().encodeToString(iv)
              + ":"
              + Base64.getEncoder().encodeToString(tampered);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> AesGcmCipher.decrypt(KEY, forged),
              "密文第 " + i + " 位被翻转后仍解密成功 —— 认证标签没起作用");
      assertFalse(
          failure.getMessage() == null || failure.getMessage().isBlank(),
          "异常必须带可读原因，否则运维只能看到一次没有线索的失败");
    }
  }

  @Test
  @DisplayName("篡改 IV 同样必须被发现（IV 参与认证）")
  void tamperedIvIsRejected() {
    String payload = AesGcmCipher.encrypt(KEY, "JBSWY3DPEHPK3PXP");
    int separator = payload.indexOf(':');
    byte[] iv = Base64.getDecoder().decode(payload.substring(0, separator));
    iv[0] ^= 0x01;
    String forged = Base64.getEncoder().encodeToString(iv) + ":" + payload.substring(separator + 1);

    assertThrows(IllegalStateException.class, () -> AesGcmCipher.decrypt(KEY, forged));
  }

  @Test
  @DisplayName("换一把密钥解不开（且是抛，不是返回乱码）")
  void wrongKeyIsRejected() {
    String payload = AesGcmCipher.encrypt(KEY, "JBSWY3DPEHPK3PXP");

    assertThrows(IllegalStateException.class, () -> AesGcmCipher.decrypt(OTHER_KEY, payload));
  }

  @Test
  @DisplayName("密钥长度不是 32 字节：拒绝，而不是悄悄按 AES-128 跑")
  void rejectsWrongKeyLength() {
    assertThrows(
        IllegalArgumentException.class,
        () -> AesGcmCipher.encrypt(new byte[16], "x"),
        "16 字节会被 JCE 当成 AES-128，不是 AES-256");
    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.encrypt(new byte[31], "x"));
    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.encrypt(null, "x"));
    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.decrypt(new byte[33], "a:b"));
  }

  @Test
  @DisplayName("格式非法：缺冒号、空段、非 Base64、IV 长度不对 —— 一律抛")
  void rejectsMalformedPayload() {
    for (String malformed :
        new String[] {
          "bm90LWEtcGF5bG9hZA==", // 缺冒号
          ":bm90LWEtcGF5bG9hZA==", // IV 段为空
          "bm90LWEtcGF5bG9hZA==:", // 密文段为空
          "!!!:bm90LWEtcGF5bG9hZA==", // IV 段不是 Base64
          "bm90LWEtcGF5bG9hZA==:!!!", // 密文段不是 Base64
          "YWJj:Ym9keQ==", // IV 只有 3 字节
        }) {
      assertThrows(
          IllegalStateException.class,
          () -> AesGcmCipher.decrypt(KEY, malformed),
          "应拒绝的载荷：" + malformed);
    }
  }

  @Test
  @DisplayName("null 明文/密文：拒绝，而不是加密出「null」四个字母")
  void rejectsNullInput() {
    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.encrypt(KEY, null));
    assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.decrypt(KEY, null));
  }

  @Test
  @DisplayName("密文里不含明文：这一列要防的第一件事")
  void ciphertextDoesNotContainPlaintext() {
    String secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    String payload = AesGcmCipher.encrypt(KEY, secret);

    assertFalse(payload.contains(secret), "密文里出现明文，等于没加密");
    assertTrue(payload.length() > secret.length(), "Base64 的密文应当比明文长");
  }
}
