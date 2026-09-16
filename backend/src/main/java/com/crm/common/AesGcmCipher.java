package com.crm.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM 认证加密（AEAD），用于静态加密 TOTP 密钥（082-two-factor-auth）。
 *
 * <p><b>为什么是 GCM 而不是 CBC</b>：库里那一列要防的不只是"读出来"，还有"改掉"——攻击者若能写库， 把某人的 {@code totp_secret_encrypted}
 * 换成自己知道密钥的密文，就能给任意账号绑上自己的认证器。 CBC 只保证机密性，改动是静默的（解出来是一段乱码，然后按"密钥格式不对"处理，看起来像数据损坏）。 GCM 带 128
 * 位认证标签，<b>任何一位被改都会让解密直接失败</b>，把一次静默的接管变成一次显式报错。
 *
 * <p><b>格式</b>：{@code base64(iv) + ":" + base64(ciphertext||tag)}。
 *
 * <p>用冒号分隔而不是把 IV 拼在密文前面：读的人一眼能看出 IV 是独立的、且能与密文分别取出核对； 拼接格式在评审时只能靠"前 12 字节是
 * IV"这句注释成立，而注释会腐坏。两种做法的字节数一样。 密文段里 {@code ciphertext||tag} 是 JCE 的既定布局（{@code doFinal} 的输出即含 tag），
 * 故不再自己切一刀——自己切会引入一个"tag 放在哪"的自由度，而那个自由度没有收益。
 *
 * <p><b>不改动密钥用途</b>：本类不派生密钥。传入的 {@code key32} 必须是恰好 32 字节；由 {@code MfaSecretEncryptionService}
 * 负责把配置里的 Base64 密钥解出来并在这里失败。 把"配置怎么来"与"密码学怎么做"放在一个类里，会让后者无法在不碰配置的前提下被测试。
 *
 * <p><b>⚠️ 本类的测试在覆盖率门禁之外，且不可见</b>：{@code pom.xml} 的 JaCoCo 配置排除了 {@code com/crm/common/**}，删掉
 * {@code AesGcmCipherTest} 时<b>构建不会红</b>。 该测试是"篡改必须被发现"这条性质的唯一防线（见 {@link OutboundUrlValidator}
 * 里同一个坑的记载）。
 *
 * <p><b>无 AAD</b>：本类不绑定附加认证数据。当前唯一用途是加密一个独立字段，没有"密文必须属于某一行" 的绑定需求——若将来要把密文与 {@code user_id}
 * 绑定（防密文在行间搬移），那时再加 AAD 参数， 并必须同步改密文格式（旧密文会解不开，需要一次性重加密，不能静默兼容）。
 */
public final class AesGcmCipher {

  /** GCM 标准 IV 长度（96 位）。不是"随便取 12"，是非 96 位 IV 会让 GCM 走额外的 GHASH 派生路径。 */
  static final int IV_BYTES = 12;

  /** 认证标签长度（位）。128 位是 GCM 允许的最大值，没有理由取更小。 */
  static final int TAG_BITS = 128;

  /** AES-256 的密钥长度（字节）。 */
  static final int KEY_BYTES = 32;

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";

  private static final SecureRandom RANDOM = new SecureRandom();

  private AesGcmCipher() {}

  /**
   * 加密并返回 {@code base64(iv):base64(ct||tag)}。
   *
   * <p>每次调用都取一段新的随机 IV。**这是 GCM 的硬性要求**：同一把密钥下重用 IV 会让 "两条密文的异或 = 两条明文的异或"成立，从而可恢复明文，且认证标签也会失效。 用
   * {@link SecureRandom} 而不是计数器：计数器需要跨进程持久化，那个状态本身就是新的故障源。
   *
   * @throws IllegalArgumentException 密钥不是 32 字节，或明文为 {@code null}
   */
  public static String encrypt(byte[] key32, String plaintext) {
    requireKey(key32);
    if (plaintext == null) {
      throw new IllegalArgumentException("待加密明文为空");
    }
    byte[] iv = new byte[IV_BYTES];
    RANDOM.nextBytes(iv);
    byte[] ciphertext =
        run(Cipher.ENCRYPT_MODE, key32, iv, plaintext.getBytes(StandardCharsets.UTF_8));
    Base64.Encoder encoder = Base64.getEncoder();
    return encoder.encodeToString(iv) + ":" + encoder.encodeToString(ciphertext);
  }

  /**
   * 解密 {@link #encrypt} 的输出，失败即抛 —— 本方法<b>不会</b>返回 {@code null} 或"尽力而为的结果"。
   *
   * <p>这一点是刻意的：调用方拿到的要么是真密钥，要么是一次显式的失败。若这里在格式不对时返回 {@code
   * null}，调用方就会退化成"没有密钥"的路径，而那条路径的表现是"用户的码永远不对"—— 数据被改过这件事会被伪装成一个用户操作问题。
   *
   * @throws IllegalStateException 格式非法、认证标签不匹配（密文或 IV 被改动）、密钥不符
   * @throws IllegalArgumentException 密钥不是 32 字节，或 {@code payload} 为 {@code null}
   */
  public static String decrypt(byte[] key32, String payload) {
    requireKey(key32);
    if (payload == null) {
      throw new IllegalArgumentException("待解密密文为空");
    }
    int separator = payload.indexOf(':');
    if (separator <= 0 || separator == payload.length() - 1) {
      throw new IllegalStateException("密文格式非法：应为 base64(iv):base64(ct||tag)");
    }
    byte[] iv;
    byte[] ciphertext;
    try {
      Base64.Decoder decoder = Base64.getDecoder();
      iv = decoder.decode(payload.substring(0, separator));
      ciphertext = decoder.decode(payload.substring(separator + 1));
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("密文格式非法：分段不是合法的 Base64", ex);
    }
    if (iv.length != IV_BYTES) {
      throw new IllegalStateException("密文格式非法：IV 应为 " + IV_BYTES + " 字节，实际 " + iv.length);
    }
    byte[] plaintext = run(Cipher.DECRYPT_MODE, key32, iv, ciphertext);
    return new String(plaintext, StandardCharsets.UTF_8);
  }

  private static byte[] run(int mode, byte[] key32, byte[] iv, byte[] input) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(mode, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      return cipher.doFinal(input);
    } catch (GeneralSecurityException ex) {
      // AEADBadTagException 落在这里：认证失败。**必须转抛**，绝不能吞掉后返回空值或原文
      // （吞掉就等于把"密文被改过"静默降级成"用一把错密钥继续跑"）。
      throw new IllegalStateException(TRANSFORMATION + " 运算失败（密文被改动或密钥不符）", ex);
    }
  }

  private static void requireKey(byte[] key32) {
    if (key32 == null || key32.length != KEY_BYTES) {
      throw new IllegalArgumentException(
          "AES-256 密钥必须恰好 " + KEY_BYTES + " 字节，实际 " + (key32 == null ? "null" : key32.length));
    }
  }
}
