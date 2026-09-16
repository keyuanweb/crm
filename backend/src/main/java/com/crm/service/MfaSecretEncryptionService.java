package com.crm.service;

import com.crm.common.AesGcmCipher;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * TOTP 密钥的静态加密与配置校验（082-two-factor-auth）。
 *
 * <p><b>这一层存在的理由是"把配置怎么来"与"密码学怎么做"分开</b>：{@link AesGcmCipher} 只认 32 字节 原始密钥、不认
 * Base64、不读配置，因此它可以被直接测（见 {@code AesGcmCipherTest}）。而"环境变量有没有配、 配得对不对、错了该在什么时候报"是另一件事，落在本类。
 *
 * <p><b>两种错法，两种处置</b>（判据在本类的 {@link #configurationProblem()} 里，消费方各有各的动作）：
 *
 * <table border="1">
 *   <caption>配置状态与后果</caption>
 *   <tr><th>状态</th><th>启动时（{@code SecurityDefaultsGuard}）</th><th>调用时（本类）</th></tr>
 *   <tr><td>空白</td><td>告警，继续启动</td><td>{@code MFA_SECRET_MISSING}</td></tr>
 *   <tr><td>非 Base64 / 非 32 字节</td><td><b>抛出，拒绝启动</b></td><td>{@code MFA_SECRET_MISSING}</td></tr>
 *   <tr><td>合法</td><td>无</td><td>正常</td></tr>
 * </table>
 *
 * <p><b>为什么"畸形"要拒绝启动，而"未配置"不</b>：畸形是误配——它在启动时没有任何症状， 要等某个人真的去绑定认证器时才炸，而那一刻的错误现场是"某个人绑不上"，排查得从配置查起。
 * "未配置"则是一个正常状态（2FA 是可选功能，很多部署不用它），拒绝启动等于用一个没人开启的功能炸掉整个服务。
 *
 * <p><b>两种状态在调用时都归到 {@code MFA_SECRET_MISSING}</b>：对登录/绑定流程的调用方而言它们是
 * 同一件事——"服务端现在做不了这个操作"，区分它们只对运维有意义，而那个场景由启动检查承担。 刻意<b>不</b>报成 400 系的错误码：用户没有做错任何事。
 *
 * <p><b>密钥不进日志、不进响应、不进异常消息</b>：配置值可能是运维误粘的别的东西（私钥、口令）， 所以本类只报**长度与形态**（"解码后 N 字节"、"不是合法
 * Base64"），从不回显配置内容或其片段。 这与 {@code TotpGenerator.decodeSecret} 的回显策略（回显的是抹掉后的形状）是同一个考虑。
 */
@Service
public class MfaSecretEncryptionService {

  /**
   * 配置值的原样保留。
   *
   * <p>存原字符串而<b>不</b>在字段里缓存"解码好的 32 字节"：解码是一次 32 字节的 Base64 运算， 而 2FA
   * 的调用频率是"每人每次登录一次"。缓存只会引入一个"什么时候失效"的问题，收益为零。
   */
  private final String configuredKey;

  public MfaSecretEncryptionService(
      @Value("${crm.security.mfa.secret-key:}") String configuredKey) {
    this.configuredKey = configuredKey;
  }

  /** 配置是否非空白。只回答"有没有写"，不回答"写得对不对"——后者见 {@link #configurationProblem()}。 */
  public boolean isConfigured() {
    return configuredKey != null && !configuredKey.isBlank();
  }

  /**
   * 配置问题的可读描述；{@link Optional#empty()} 表示配置可用。
   *
   * <p>返回描述而不是抛异常，是因为调用方对同一个判据有两种动作（启动时抛、调用时转业务异常）， 而<b>判据只该有一处</b>。若本方法抛业务异常、再由启动检查去 catch
   * 它，就会出现"启动检查依赖 一个 HTTP 语义的异常类型"这种耦合。
   *
   * <p>描述里<b>不含</b>配置内容：见类 javadoc。
   */
  public Optional<String> configurationProblem() {
    if (!isConfigured()) {
      return Optional.of("未配置 crm.security.mfa.secret-key（环境变量 MFA_SECRET_KEY）");
    }
    byte[] key;
    try {
      // 先抹掉所有空白再严格解码，与 TotpGenerator.decodeSecret 同一个处理：环境变量、Docker secret
      // 与 K8s 注入的值经常带尾随换行，而 Base64.getDecoder() 不容忍换行。刻意用严格解码器而不是
      // getMimeDecoder()——后者会静默忽略字母表外的字符，那正是 082 在 commons-codec 上踩过的坑
      // （见 TotpGenerator.decodeSecret 的 javadoc），在这里重演等于让一个抄错的密钥悄悄变成另一把。
      key = Base64.getDecoder().decode(configuredKey.replaceAll("\\s", ""));
    } catch (IllegalArgumentException ex) {
      return Optional.of(
          "crm.security.mfa.secret-key 不是合法的 Base64。应为 `openssl rand -base64 32` 的输出（44 字符，末尾一个 =）");
    }
    if (key.length != AesGcmCipher.KEY_BYTES) {
      return Optional.of(
          "crm.security.mfa.secret-key 解码后为 "
              + key.length
              + " 字节，AES-256 要求恰好 "
              + AesGcmCipher.KEY_BYTES
              + " 字节（Base64 前 32 字节对应的明文长度）。"
              + "注意长度是**解码后**算的：44 个字符的 Base64 串才是 32 字节");
    }
    return Optional.empty();
  }

  /**
   * 加密一个 TOTP 密钥（Base32 字符串），返回 {@code base64(iv):base64(ct||tag)}。
   *
   * @throws BusinessException {@code MFA_SECRET_MISSING} 密钥未配置或畸形
   */
  public String encrypt(String plaintext) {
    return AesGcmCipher.encrypt(requireKey(), plaintext);
  }

  /**
   * 解密 {@link #encrypt} 的输出。
   *
   * <p>密文被改动时抛的是 {@link IllegalStateException}（来自 {@link AesGcmCipher}），刻意<b>不</b> 转成 {@code
   * MFA_SECRET_MISSING}：那是"数据被改过"，不是"配置有问题"。把它伪装成配置问题会让 一次可能的数据篡改看起来像运维配错了密钥。
   *
   * @throws BusinessException {@code MFA_SECRET_MISSING} 密钥未配置或畸形
   * @throws IllegalStateException 密文格式非法或认证失败（密钥不符 / 密文被改动）
   */
  public String decrypt(String payload) {
    return AesGcmCipher.decrypt(requireKey(), payload);
  }

  /**
   * 取出 32 字节原始密钥。
   *
   * @throws BusinessException {@code MFA_SECRET_MISSING}
   */
  private byte[] requireKey() {
    Optional<String> problem = configurationProblem();
    if (problem.isPresent()) {
      throw new BusinessException(ErrorCode.MFA_SECRET_MISSING, problem.get());
    }
    return Base64.getDecoder().decode(configuredKey.replaceAll("\\s", ""));
  }
}
