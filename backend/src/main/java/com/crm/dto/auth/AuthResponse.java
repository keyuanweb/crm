package com.crm.dto.auth;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应：access + refresh 令牌与用户信息；已启用 2FA 的用户改走 {@code mfaRequired} 分支（082）。
 *
 * <p><b>FR-M14（零回归）靠什么成立</b>：本类在 082 之前只有前三个字段，那时的登录响应是 {@code
 * {"accessToken":…,"refreshToken":…,"user":…}}。本批新增的三个字段在<b>正常登录路径上恒为 {@code null}</b>，而本仓开了 {@code
 * spring.jackson.default-property-inclusion: non_null} （{@code application.yml}）⇒ <b>{@code null}
 * 字段整个不出现在 JSON 里</b>，序列化结果与 082 之前 <b>逐字节相同</b>。
 *
 * <p>⚠️ <b>这条保证依赖那条全局配置</b>。若有人把 {@code default-property-inclusion} 改成 {@code always}，登录响应会凭空多出
 * {@code "mfaRequired":null,"mfaToken":null,"expiresIn":null} 三个键 —— 破坏 FR-M14，且<b>不会有任何编译期提示</b>。
 * 这一点由 {@code AuthResponseSerializationTest}（字面量全等）与 {@code LoginResponseShapeIT}（HTTP 层 {@code
 * data} 键集恰为 3 个）两条断言守住， 它们是这条保证的<b>唯一</b>防线。
 *
 * <p>⚠️ 不要给本类的字段加 Lombok 默认值（如 {@code private Boolean mfaRequired = false;}）—— 那会让"正常路径"也序列化出
 * {@code "mfaRequired":false}，同样破坏 FR-M14。 这正是上面那条定向破坏里的一条。
 */
@Data
@NoArgsConstructor
public class AuthResponse {

  private String accessToken;
  private String refreshToken;
  private UserInfo user;

  /**
   * 密码阶段通过、但账号已启用 2FA：需二次验证。
   *
   * <p>只在 {@code mfaRequired} 分支为 {@code true}；其余路径为 {@code null}（被省略）。
   */
  private Boolean mfaRequired;

  /** 一次性二次验证票据（不透明随机串，<b>不是 JWT</b>）。只在 {@code mfaRequired} 分支出现。 */
  private String mfaToken;

  /**
   * {@link #mfaToken} 的存活秒数。
   *
   * <p><b>本字段语义唯一</b>：只在 {@code mfaRequired} 分支出现，指的是<b>票据</b>的 TTL，
   * <b>不是</b>访问令牌的有效期。令牌自身的有效期由既有机制表达，不在本响应里重复 （立项契约原文在 {@code verify} 里也放了一个 {@code expiresIn:
   * 3600}，与这里的 300 同名不同义， 已订正为「只有这一处」——见 {@code contracts/auth-mfa.md} 的订正块 §三）。
   */
  private Integer expiresIn;

  /** 正常路径：签发令牌。 */
  public AuthResponse(String accessToken, String refreshToken, UserInfo user) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.user = user;
  }

  /**
   * 二次验证分支：只回票据，不回任何令牌。
   *
   * <p>用静态工厂而不是第二个公开构造器，是为了让"这一支不该有令牌"成为<b>结构上不可能</b>—— 调用方没有任何途径在这条路径上顺手塞进一个 {@code accessToken}。
   */
  public static AuthResponse mfaChallenge(String mfaToken, int expiresInSeconds) {
    AuthResponse response = new AuthResponse();
    response.mfaRequired = Boolean.TRUE;
    response.mfaToken = mfaToken;
    response.expiresIn = expiresInSeconds;
    return response;
  }
}
