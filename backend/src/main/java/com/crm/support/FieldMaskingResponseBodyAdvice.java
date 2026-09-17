package com.crm.support;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 内置字段掩码的**单一收口点**（102-builtin-field-permission）：出参序列化前，把当前角色不可见（HIDDEN）的内置字段 就地置 {@code null}。
 *
 * <h2>为什么收口在序列化这一层，而不是逐装配点调用</h2>
 *
 * <p>仓里此前**不存在任何出参收口点**，而请求→响应 DTO 的转换是 **53 个文件**里的手写 {@code toResponse} / {@code copyToResponse}
 * / {@code fillResponse}（含 {@code CustomerPoolService} 里那份**第二份**客户装配）。 逐点接入等于**再造 53
 * 个可被遗忘的点**；收口在序列化前只加**一个**必须被维护的位置，且天然覆盖新建的装配点。
 *
 * <h2>遍历规则（与「按实体判定、不向子对象传播」绑定）</h2>
 *
 * <ul>
 *   <li>拆信封：{@link ApiResponse#getData()}、{@link PageResult#getItems()}；再递归 {@code Collection} /
 *       {@code Map.values()} / 对象数组。
 *   <li>命中**载体**（{@link BuiltinFieldRegistry#entityTypeOf}）⇒ 取**该载体自己所属实体**的掩码集合置 null。 两个 {@code
 *       *DetailResponse} 子类靠 {@code isInstance} 覆盖。**不向子对象传播**——{@code ContactResponse} 的 {@code
 *       phone}/{@code email} 属 CONTACT、{@code SalesOpportunityResponse} 的 {@code amount} 属
 *       SALES_OPPORTUNITY，都不受 CUSTOMER/OPPORTUNITY 的配置影响（否则误伤，且「金额已过滤」会被误读成 {@code amount} 也不可见）。
 *   <li>JDK 类型/标量（{@code java.*}、{@code String}、数值、枚举、{@code byte[]} 之类）直接跳过：既省开销， 也避开 {@code
 *       InaccessibleObjectException}。
 *   <li>身份集（{@link IdentityHashMap}）挡环 + 深度上限兜底。
 *   <li>**不反射非载体对象**：载体在本仓都是叶子 DTO，今天的响应图里不存在「载体套载体」；为一个不存在的形态 去反射每个 DTO 只会引入 {@code
 *       setAccessible}、JDK 类型与 record 的风险。这是一条**如实登记的边界** （102 债务 5）：将来若某个响应把载体嵌在非载体对象里，掩码会漏。
 * </ul>
 *
 * <h2>代价（有意）</h2>
 *
 * <ul>
 *   <li>掩码形态是**置 null 而非删键**：不引入全局 {@code @JsonInclude}（那会改动全仓所有响应体的形状）。置 null 与「无值」**不可区分 ⇒
 *       无存在性侧信道**；代价是前端类型必须允许 null（债务 6）。
 *   <li>{@code 无主体 ⇒ ADMIN} 走快路径：沿 056 的既有 fail-open 口径，**不改**（匿名端点今天不返回任何注册载体）。
 *   <li>就地置 null 会改写对象本身：今天三个手工缓存都不存出参 DTO（**证据排除**，见 102 的 research.md §13），
 *       但**没有任何端到端判据看着这条结构性风险**——如实记为 D14，不写成「已验证无风险」。
 * </ul>
 */
@ControllerAdvice
public class FieldMaskingResponseBodyAdvice implements ResponseBodyAdvice<Object> {

  /** 深度上限：正常响应 ≤4 层（信封→items→载体），给嵌套留余量，同时兜住病态/环状对象图。 */
  private static final int MAX_DEPTH = 8;

  private final FieldMaskPlanner planner;
  private final BuiltinFieldRegistry registry;

  public FieldMaskingResponseBodyAdvice(FieldMaskPlanner planner, BuiltinFieldRegistry registry) {
    this.planner = planner;
    this.registry = registry;
  }

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    // 全部响应都过一遍：判据是「有没有注册载体」，不是「哪个控制器」——按控制器挑选会把新控制器默认漏掉
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    String roleCode = planner.currentRole();
    if (body == null || "ADMIN".equals(roleCode)) {
      return body; // 快路径：ADMIN 在 permissionFor 里恒 EDITABLE，连遍历都省掉
    }
    mask(body, new MaskContext(roleCode), 0);
    return body;
  }

  private void mask(Object node, MaskContext context, int depth) {
    if (node == null || depth > MAX_DEPTH) {
      return;
    }
    if (isJdkLeaf(node)) {
      return;
    }
    if (context.seen.put(node, Boolean.TRUE) != null) {
      return; // 同一个对象第二次遇到（含环状对象图）
    }
    if (node instanceof ApiResponse<?> api) {
      mask(api.getData(), context, depth + 1);
      return;
    }
    if (node instanceof PageResult<?> page) {
      mask(page.getItems(), context, depth + 1);
      return;
    }
    if (node instanceof Collection<?> items) {
      for (Object item : items) {
        mask(item, context, depth + 1);
      }
      return;
    }
    if (node instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        mask(value, context, depth + 1);
      }
      return;
    }
    if (node.getClass().isArray()) {
      if (!node.getClass().getComponentType().isPrimitive()) {
        for (Object item : (Object[]) node) {
          mask(item, context, depth + 1);
        }
      }
      return;
    }
    String entityType = registry.entityTypeOf(node);
    if (entityType != null) {
      registry.nullify(node, context.planFor(entityType));
    }
  }

  /**
   * 标量与 JDK 类型：跳过。
   *
   * <p>容器（{@code Collection}/{@code Map}/数组）**不算**叶子——它们的类名同样以 {@code java.} 开头， 若照名字一刀切会把 {@code
   * ArrayList} 一起跳过，于是列表页整段漏掩码（正是 D3 要杀的那种漏）。
   */
  private static boolean isJdkLeaf(Object node) {
    Class<?> type = node.getClass();
    if (Collection.class.isAssignableFrom(type)
        || Map.class.isAssignableFrom(type)
        || type.isArray()) {
      return false;
    }
    return node instanceof String
        || node instanceof Number
        || node instanceof Boolean
        || node instanceof Character
        || node instanceof Enum<?>
        || type.getName().startsWith("java.");
  }

  /** 单次响应内的遍历状态：角色 + 已算过的掩码（按实体缓存）+ 已访问对象集。 */
  private final class MaskContext {

    private final String roleCode;
    private final Map<String, Set<String>> plans = new LinkedHashMap<>();
    private final Map<Object, Boolean> seen = new IdentityHashMap<>();

    MaskContext(String roleCode) {
      this.roleCode = roleCode;
    }

    /** 该实体在本角色下的掩码。**按实体复用一次**：列表页有 N 行载体，若不缓存就会查 N 次库。 */
    Set<String> planFor(String entityType) {
      return plans.computeIfAbsent(entityType, key -> planner.plan(roleCode, key));
    }
  }
}
