package com.crm.dto.auth;

/**
 * 一次二次验证的发起凭据：票据本身 + 它的存活秒数（082，FR-M07）。
 *
 * <p>与 {@link AuthResponse#mfaChallenge} 的分工：那是<b>协议响应</b>的形状（三个字段，
 * 且结构上不允许带令牌），本类是<b>服务之间</b>的传值——{@code MfaChallengeService} 产出一个、 {@code AuthService.login}
 * 消费一个。合成一个类型的话，服务层就得先造一个响应对象再把它拆开。
 *
 * <p>{@code expiresInSeconds} 与票据的 Redis TTL 同源（同一份配置、同一次调用）， 不各自计算——两处各算一次就会出现"响应说 300 秒、Redis 里其实
 * 240 秒"这种只有用户能发现的偏差。
 *
 * @param token 不透明随机串，不是 JWT
 * @param expiresInSeconds 票据有效期，语义唯一（见 {@link AuthResponse#getExpiresIn()}）
 */
public record MfaChallenge(String token, int expiresInSeconds) {}
