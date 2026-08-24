package com.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户状态缓存（FR-H05/06/07）：缓存 enabled + tokenVersion，TTL 30 秒。
 *
 * <p>JwtAuthFilter 优先读缓存，未命中时查 DB 并回写；用户状态变更（停用/改密/重置密码） 时主动 evict。所有 Redis 操作 try-catch，Redis
 * 不可用时降级为直接查 DB（fail-open）。
 *
 * <p>使用专用 StringRedisTemplate（JSON 字符串存储），与全局对象序列化器解耦： 避免类型包装（WRAPPER_ARRAY）带来的格式脆弱性与旧数据不兼容。
 */
@Component
public class UserStateCache {

  private static final Logger log = LoggerFactory.getLogger(UserStateCache.class);
  private static final String KEY_PREFIX = "auth:user-state:";
  private static final Duration TTL = Duration.ofSeconds(30);

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public UserStateCache(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /** 从缓存读取用户状态；未命中或 Redis 异常返回 null。 */
  public UserState get(Long userId) {
    if (userId == null) {
      return null;
    }
    try {
      String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
      if (json == null) {
        return null;
      }
      return objectMapper.readValue(json, UserState.class);
    } catch (Exception ex) {
      log.debug("Failed to read user state cache for userId={}: {}", userId, ex.getMessage());
      return null;
    }
  }

  /** 写入用户状态到缓存（TTL 30 秒）；Redis 异常时静默忽略。 */
  public void put(Long userId, UserState state) {
    if (userId == null || state == null) {
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(state);
      stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, json, TTL);
    } catch (Exception ex) {
      log.debug("Failed to write user state cache for userId={}: {}", userId, ex.getMessage());
    }
  }

  /** 主动失效缓存（用户状态变更时调用）；Redis 异常时静默忽略。 */
  public void evict(Long userId) {
    if (userId == null) {
      return;
    }
    try {
      stringRedisTemplate.delete(KEY_PREFIX + userId);
    } catch (Exception ex) {
      log.debug("Failed to evict user state cache for userId={}: {}", userId, ex.getMessage());
    }
  }

  /** 缓存的用户状态：enabled + tokenVersion。 */
  public record UserState(boolean enabled, int tokenVersion) {}
}
