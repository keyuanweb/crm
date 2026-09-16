package com.crm.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 一个**功能性**的 Redis 替身：真存真取、真过期、真抛故障，语义与 {@code RedisTemplate} 对齐。
 *
 * <p><b>为什么需要它</b>：{@code AbstractIntegrationTest} 的 {@code stubRedis()} 装的是裸 {@code
 * mock(ValueOperations.class)} —— {@code set} 是空操作、{@code get} 恒为 {@code null}。 于是**任何"写进去再读回来"的路径在
 * IT 层根本跑不通**：实测 {@code POST /api/v1/auth/refresh} 的**成功分支**此前从未被任何集成测试执行过（全测试树对 {@code
 * auth/refresh} 零命中）， 就是因为 refresh 要先校验"Redis 里登记过的那个 refresh token"。
 *
 * <p><b>为什么不改 {@code AbstractIntegrationTest}</b>：那个类的 javadoc 写明了它的默认值被 74 个 IT
 * 依赖，改它会动到那些类的可观测行为（例如依赖 {@code auth:refresh:} 读到 {@code null} 的用例）。 ⇒ 本类由**需要它的 IT
 * 自己安装**，父类不动。JUnit 5 保证父类 {@code @BeforeEach} 先于子类执行， 故子类里后装的替身生效。
 *
 * <p>⚠️ <b>未实现的方法会按 Mockito 默认答案返回 {@code null}/{@code false}/{@code 0} —— 这是静默的</b>。 危险之处在于 {@code
 * setIfAbsent} 若没实现就会恒返回 {@code false}、{@code getAndDelete} 恒返回 {@code
 * null}，两者都<b>看起来像正常结果</b>（"已被占用"、"票据不存在"），足以让一个防重放用例 <b>因为错误的原因而变绿</b>。⇒ <b>主代码里新增任何 Redis
 * 调用，必须同步在下面补实现。</b> 当前已实现的是全仓 {@code src/main} 实际用到的全部方法（{@code opsForValue().get/set/setIfAbsent/
 * getAndDelete/increment}、{@code opsForSet().add/members}、模板的 {@code delete/expire/getExpire}）。
 *
 * <h2>TTL 是真的会过期的（082 第 6 步加上）</h2>
 *
 * <p>过期基准取自安装时传入的 {@link Clock}——**必须是应用自己那个 Clock bean**，否则同一个用例里 "业务认为过了 901 秒"与"Redis 认为还不到 900
 * 秒"会各说各话，而症状是"锁定期满自动恢复"这条断言怎么调都不对。 不传则退回 {@code systemDefaultZone()}。
 *
 * <p>三处<b>刻意与真 Redis 对齐</b>、写错就会让 IT 因错误原因变绿的地方：
 *
 * <ul>
 *   <li><b>{@code getExpire} 区分 {@code -2}（键不存在）与 {@code -1}（有键无 TTL）</b>：{@code
 *       MfaStateStore.lockRemainingSeconds} 正是靠这个区分判断"计数已满但窗口丢了"要不要补窗 —— 替身若一律返回 {@code
 *       -1}，那条"永久锁定洞"的用例就永远看不到补窗分支。
 *   <li><b>{@code increment} 保留已有 TTL</b>（真 Redis 的 {@code INCR} 明确不修改 TTL）：{@code AuthService}
 *       的失败计数是 {@code increment} + 另一次 {@code expire}，若替身在 {@code INCR} 时把 TTL 清掉，
 *       那两次调用之间的窗口在替身里永远不存在。
 *   <li><b>{@code expire} 在键不存在时返回 {@code false}</b>（本类此前无条件返回 {@code true}）： 真 Redis 对不存在的键返回
 *       0/false。无条件成功会让" 给一个不存在的键补 TTL"看起来成功了。
 * </ul>
 *
 * <h2>按 key 前缀注入故障（082 第 6 步加上）</h2>
 *
 * <p>{@link #failOnKeyPrefix(String)} 之后，<b>任何</b>命中该前缀的键上的一切操作（读、写、删、TTL 读写）都抛 {@link
 * RedisConnectionFailureException}——与真 Redis 挂掉时抛的是同一个类型。 这使「Redis 挂了 ⇒ 2FA fail closed」与「同一时刻非 2FA
 * 的登录路径不受影响」 能放进<b>同一个用例、同一次故障注入</b>里断言： 两次调用发生在同一个进程、同一份替身、同一秒，唯一差别就是键前缀。拆成两个用例各自注入一次的话，"非 2FA
 * 不受影响" 那条其实是在**一个没有故障的替身**上跑的，等于没验证过隔离性。
 *
 * <p><b>刻意未实现</b>（用到时再补，别默认它们存在）：{@code delete(Collection)}、{@code keys()}、管道/事务/Lua、 {@code
 * opsForZSet}/{@code opsForHash}、{@code EXPIRE} 的非正 TTL（真 Redis 会删键）、{@code getExpire} 的秒级向下取整、
 * 以及并发语义（本替身是单线程的，**它不能用来证明任何原子的东西**——原子性只能靠 {@code MfaStateStoreTest} 的调用形状断言）。
 */
public final class InMemoryRedisTestSupport {

  /** 一个键值项。{@code expiresAtMillis == null} = 不过期（真 Redis 的 {@code SET} 无 EX）。 */
  private record Entry(Object value, Long expiresAtMillis) {}

  private final Map<String, Entry> values = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> sets = new ConcurrentHashMap<>();
  private final List<String> failingPrefixes = new CopyOnWriteArrayList<>();
  private final List<Map.Entry<String, Runnable>> keyHooks = new CopyOnWriteArrayList<>();

  private Clock clock = Clock.systemDefaultZone();

  /** 装到给定模板上，过期基准用系统时钟。 */
  public void install(RedisTemplate<String, Object> template) {
    install(template, Clock.systemDefaultZone());
  }

  /**
   * 装到给定模板上：替换 {@code opsForValue()} / {@code opsForSet()} 并接管 {@code delete} / {@code expire} /
   * {@code getExpire}。
   *
   * @param clock 过期基准。**必须与应用的 {@code Clock} bean 是同一个对象**，否则"业务时间"与"Redis 时间"会分歧（见类 javadoc）。
   */
  public void install(RedisTemplate<String, Object> template, Clock clock) {
    this.clock = clock;
    // 必须先建好两个 ops 替身，再挂到 template 上。写成 `when(template.opsForValue()).thenReturn(valueOps())`
    // 会在 `when(...)` 尚未收尾时启动 valueOps() 内部的另一批打桩，Mockito 直接抛 UnfinishedStubbing。
    ValueOperations<String, Object> valueOps = valueOps();
    SetOperations<String, Object> setOps = setOps();
    when(template.opsForValue()).thenReturn(valueOps);
    when(template.opsForSet()).thenReturn(setOps);

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              boolean present = live(key) != null;
              values.remove(key);
              sets.remove(key);
              return present;
            })
        .when(template)
        .delete(anyString());

    // 只实现 delete(K)：全仓 src/main 只用这一个重载（实测 8 处，无 delete(Collection)）。
    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              Duration ttl = (Duration) inv.getArgument(1);
              if (live(key) == null) {
                return false; // 真 Redis：对不存在的键不设 TTL，返回 0
              }
              long expiresAt = now() + ttl.toMillis();
              values.computeIfPresent(key, (k, e) -> new Entry(e.value(), expiresAt));
              return true;
            })
        .when(template)
        .expire(anyString(), any(Duration.class));

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              TimeUnit unit = (TimeUnit) inv.getArgument(1);
              Entry entry = live(key);
              if (entry == null) {
                return -2L; // 键不存在
              }
              if (entry.expiresAtMillis() == null) {
                return -1L; // 有键、无 TTL
              }
              return unit.convert(entry.expiresAtMillis() - now(), TimeUnit.MILLISECONDS);
            })
        .when(template)
        .getExpire(anyString(), any(TimeUnit.class));
  }

  private ValueOperations<String, Object> valueOps() {
    ValueOperations<String, Object> ops = mock(ValueOperations.class);

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              put(key, inv.getArgument(1), null);
              return null;
            })
        .when(ops)
        .set(anyString(), any());

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              put(key, inv.getArgument(1), ((Duration) inv.getArgument(2)).toMillis());
              return null;
            })
        .when(ops)
        .set(anyString(), any(), any(Duration.class));

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              Entry entry = live(key);
              return entry == null ? null : entry.value();
            })
        .when(ops)
        .get(anyString());

    // 原子性原语：MFA 的一次性票据靠它。实现成"读并删"的**原子**版（本类是单线程语义，
    // 但方法形状必须与真 GETDEL 一致，否则第 9 步的调用形状断言失去意义）。
    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              Entry entry = live(key);
              values.remove(key);
              fireHooks(key);
              return entry == null ? null : entry.value();
            })
        .when(ops)
        .getAndDelete(anyString());

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              if (live(key) != null) {
                return false;
              }
              put(key, inv.getArgument(1), null);
              return true;
            })
        .when(ops)
        .setIfAbsent(anyString(), any());

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              if (live(key) != null) {
                return false;
              }
              put(key, inv.getArgument(1), ((Duration) inv.getArgument(2)).toMillis());
              return true;
            })
        .when(ops)
        .setIfAbsent(anyString(), any(), any(Duration.class));

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              return mergeCounter(key, 1L);
            })
        .when(ops)
        .increment(anyString());

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              return mergeCounter(key, (Long) inv.getArgument(1));
            })
        .when(ops)
        .increment(anyString(), anyLong());

    return ops;
  }

  private SetOperations<String, Object> setOps() {
    SetOperations<String, Object> ops = mock(SetOperations.class);

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              Set<String> members = sets.computeIfAbsent(key, k -> new LinkedHashSet<>());
              long added = 0;
              for (int i = 1; i < inv.getArguments().length; i++) {
                if (members.add(String.valueOf(inv.getArgument(i)))) {
                  added++;
                }
              }
              return added;
            })
        .when(ops)
        .add(anyString(), any());

    doAnswer(
            inv -> {
              String key = (String) inv.getArgument(0);
              guard(key);
              Set<String> members = sets.get(key);
              return members == null
                  ? Set.of()
                  : members.stream().map(m -> (Object) m).collect(Collectors.toSet());
            })
        .when(ops)
        .members(anyString());

    return ops;
  }

  // ===== 故障注入 =====

  /**
   * 此后任何命中该前缀的键，其一切操作都抛 {@link RedisConnectionFailureException}。
   *
   * <p>前缀要选得**只覆盖被测的那一族**：2FA 用 {@code "auth:2fa-"}（这与 {@code AuthService} 的 {@code "auth:fail:"}
   * 不重叠，故"2FA 挂了、普通登录照常"可以在同一次注入下判定）。
   */
  public void failOnKeyPrefix(String prefix) {
    failingPrefixes.add(prefix);
  }

  /** 撤掉全部故障注入。{@link #clear()} 也会调用它。 */
  public void healAll() {
    failingPrefixes.clear();
  }

  // ===== 一次性副作用钩子 =====

  /**
   * 在命中该前缀的键被 {@code getAndDelete} 取走之后、返回之前，执行一次这个回调（用完即弃）。
   *
   * <p><b>为什么需要它</b>：{@code MfaVerificationService.verify} 的 ①（读用户）与 ⑤（消费票据后重读用户）
   * 之间隔着验码与消费票据，两次读到的可能是**不同的行**。要断言 ⑤ 的重读确实发生，就必须让"行在这两次读之间被改" 真的发生一次——而 MockMvc
   * 是**同步**的，用例没有任何办法从外面插进这个窗口。
   *
   * <p>钩在 {@code getAndDelete} 上而不是别的原语上：④（{@code consumeTicket}）正好落在那个窗口里，
   * 且它是这条流程里**唯一**一次票据读删，故触发点既精确又不歧义。
   *
   * <p>回调**只执行一次**：留着的话同一用例里后续的消费会反复触发，把"这次变更是谁引起的"搅浑。 与故障注入一样，{@link #clear()} 会把它一并清掉（不跨用例泄漏）。
   */
  public void onGetAndDeleteKeyPrefix(String prefix, Runnable action) {
    keyHooks.add(Map.entry(prefix, action));
  }

  /** 触发并**移除**命中的钩子（一次性）。 */
  private void fireHooks(String key) {
    for (Map.Entry<String, Runnable> hook : keyHooks) {
      if (key.startsWith(hook.getKey()) && keyHooks.remove(hook)) {
        hook.getValue().run();
      }
    }
  }

  private void guard(String key) {
    if (key == null) {
      return;
    }
    for (String prefix : failingPrefixes) {
      if (key.startsWith(prefix)) {
        throw new RedisConnectionFailureException("注入的故障：键 " + key + " 命中前缀 " + prefix);
      }
    }
  }

  // ===== 内部 =====

  private long now() {
    return clock.millis();
  }

  /** 取一个**未过期**的项；已过期则顺手移除（真 Redis 的惰性删除）并当作不存在。 */
  private Entry live(String key) {
    Entry entry = values.get(key);
    if (entry == null) {
      return null;
    }
    if (entry.expiresAtMillis() != null && now() >= entry.expiresAtMillis()) {
      values.remove(key, entry);
      return null;
    }
    return entry;
  }

  private void put(String key, Object value, Long ttlMillis) {
    values.put(key, new Entry(value, ttlMillis == null ? null : now() + ttlMillis));
  }

  /**
   * 与真 Redis 的 {@code INCR} 一致：返回 {@code Long}，且<b>不修改已有 TTL</b>。
   *
   * <p><b>特意存 {@code Long} 而不是字符串</b>：{@code AuthService} 读计数用的是 {@code
   * Integer.parseInt(value.toString())}，两种都能过；但 MFA 的失败计数（082 第 6 步） 要用 {@code increment}
   * 的<b>返回值</b>判断是否达阈值 —— 那里若返回 String 会 ClassCastException， 而真 Redis 返回的就是 Long。⇒
   * 类型必须与真实现一致，否则替身在关键路径上比真货更宽松。
   */
  private Long mergeCounter(String key, long delta) {
    Entry current = live(key);
    long base = current == null ? 0L : ((Number) current.value()).longValue();
    // TTL 原样带走：真 Redis 的 INCR 不会给键续期，也不会把 TTL 清掉。
    values.put(key, new Entry(base + delta, current == null ? null : current.expiresAtMillis()));
    return base + delta;
  }

  /** 断言用：当前所有**未过期**的键值（值原样；计数是 {@code Long}，refresh token 是字符串）。 */
  public Map<String, Object> snapshot() {
    Map<String, Object> live = new LinkedHashMap<>();
    for (String key : values.keySet()) {
      Entry entry = live(key);
      if (entry != null) {
        live.put(key, entry.value());
      }
    }
    return Map.copyOf(live);
  }

  public boolean containsKey(String key) {
    return live(key) != null;
  }

  /** 回到干净状态：清空数据**并撤掉故障注入与钩子**（两者都不跨用例泄漏）。 */
  public void clear() {
    values.clear();
    sets.clear();
    keyHooks.clear();
    healAll();
  }
}
