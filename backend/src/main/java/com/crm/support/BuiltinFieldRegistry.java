package com.crm.support;

import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.opportunity.OpportunityRequest;
import com.crm.dto.opportunity.OpportunityResponse;
import com.crm.entity.Customer;
import com.crm.entity.Opportunity;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 内置字段注册表（102-builtin-field-permission）：哪些内置字段可以被配成 HIDDEN / READ_ONLY。
 *
 * <h2>入选规则（加条目之前先逐条对上，别顺手加）</h2>
 *
 * <ol>
 *   <li>是**用户可见的业务数据**（不是审计/技术列）；
 *   <li>在 **entity / request / response 三侧都有同名载体**（掩码与回补都靠这个名字解析）；
 *   <li>**不是必填**——见下方「必填字段为什么永久排除」；
 *   <li>{@code null} **不承载额外语义**（置 null 之后不会改变实体的业务含义）。
 * </ol>
 *
 * <h2>⚠️ 三类刻意排除（不是遗漏）</h2>
 *
 * <ul>
 *   <li><b>必填字段永久排除</b>：{@code CustomerRequest.name}/{@code company}（{@code @NotBlank}）、 {@code
 *       OpportunityRequest.customerId}（{@code @NotNull}）与 {@code name}。把必填字段设成 HIDDEN
 *       会让实体**完全不可编辑**——客户端看不见它却必须提交它。这条排除的代价必须如实读： {@code CRM_FEATURE_COMPARISON.md}
 *       点名的「内置字段（客户名、金额等）」里， **「客户名」这一半本项不覆盖**。
 *   <li><b>{@code ownerId} / {@code ownerName} 排除</b>：{@code ownerId} 的既有语义是「空 = 公海」 （见 {@code
 *       CustomerResponse} 的注释），置 null 不是「看不见」而是「把它变成公海」—— 那是**说谎**而不是掩码；{@code ownerName}
 *       只在两条装配路径上被赋值，遮蔽它会时有时无。
 *   <li><b>派生 / 只读列排除</b>：{@code customerName}、{@code salesOpportunityCount} 之类没有对应实体列， 回补时无值可回。
 * </ul>
 *
 * <h2>为什么必须有 {@link #validate()}</h2>
 *
 * <p>掩码与回补都是**按属性名**做的反射操作，而反射最坏的失败形态是**静默成功**：名字打错时 {@code getDeclaredField}
 * 抛异常会被写成「跳过」，于是权限**看起来配好了、实际什么也没做**， 且没有任何用例会红。故本类在 {@code @PostConstruct}
 * 里对**每一个载体类**解析**每一个注册字段**， 任一解析失败即抛 {@link IllegalStateException} ⇒ 应用起不来。这是本设计最关键的护栏。
 *
 * <p>载体匹配用 {@link Class#isInstance(Object)}（而不是精确类相等）：{@code CustomerDetailResponse extends
 * CustomerResponse}、{@code OpportunityDetailResponse extends OpportunityResponse}， 掩码必须对详情型子类同样生效。
 */
@Component
public class BuiltinFieldRegistry {

  public static final String ENTITY_CUSTOMER = "CUSTOMER";
  public static final String ENTITY_OPPORTUNITY = "OPPORTUNITY";

  /** 注册条数。测试会断言它——防「悄悄少一条」与「顺手加一条」。 */
  public static final int EXPECTED_FIELD_COUNT = 11;

  /** 唯一真源：能被配权限的内置字段（顺序即配置面的展示顺序）。 */
  private static final List<BuiltinField> REGISTERED =
      List.of(
          new BuiltinField(ENTITY_CUSTOMER, "contactPerson", "联系人"),
          new BuiltinField(ENTITY_CUSTOMER, "phone", "电话"),
          new BuiltinField(ENTITY_CUSTOMER, "email", "邮箱"),
          new BuiltinField(ENTITY_CUSTOMER, "address", "地址"),
          new BuiltinField(ENTITY_CUSTOMER, "remark", "备注"),
          new BuiltinField(ENTITY_CUSTOMER, "status", "状态"),
          new BuiltinField(ENTITY_CUSTOMER, "campaignId", "营销活动"),
          new BuiltinField(ENTITY_OPPORTUNITY, "expectedAmountMin", "预计金额下限"),
          new BuiltinField(ENTITY_OPPORTUNITY, "expectedAmountMax", "预计金额上限"),
          new BuiltinField(ENTITY_OPPORTUNITY, "remark", "备注"),
          new BuiltinField(ENTITY_OPPORTUNITY, "status", "状态"));

  /** 每个实体类型的载体类：实体（回补用）、请求（写入校验用）、响应（掩码用）。 */
  private static final Map<String, List<Class<?>>> CARRIERS =
      Map.of(
          ENTITY_CUSTOMER, List.of(Customer.class, CustomerRequest.class, CustomerResponse.class),
          ENTITY_OPPORTUNITY,
              List.of(Opportunity.class, OpportunityRequest.class, OpportunityResponse.class));

  /** {@code entityType + ":" + fieldKey} → 注册项。 */
  private final Map<String, BuiltinField> byKey = new LinkedHashMap<>();

  /** 载体类 → 启动时解析好的字段句柄。 */
  private final List<Carrier> carriers = new ArrayList<>();

  /** 启动自检：解析每一个载体类上的每一个注册属性，失败即让应用起不来。 */
  @PostConstruct
  void validate() {
    if (REGISTERED.size() != EXPECTED_FIELD_COUNT) {
      throw new IllegalStateException(
          "内置字段注册表条数为 " + REGISTERED.size() + "，与声明的 " + EXPECTED_FIELD_COUNT + " 不符");
    }
    for (String entityType : CARRIERS.keySet()) {
      for (BuiltinField f : REGISTERED) {
        if (entityType.equals(f.entityType())) {
          BuiltinField previous = byKey.put(f.entityType() + ":" + f.fieldKey(), f);
          if (previous != null) {
            throw new IllegalStateException("内置字段重复注册：" + f.entityType() + ":" + f.fieldKey());
          }
        }
      }
    }
    for (Map.Entry<String, List<Class<?>>> entry : CARRIERS.entrySet()) {
      String entityType = entry.getKey();
      for (Class<?> type : entry.getValue()) {
        Map<String, Field> handles = new LinkedHashMap<>();
        for (BuiltinField f : REGISTERED) {
          if (!entityType.equals(f.entityType())) {
            continue;
          }
          Field handle = declaredField(type, f.fieldKey());
          if (handle == null) {
            throw new IllegalStateException(
                "载体类 "
                    + type.getName()
                    + " 上没有属性 `"
                    + f.fieldKey()
                    + "`（内置字段 "
                    + entityType
                    + ":"
                    + f.fieldKey()
                    + "）。掩码与回补都按属性名做反射，名字对不上时会**静默什么都不做**，"
                    + "故此处直接让应用起不来。");
          }
          handle.setAccessible(true);
          handles.put(f.fieldKey(), handle);
        }
        carriers.add(new Carrier(entityType, type, handles));
      }
    }
    if (carriers.size() != CARRIERS.values().stream().mapToInt(List::size).sum()) {
      throw new IllegalStateException("载体句柄数量与声明不符，注册表构建失败");
    }
  }

  /** 某实体的全部注册字段（配置面用；未注册的实体返回空表）。 */
  public List<BuiltinField> fieldsOf(String entityType) {
    List<BuiltinField> result = new ArrayList<>();
    for (BuiltinField f : REGISTERED) {
      if (f.entityType().equals(entityType)) {
        result.add(f);
      }
    }
    return result;
  }

  /** 注册表里已知的实体类型（配置面据此拒绝未知 entityType）。 */
  public List<String> knownEntityTypes() {
    return new ArrayList<>(CARRIERS.keySet());
  }

  /** 查注册项；未注册返回 {@code null}（调用方据此 422，而不是静默接受一条垃圾配置）。 */
  public BuiltinField find(String entityType, String fieldKey) {
    if (entityType == null || fieldKey == null) {
      return null;
    }
    return byKey.get(entityType + ":" + fieldKey);
  }

  /** 该对象是不是某个已注册载体的实例；是则返回它的实体类型，否则 {@code null}。 */
  public String entityTypeOf(Object candidate) {
    Carrier carrier = carrierFor(candidate);
    return carrier == null ? null : carrier.entityType;
  }

  /** 反射读一个注册属性；非载体 / 未注册 / 对象为 null 时返回 {@code null}。 */
  public Object read(Object carrier, String fieldKey) {
    Carrier c = carrierFor(carrier);
    if (c == null) {
      return null;
    }
    Field handle = c.fields.get(fieldKey);
    return handle == null ? null : get(carrier, handle);
  }

  /** 掩码：把这些属性就地**置 null**（不删键——删键需要全局 {@code @JsonInclude}，那会改动全仓响应体形状）。 */
  public void nullify(Object target, Collection<String> fieldKeys) {
    Carrier c = carrierFor(target);
    if (c == null || fieldKeys == null) {
      return;
    }
    for (String fieldKey : fieldKeys) {
      Field handle = c.fields.get(fieldKey);
      if (handle != null) {
        set(target, handle, null);
      }
    }
  }

  /**
   * 写侧回补：把 {@code source} 上这些属性的值原样写到 {@code target}（{@code source} 为 null 即置 null）。
   *
   * <p>只遍历**注册表里的**属性，且调用方只传掩码命中的键——不掩码的字段一律由 {@code apply} 决定， 回补不会吞掉正常编辑。
   */
  public void copyInto(Object target, Object source, Collection<String> fieldKeys) {
    Carrier c = carrierFor(target);
    if (c == null || fieldKeys == null) {
      return;
    }
    for (String fieldKey : fieldKeys) {
      Field handle = c.fields.get(fieldKey);
      if (handle != null) {
        set(target, handle, read(source, fieldKey));
      }
    }
  }

  private Carrier carrierFor(Object candidate) {
    if (candidate == null) {
      return null;
    }
    for (Carrier c : carriers) {
      if (c.type.isInstance(candidate)) {
        return c;
      }
    }
    return null;
  }

  /** 沿类层次向上找声明字段（详情型子类不重复声明父类字段）。 */
  private static Field declaredField(Class<?> type, String name) {
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        // 继续往父类找；找到底都没有则由调用方报错
      }
    }
    return null;
  }

  private static Object get(Object target, Field handle) {
    try {
      return handle.get(target);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("反射读取内置字段失败：" + handle, e);
    }
  }

  private static void set(Object target, Field handle, Object value) {
    try {
      handle.set(target, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("反射写入内置字段失败：" + handle, e);
    }
  }

  /** 一个载体类 + 它在启动时解析好的字段句柄（{@code fieldKey} → 句柄）。 */
  private static final class Carrier {

    private final String entityType;
    private final Class<?> type;
    private final Map<String, Field> fields;

    Carrier(String entityType, Class<?> type, Map<String, Field> fields) {
      this.entityType = entityType;
      this.type = type;
      this.fields = fields;
    }
  }
}
