package com.crm.security;

/**
 * 限流的身份维度（100-rate-limit-consolidation）：一个请求「算谁的」。
 *
 * <p>{@link #AUTO} 的判定顺序（实现见 {@link RateLimitIdentity#keyOf}，纯静态函数，可脱 Spring 单测）： <b>机器主体 → {@code
 * key:<keyId>}；其余已认证 → {@code user:<userId>}；未认证 → {@code ip:<客户端 IP>}</b>。
 *
 * <p>顺序的理由：机器主体（API Key）的 {@code userId} 是<b>密钥创建者</b> （{@code ApiKeyAuthFilter} 注入的是 {@code
 * key.getCreatedBy()}），密钥 id 另在 {@code authentication.getDetails()} 里。按 {@code userId}
 * 分桶会让同一个管理员创建的<b>多个密钥共用一个桶</b> ——一个密钥打满、其余全部被拒，而症状是静默的：每个密钥单独看都「没超限」，只有合起来看才看得出。
 *
 * <p>显式写 {@link #USER} / {@link #API_KEY} 而请求<b>不具备</b>该身份时，回退 IP 并记一条 warn：
 * 当端点声明了某个维度而请求偏偏没有那个主体时，宁可退化成「按来源分桶」也不要静默不限流——静默不限流 正是本机制最难发现的那类失效（客户端行为一字不变，只是护栏不见了）。
 */
public enum RateLimitDimension {
  /** 自动判定：机器主体→keyId、已认证→userId、匿名→IP（顺序见类 javadoc）。 */
  AUTO,
  /**
   * 按客户端 IP 分桶。**只能这样分桶的端点**（匿名）也正是伪造 {@code X-Forwarded-For} 能绕开的那些，见 {@link ClientIpResolver}。
   */
  IP,
  /** 按登录用户分桶；无登录主体时回退 IP。 */
  USER,
  /** 按 API Key 分桶；非机器主体时回退 IP。 */
  API_KEY
}
