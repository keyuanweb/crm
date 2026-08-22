package com.crm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 工具：签发与解析 HS256 令牌（research.md R1）。 */
@Component
public class JwtUtil {

  private final SecretKey key;
  private final long accessTtlSeconds;
  private final long refreshTtlSeconds;

  public JwtUtil(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-ttl-seconds}") long accessTtlSeconds,
      @Value("${jwt.refresh-token-ttl-seconds}") long refreshTtlSeconds) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtlSeconds = accessTtlSeconds;
    this.refreshTtlSeconds = refreshTtlSeconds;
  }

  public String generateAccessToken(Long userId, String username, String role, int tokenVersion) {
    return generate(userId, username, role, tokenVersion, accessTtlSeconds);
  }

  public String generateRefreshToken(Long userId, String username, String role, int tokenVersion) {
    return generate(userId, username, role, tokenVersion, refreshTtlSeconds);
  }

  public long accessTtlSeconds() {
    return accessTtlSeconds;
  }

  public long refreshTtlSeconds() {
    return refreshTtlSeconds;
  }

  private String generate(
      Long userId, String username, String role, int tokenVersion, long ttlSeconds) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + ttlSeconds * 1000);
    return Jwts.builder()
        .subject(username)
        .claim("userId", userId)
        .claim("role", role)
        .claim("tv", tokenVersion)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key)
        .compact();
  }

  /** 解析并校验令牌；无效/过期抛出异常。 */
  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  /** 仅取密钥（供过滤器使用）。 */
  public Key signingKey() {
    return key;
  }
}
