package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MFA 密钥的配置判据与加解密（082）。
 *
 * <p>本类直接 {@code new} 出服务对象、不经 Spring：判据是"配置字符串 → 结论"的纯函数， 不需要容器。容器里那份配置由 {@code
 * application-test.yml} 提供，另有一条启动路径的用例（{@code SecurityDefaultsGuardTest}）覆盖"谁在什么时候消费这个结论"。
 */
class MfaSecretEncryptionServiceTest {

  /** Base64 的 32 字节（明文 {@code test-mfa-key-for-082-0123456789x}），与 application-test.yml 同源。 */
  private static final String VALID = "dGVzdC1tZmEta2V5LWZvci0wODItMDEyMzQ1Njc4OXg=";

  /** Base64 的 16 字节：长度不够，应被拒（JCE 会把它当 AES-128，不是 AES-256）。 */
  private static final String SHORT_16 = "MDEyMzQ1Njc4OWFiY2RlZg==";

  /** Base64 的 31 字节：差一个字节，同样应被拒——"差不多够长"不是合格的密钥。 */
  private static final String LONG_31 = "eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eA==";

  private static final String NOT_BASE64 = "!!!这不是 Base64!!!";

  private static MfaSecretEncryptionService configuredWith(String value) {
    return new MfaSecretEncryptionService(value);
  }

  @Test
  @DisplayName("isConfigured 只回答「写没写」，不回答「写得对不对」")
  void isConfiguredOnlyAnswersWhetherItWasWritten() {
    assertFalse(configuredWith(null).isConfigured());
    assertFalse(configuredWith("").isConfigured());
    assertFalse(configuredWith("   ").isConfigured(), "全是空白等于没配");

    assertTrue(configuredWith(VALID).isConfigured());
    // 畸形但非空 —— 这里必须是 true：它会在启动时抛出（见 SecurityDefaultsGuard），
    // 若这里返回 false，调用方就会走"未配置"那条只告警的路径，误配被降级成"没配"。
    assertTrue(configuredWith(NOT_BASE64).isConfigured());
    assertTrue(configuredWith(SHORT_16).isConfigured());
  }

  @Test
  @DisplayName("合法密钥 ⇒ 无问题；首尾空白与内嵌换行不算问题（环境变量常带）")
  void configurationProblemIsEmptyForAValidKey() {
    assertTrue(configuredWith(VALID).configurationProblem().isEmpty());
    assertTrue(configuredWith("  " + VALID + "  ").configurationProblem().isEmpty(), "首尾空白应被容忍");
    assertTrue(configuredWith(VALID + "\n").configurationProblem().isEmpty(), "尾随换行应被容忍");
    assertTrue(
        configuredWith(VALID.substring(0, 20) + "\n" + VALID.substring(20))
            .configurationProblem()
            .isEmpty(),
        "折成两行的 Base64 应被容忍（Docker secret / K8s 注入常见）");
  }

  @Test
  @DisplayName("未配置 ⇒ 问题描述指向「没配」")
  void configurationProblemReportsBlank() {
    for (String blank : new String[] {null, "", "   ", "\n\t "}) {
      Optional<String> problem = configuredWith(blank).configurationProblem();
      assertTrue(problem.isPresent(), "空值必须被报出来：" + blank);
      assertTrue(problem.get().contains("未配置"), "描述应说清是「没配」：" + problem.get());
    }
  }

  @Test
  @DisplayName("非 Base64 ⇒ 问题描述指向「不是合法 Base64」")
  void configurationProblemReportsNonBase64() {
    Optional<String> problem = configuredWith(NOT_BASE64).configurationProblem();

    assertTrue(problem.isPresent());
    assertTrue(problem.get().contains("Base64"), "描述应点明编码问题：" + problem.get());
  }

  /** 长度是**解码后**算的：Base64 串的长度（44 字符）是误导性的，报错要说清这一点。 */
  @Test
  @DisplayName("长度不对 ⇒ 报出解码后的字节数与要求值（16 字节与 31 字节都拒）")
  void configurationProblemReportsWrongLength() {
    Optional<String> tooShort = configuredWith(SHORT_16).configurationProblem();
    assertTrue(tooShort.isPresent());
    assertTrue(tooShort.get().contains("16"), "应报出实际字节数：" + tooShort.get());
    assertTrue(tooShort.get().contains("32"), "应报出要求字节数：" + tooShort.get());

    Optional<String> nearly = configuredWith(LONG_31).configurationProblem();
    assertTrue(nearly.isPresent(), "差一个字节同样是误配，不该被放过");
    assertTrue(nearly.get().contains("31"));
  }

  @Test
  @DisplayName("往返：加密后不含明文，能解回原值")
  void roundTripsASecret() {
    MfaSecretEncryptionService service = configuredWith(VALID);
    String secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    String payload = service.encrypt(secret);

    assertFalse(payload.contains(secret), "密文里出现明文等于没加密");
    assertTrue(payload.contains(":"), "格式应为 base64(iv):base64(ct||tag)：" + payload);
    assertEquals(secret, service.decrypt(payload));
  }

  @Test
  @DisplayName("未配置 ⇒ encrypt/decrypt 以 MFA_SECRET_MISSING 失败（fail closed，不返回明文/空值）")
  void failsClosedWhenNotConfigured() {
    MfaSecretEncryptionService service = configuredWith("");

    BusinessException onEncrypt =
        assertThrows(BusinessException.class, () -> service.encrypt("JBSWY3DPEHPK3PXP"));
    assertEquals(ErrorCode.MFA_SECRET_MISSING, onEncrypt.getErrorCode());

    BusinessException onDecrypt =
        assertThrows(BusinessException.class, () -> service.decrypt("YWJj:Ym9keQ=="));
    assertEquals(ErrorCode.MFA_SECRET_MISSING, onDecrypt.getErrorCode());
  }

  @Test
  @DisplayName("配了但畸形 ⇒ 与未配置同一条业务失败（对调用方是同一件事）")
  void failsClosedWhenMalformed() {
    for (String malformed : new String[] {NOT_BASE64, SHORT_16, LONG_31}) {
      MfaSecretEncryptionService service = configuredWith(malformed);
      BusinessException ex =
          assertThrows(BusinessException.class, () -> service.encrypt("JBSWY3DPEHPK3PXP"));
      assertEquals(ErrorCode.MFA_SECRET_MISSING, ex.getErrorCode(), "配置问题：" + malformed);
    }
  }

  /**
   * 篡改密文抛的是 {@link IllegalStateException}（来自 {@code AesGcmCipher}），不是 {@code BusinessException}。
   *
   * <p>这一条断言就足以把两者分开：{@code BusinessException} 不继承 {@code IllegalStateException}， 所以任何"把
   * AesGcmCipher 的失败包成 MFA_SECRET_MISSING"的改法都会让本用例转红。
   * 区分的理由：前者是"数据被改过"，后者是"配置有问题"——把一次可能的数据篡改伪装成运维配错密钥， 会让排查方向整个跑偏。
   */
  @Test
  @DisplayName("篡改密文 ⇒ 抛，且是数据类失败而非配置类失败")
  void tamperedCiphertextIsRejectedAsADataFailure() {
    MfaSecretEncryptionService service = configuredWith(VALID);
    String payload = service.encrypt("JBSWY3DPEHPK3PXP");
    int separator = payload.indexOf(':');
    byte[] ciphertext = Base64.getDecoder().decode(payload.substring(separator + 1));
    ciphertext[0] ^= 0x01;

    String forged =
        payload.substring(0, separator + 1) + Base64.getEncoder().encodeToString(ciphertext);

    assertThrows(IllegalStateException.class, () -> service.decrypt(forged));
  }

  /**
   * 配置值可能是运维误粘的别的东西（私钥、口令），所以任何对外可见的文本里都不能出现它。
   *
   * <p>只断言"消息不含配置内容"是不够的——一条什么都不说的消息也能通过。故同时对每个用例断言 消息**非空**：既要报出信息，又不能报出内容。
   */
  @Test
  @DisplayName("问题描述既不回显配置内容，也不为空")
  void messagesNeverContainTheConfiguredValue() {
    String[][] samples = {
      {NOT_BASE64, "!"},
      {SHORT_16, "MDEy"},
      {LONG_31, "eHh4"},
    };

    for (String[] sample : samples) {
      MfaSecretEncryptionService service = configuredWith(sample[0]);

      Optional<String> problem = service.configurationProblem();
      assertTrue(problem.isPresent());
      assertFalse(problem.get().isBlank(), "描述不能是空串：" + sample[0]);
      assertFalse(
          problem.get().contains(sample[1]), "描述里出现了配置内容的片段 [" + sample[1] + "]：" + problem.get());

      BusinessException ex =
          assertThrows(BusinessException.class, () -> service.encrypt("JBSWY3DPEHPK3PXP"));
      assertFalse(
          ex.getMessage() == null || ex.getMessage().contains(sample[1]),
          "异常消息里出现了配置内容的片段 [" + sample[1] + "]：" + ex.getMessage());
    }
  }
}
