package com.crm.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 进程内缓存配置（083-engineering-consolidation）。
 *
 * <p>必须为<b>进程内</b>实现：集成测试把 {@code RedisTemplate} 声明为测试替身，用外部缓存会使缓存行为在测试中静默失效、 无法被任何测试验证（research.md
 * §4）。
 *
 * <p>不使用 {@code @Cacheable} / {@code @CacheEvict} 注解：同类内部的自调用不经过代理，注解会静默不生效。此处只提供缓存 容器，失效点由 Service
 * 层显式调用，便于审计（research.md §4）。
 */
@Configuration
public class CacheConfig {

  /** 角色权限缓存：键为角色编码，值为权限码列表。TTL 取规格允许的上限 60 秒。 */
  public static final String ROLE_PERMISSIONS_CACHE = "rolePermissions";

  /**
   * 可见数据范围缓存：键为用户 id，值为可见负责人 id 列表。
   *
   * <p>安全敏感——其值取决于<b>部门成员集合</b>，成员变动无法从该用户自身的写操作推断，故任何用户或部门的写操作都必须 <b>全量失效</b>本缓存。TTL 取 30
   * 秒（短于角色权限缓存）：陈旧代价是越权读到他人数据（data-model.md §3）。
   */
  public static final String VISIBLE_OWNER_IDS_CACHE = "visibleOwnerIds";

  /**
   * 商机阶段字典缓存（1.2-stage-configurable）：键为固定值，值为整张 {@code opportunity_stage} 表的有序列表。
   *
   * <p>本表只有个位数行、读远多于写，故整体缓存一份而不是按查询分别缓存。TTL 与角色权限缓存同取 60 秒； 阶段本身几乎不变，且所有写路径都显式 {@code
   * evict}，故陈旧窗口只在下游直接改库时才会出现。
   */
  public static final String OPPORTUNITY_STAGES_CACHE = "opportunityStages";

  /** 角色权限缓存存活时间。上限由 spec.md「关键实体」约束：不得超过 60 秒。 */
  public static final Duration ROLE_PERMISSIONS_TTL = Duration.ofSeconds(60);

  /** 可见数据范围缓存存活时间。上限同上，取更短值以压低越权窗口。 */
  public static final Duration VISIBLE_OWNER_IDS_TTL = Duration.ofSeconds(30);

  private static final long ROLE_PERMISSIONS_MAX_SIZE = 1_000L;

  private static final long VISIBLE_OWNER_IDS_MAX_SIZE = 10_000L;

  /** 阶段字典只有一个键，16 的上限纯属留白。 */
  private static final long OPPORTUNITY_STAGES_MAX_SIZE = 16L;

  @Bean
  public CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(
        List.of(
            caffeineCache(ROLE_PERMISSIONS_CACHE, ROLE_PERMISSIONS_TTL, ROLE_PERMISSIONS_MAX_SIZE),
            caffeineCache(
                OPPORTUNITY_STAGES_CACHE, ROLE_PERMISSIONS_TTL, OPPORTUNITY_STAGES_MAX_SIZE),
            caffeineCache(
                VISIBLE_OWNER_IDS_CACHE, VISIBLE_OWNER_IDS_TTL, VISIBLE_OWNER_IDS_MAX_SIZE)));
    return manager;
  }

  private static CaffeineCache caffeineCache(String name, Duration ttl, long maxSize) {
    return new CaffeineCache(
        name, Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build());
  }
}
