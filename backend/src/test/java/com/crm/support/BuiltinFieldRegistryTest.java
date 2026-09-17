package com.crm.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.dto.customer.CustomerDetailResponse;
import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.opportunity.OpportunityRequest;
import com.crm.dto.opportunity.OpportunityResponse;
import com.crm.entity.Customer;
import com.crm.entity.Opportunity;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 内置字段注册表（102 T1/T2）：条数、成员、启动自检，以及三个反射原语的行为边界。
 *
 * <p><b>为什么这个类必须有用例</b>：{@code REGISTERED} 与 {@code CARRIERS} 都是 {@code private static final}、
 * {@code validate()} 只在 {@code @PostConstruct} 跑一次，而掩码/回补/写守卫三条链**全部**按属性名做反射——名字
 * 对不上时反射的失败形态是**静默什么都不做**（权限看起来配好了、实际无效，且没有任何用例会红）。故这里既钉住 「坏名字一定抛」（{@link
 * BuiltinFieldRegistry#requireProperty}），也钉住「真条目在真载体上一定解析得到」。
 *
 * <p>本类不起 Spring 上下文：直接 {@code new} 出注册表并手调包级可见的 {@code validate()}（它就是 {@code @PostConstruct}
 * 的那个方法），这样判定失败会以用例红的形式出现，而不是「启动日志里少了一行」。
 */
class BuiltinFieldRegistryTest {

  private final BuiltinFieldRegistry registry = new BuiltinFieldRegistry();

  @BeforeEach
  void setUp() {
    // 等价于 @PostConstruct：条数断言 + 每一个载体类上的每一个注册属性都解析一遍。
    // 它自身红了就说明注册表内容与载体类脱节（本设计最关键的护栏）。
    registry.validate();
  }

  @Test
  @DisplayName("T2 注册表条数与成员被逐条钉住（防悄悄少一条 / 顺手加一条）")
  void registeredFieldsArePinned() {
    assertThat(BuiltinFieldRegistry.EXPECTED_FIELD_COUNT).isEqualTo(11);
    assertThat(registry.knownEntityTypes())
        .containsExactlyInAnyOrder(
            BuiltinFieldRegistry.ENTITY_CUSTOMER, BuiltinFieldRegistry.ENTITY_OPPORTUNITY);

    List<String> actual =
        List.of(BuiltinFieldRegistry.ENTITY_CUSTOMER, BuiltinFieldRegistry.ENTITY_OPPORTUNITY)
            .stream()
            .flatMap(
                e -> registry.fieldsOf(e).stream().map(f -> f.entityType() + ":" + f.fieldKey()))
            .toList();
    assertThat(actual)
        .containsExactly(
            "CUSTOMER:contactPerson",
            "CUSTOMER:phone",
            "CUSTOMER:email",
            "CUSTOMER:address",
            "CUSTOMER:remark",
            "CUSTOMER:status",
            "CUSTOMER:campaignId",
            "OPPORTUNITY:expectedAmountMin",
            "OPPORTUNITY:expectedAmountMax",
            "OPPORTUNITY:remark",
            "OPPORTUNITY:status");
  }

  @Test
  @DisplayName("T2 必填字段与 ownerId/ownerName 不注册（设成 HIDDEN 会让实体不可编辑 / 说谎）")
  void requiredAndOwnerFieldsAreNotRegistered() {
    for (String key : List.of("name", "company", "customerId", "ownerId", "ownerName")) {
      for (String entityType : registry.knownEntityTypes()) {
        assertThat(registry.find(entityType, key)).as("%s 上不该注册 %s", entityType, key).isNull();
      }
    }
  }

  @Test
  @DisplayName("T1 属性名打错 ⇒ requireProperty 抛 IllegalStateException（而不是静默空操作）")
  void requirePropertyRejectsUnknownPropertyName() {
    BuiltinField typo =
        new BuiltinField(BuiltinFieldRegistry.ENTITY_CUSTOMER, "noSuchProperty", "不存在");
    assertThatThrownBy(() -> BuiltinFieldRegistry.requireProperty(Customer.class, typo))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("noSuchProperty")
        .hasMessageContaining("CUSTOMER:noSuchProperty");
    // 正对照：同一个方法在真条目上不抛 —— 否则上一条会被读成「这个分支恒抛」
    assertThat(
            BuiltinFieldRegistry.requireProperty(
                Customer.class, registered(typo.entityType(), "phone")))
        .isNotNull();
  }

  @Test
  @DisplayName("T1 边界：自检只认属性名、不校验类型（如实钉住实现范围）")
  void requirePropertyChecksNameOnly() {
    // declaredField 沿类层次按名字找，类型不参与判定。用「名字在、类型与注册表里的同名项不同」的
    // 情形说明这一点：Customer.id 是 Long，而 CUSTOMER:phone 是 String，两者都解析得到。
    // 这条断言的作用是防后人把「类型也对」当成既有保证来依赖（它会让这条红）。
    BuiltinField idField = new BuiltinField(BuiltinFieldRegistry.ENTITY_CUSTOMER, "id", "编号");
    assertThat(BuiltinFieldRegistry.requireProperty(Customer.class, idField)).isNotNull();
    assertThat(
            BuiltinFieldRegistry.requireProperty(Customer.class, registered("CUSTOMER", "phone")))
        .isNotNull();
  }

  @Test
  @DisplayName("find：未注册 / 未知实体 / null 入参一律返回 null（调用方据此 422）")
  void findReturnsNullForUnknown() {
    assertThat(registry.find("CUSTOMER", "phone")).isNotNull();
    assertThat(registry.find("CUSTOMER", "nope")).isNull();
    assertThat(registry.find("TICKET", "phone")).isNull();
    assertThat(registry.find(null, "phone")).isNull();
    assertThat(registry.find("CUSTOMER", null)).isNull();
    assertThat(registry.fieldsOf("TICKET")).isEmpty();
    // 同名键属于别的实体 ⇒ 也必须认不出（find 的键是 entityType+fieldKey，不是单独的 fieldKey）
    assertThat(registry.find(BuiltinFieldRegistry.ENTITY_OPPORTUNITY, "phone")).isNull();
    assertThat(registry.fieldsOf("LEAD")).isEmpty();
  }

  @Test
  @DisplayName("read/nullify/write：只对载体生效，非载体与未注册键是空操作")
  void reflectionPrimitivesActOnCarriersOnly() {
    Customer customer = new Customer();
    customer.setPhone("13900000000");
    customer.setEmail("a@b.c");

    assertThat(registry.read(customer, "phone")).isEqualTo("13900000000");
    assertThat(registry.read(customer, "nope")).isNull();
    assertThat(registry.read(new Object(), "phone")).isNull();
    assertThat(registry.read(null, "phone")).isNull();

    registry.nullify(customer, Set.of("phone", "nope"));
    assertThat(customer.getPhone()).isNull();
    assertThat(customer.getEmail()).isEqualTo("a@b.c"); // 只动传进来的键
    registry.nullify(new Object(), Set.of("phone")); // 非载体 ⇒ 空操作，不抛
    registry.nullify(customer, null);

    registry.write(customer, "phone", "13800000000");
    assertThat(customer.getPhone()).isEqualTo("13800000000");
    registry.write(customer, "nope", "x"); // 未注册键 ⇒ 空操作
    registry.write(new Object(), "phone", "x"); // 非载体 ⇒ 空操作
  }

  @Test
  @DisplayName("T9 前置：entityTypeOf 认出详情型子类（掩码按 isInstance 匹配，不是精确类相等）")
  void entityTypeOfCoversDetailSubclasses() {
    assertThat(registry.entityTypeOf(new Customer()))
        .isEqualTo(BuiltinFieldRegistry.ENTITY_CUSTOMER);
    assertThat(registry.entityTypeOf(new CustomerResponse()))
        .isEqualTo(BuiltinFieldRegistry.ENTITY_CUSTOMER);
    assertThat(registry.entityTypeOf(new CustomerDetailResponse()))
        .isEqualTo(BuiltinFieldRegistry.ENTITY_CUSTOMER);
    assertThat(registry.entityTypeOf(new Opportunity()))
        .isEqualTo(BuiltinFieldRegistry.ENTITY_OPPORTUNITY);
    assertThat(registry.entityTypeOf(new Object())).isNull();
    assertThat(registry.entityTypeOf(null)).isNull();
  }

  @Test
  @DisplayName("每个注册字段在它的三个载体上都有同名属性（entity/request/response 三侧同时成立）")
  void everyRegisteredFieldResolvesOnAllThreeCarriers() {
    Map<String, List<Class<?>>> carriers =
        Map.of(
            BuiltinFieldRegistry.ENTITY_CUSTOMER,
            List.of(Customer.class, CustomerRequest.class, CustomerResponse.class),
            BuiltinFieldRegistry.ENTITY_OPPORTUNITY,
            List.of(Opportunity.class, OpportunityRequest.class, OpportunityResponse.class));
    for (Map.Entry<String, List<Class<?>>> entry : carriers.entrySet()) {
      for (BuiltinField f : registry.fieldsOf(entry.getKey())) {
        for (Class<?> type : entry.getValue()) {
          Field handle = BuiltinFieldRegistry.requireProperty(type, f);
          assertThat(handle).as("%s 上的 %s", type.getName(), f.fieldKey()).isNotNull();
        }
      }
    }
  }

  private BuiltinField registered(String entityType, String fieldKey) {
    for (BuiltinField f : registry.fieldsOf(entityType)) {
      if (f.fieldKey().equals(fieldKey)) {
        return f;
      }
    }
    throw new IllegalStateException("注册表里没有 " + entityType + ":" + fieldKey);
  }
}
