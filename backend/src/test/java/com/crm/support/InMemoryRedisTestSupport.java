package com.crm.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 一个**功能性**的 Redis 替身：真存真取，语义与 {@code RedisTemplate} 一致。
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
 * getAndDelete/increment}、{@code opsForSet().add/members}、模板的 {@code delete/expire}）。
 *
 * <p>本类<b>不含</b> TTL 过期模拟与故障注入：前者要接 {@code Clock}（082 第 5 步的接缝）， 后者是 {@code MfaFailClosedIT}
 * 的输入。两者在第 6 步一并加上。
 */
public final class InMemoryRedisTestSupport {

  private final Map<String, Object> values = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> sets = new ConcurrentHashMap<>();

  /** 装到给定模板上：替换 {@code opsForValue()} / {@code opsForSet()} 并接管 {@code delete} / {@code expire}。 */
  public void install(RedisTemplate<String, Object> template) {
    // 必须先建好两个 ops 替身，再挂到 template 上。写成 `when(template.opsForValue()).thenReturn(valueOps())`
    // 会在 `when(...)` 尚未收尾时启动 valueOps() 内部的另一批打桩，Mockito 直接抛 UnfinishedStubbing。
    ValueOperations<String, Object> valueOps = valueOps();
    SetOperations<String, Object> setOps = setOps();
    when(template.opsForValue()).thenReturn(valueOps);
    when(template.opsForSet()).thenReturn(setOps);

    doAnswer(
            inv -> {
              Object removed = values.remove((String) inv.getArgument(0));
              sets.remove((String) inv.getArgument(0));
              return removed != null;
            })
        .when(template)
        .delete(anyString());

    // 只实现 delete(K)：全仓 src/main 只用这一个重载（实测 8 处，无 delete(Collection)）。
    // TTL 一律忽略（返回 true 表示"设成功"）：本类模拟的窗口是分钟级（900s/604800s），
    // 而测试在同一瞬间完成，没有任何用例会真的需要它到期。到期要靠注入 Clock，见类 javadoc。
    doAnswer(inv -> true).when(template).expire(anyString(), any(Duration.class));
  }

  private ValueOperations<String, Object> valueOps() {
    ValueOperations<String, Object> ops = mock(ValueOperations.class);

    doAnswer(
            inv -> {
              values.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(ops)
        .set(anyString(), any());

    doAnswer(
            inv -> {
              values.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(ops)
        .set(anyString(), any(), any(Duration.class));

    doAnswer(inv -> values.get((String) inv.getArgument(0))).when(ops).get(anyString());

    // 原子性原语：MFA 的一次性票据靠它。实现成"读并删"的**原子**版（本类是单线程语义，
    // 但方法形状必须与真 GETDEL 一致，否则第 9 步的调用形状断言失去意义）。
    doAnswer(inv -> values.remove((String) inv.getArgument(0))).when(ops).getAndDelete(anyString());

    doAnswer(
            inv -> {
              String key = inv.getArgument(0);
              if (values.containsKey(key)) {
                return false;
              }
              values.put(key, inv.getArgument(1));
              return true;
            })
        .when(ops)
        .setIfAbsent(anyString(), any());

    doAnswer(
            inv -> {
              String key = inv.getArgument(0);
              if (values.containsKey(key)) {
                return false;
              }
              values.put(key, inv.getArgument(1));
              return true;
            })
        .when(ops)
        .setIfAbsent(anyString(), any(), any(Duration.class));

    doAnswer(inv -> mergeCounter((String) inv.getArgument(0), 1L)).when(ops).increment(anyString());

    doAnswer(inv -> mergeCounter((String) inv.getArgument(0), (Long) inv.getArgument(1)))
        .when(ops)
        .increment(anyString(), anyLong());

    return ops;
  }

  private SetOperations<String, Object> setOps() {
    SetOperations<String, Object> ops = mock(SetOperations.class);

    doAnswer(
            inv -> {
              String key = inv.getArgument(0);
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
              Set<String> members = sets.get((String) inv.getArgument(0));
              return members == null
                  ? Set.of()
                  : members.stream().map(m -> (Object) m).collect(Collectors.toSet());
            })
        .when(ops)
        .members(anyString());

    return ops;
  }

  /**
   * 与真 Redis 的 {@code INCR} 一致：返回 {@code Long}。首次为 {@code delta}，此后累加。
   *
   * <p><b>特意存 {@code Long} 而不是字符串</b>：{@code AuthService} 读计数用的是 {@code
   * Integer.parseInt(value.toString())}，两种都能过；但 MFA 的失败计数（082 第 6 步） 要用 {@code increment}
   * 的<b>返回值</b>判断是否达阈值 —— 那里若返回 String 会 ClassCastException， 而真 Redis 返回的就是 Long。⇒
   * 类型必须与真实现一致，否则替身在关键路径上比真货更宽松。
   */
  private Long mergeCounter(String key, long delta) {
    Object updated = values.merge(key, delta, (a, b) -> ((Number) a).longValue() + delta);
    return ((Number) updated).longValue();
  }

  /** 断言用：当前所有键值（值原样；计数是 {@code Long}，refresh token 是字符串）。 */
  public Map<String, Object> snapshot() {
    return Map.copyOf(values);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void clear() {
    values.clear();
    sets.clear();
  }
}
