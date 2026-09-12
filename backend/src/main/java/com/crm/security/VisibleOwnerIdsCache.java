package com.crm.security;

import com.crm.config.CacheConfig;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * "可见负责人 id 集合"的进程内缓存访问点（083-engineering-consolidation，FR-G27）。
 *
 * <p><b>为什么单独抽一个组件</b>：该缓存的失效点**不在**它的读取方（{@code DataPermissionService}）里，而在用户与部门的 写入方（{@code
 * UserService}／{@code DepartmentService}）里。若把读写方法直接放在 {@code DataPermissionService} 上，{@code
 * DepartmentService} 就要反向依赖它——而 {@code DataPermissionService} <b>已经</b>依赖 {@code
 * DepartmentService}（解析 DEPT_AND_CHILD 需要部门树），两者互相依赖会使上下文启动失败。把访问点抽成无依赖的 组件后，三方都只依赖它，依赖图保持无环。
 *
 * <p><b>为什么是全量失效而不是按键失效</b>（安全不变式，data-model.md §3／§5.2）：缓存值是"某用户可见的负责人集合"，它取决于
 * <b>部门成员集合</b>——也就是说，A 的缓存值会被 B 的写入改变（B 调入/调出 A 所在部门、部门被移动或删除）。按"谁被改就失效谁"实现 时，B 的写入只会失效 B 自己的键，A
 * 的陈旧集合继续生效：A 会看不到新同事的数据，或**继续看到已调出同事的数据**—— 后者即越权读取。故本类只提供 {@link #evictAll()}
 * 一种失效粒度，不提供按键失效，避免调用方选错。
 *
 * <p>TTL（{@code CacheConfig.VISIBLE_OWNER_IDS_TTL}，30 秒）是漏失效时的兜底上限，不是正常路径的失效手段。
 */
@Component
public class VisibleOwnerIdsCache {

  private final CacheManager cacheManager;

  public VisibleOwnerIdsCache(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  /**
   * 读取缓存。
   *
   * @return 命中返回集合（可能是表示"不过滤"的空集合）；未命中或键为 {@code null} 返回 {@code null}
   */
  public List<Long> get(Long userId) {
    if (userId == null) {
      return null;
    }
    Cache cache = cacheManager.getCache(CacheConfig.VISIBLE_OWNER_IDS_CACHE);
    if (cache == null) {
      return null;
    }
    Cache.ValueWrapper wrapper = cache.get(userId);
    if (wrapper == null) {
      return null;
    }
    Object value = wrapper.get();
    if (!(value instanceof List<?> list)) {
      // 类型不符（理论上不会发生）：当作未命中，交由调用方查库，绝不把未知对象当集合返回
      return null;
    }
    @SuppressWarnings("unchecked")
    List<Long> ownerIds = (List<Long>) list;
    return ownerIds;
  }

  /** 回写缓存；键为 {@code null} 时跳过（不缓存"无主体"场景）。 */
  public void put(Long userId, List<Long> ownerIds) {
    if (userId == null || ownerIds == null) {
      return;
    }
    Cache cache = cacheManager.getCache(CacheConfig.VISIBLE_OWNER_IDS_CACHE);
    if (cache != null) {
      cache.put(userId, ownerIds);
    }
  }

  /**
   * 全量失效（任何用户或部门写操作后必须调用）。
   *
   * <p>调用方：{@code UserService} 的新增/编辑/设置数据权限/改密，{@code DepartmentService} 的新增/编辑/删除。
   * <b>宁可多失效</b>：多失效的代价是一次查库，少失效的代价是越权读到他人数据。
   */
  public void evictAll() {
    Cache cache = cacheManager.getCache(CacheConfig.VISIBLE_OWNER_IDS_CACHE);
    if (cache != null) {
      cache.clear();
    }
  }
}
