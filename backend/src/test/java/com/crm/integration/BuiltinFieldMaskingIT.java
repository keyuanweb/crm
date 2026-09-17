package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * T8/T9：内置字段掩码（102）。**出参收口点**在真实 HTTP 链路上是否生效。
 *
 * <p>两条腿必须一起断言，少一条就会被"看起来生效"骗过：
 *
 * <ol>
 *   <li><b>遮住了</b>：配成 HIDDEN 的字段看不见（见 {@link #assertInvisible} 对「看不见」的定义）；
 *   <li><b>没多遮</b>：同一次响应里别的字段仍是真值（否则"整段置 null"也能让第 1 条通过）。
 * </ol>
 *
 * <p>⚠️ 原计划此处还写了一条「键仍在：掩码是置 null 而不是删键」。**实测不成立**：掩码机制确实是就地置 null， 但本仓 {@code application.yml:22}
 * 的全局 {@code default-property-inclusion: non_null} 让空值连键都不出现 ⇒ 线上形态是**缺键**。断言已按实测收口（订正见 102 的
 * {@code tasks.md} 实做订正表）。
 *
 * <p>用的是 {@link BuiltinFieldPermissionFixture} 的**非 ADMIN** 角色：ADMIN 在收口点走快路径， 用管理员令牌跑本类会全绿而与实现无关。
 */
class BuiltinFieldMaskingIT extends AbstractIntegrationTest {

  private BuiltinFieldPermissionFixture fixture;
  private String token;
  private long ownedCustomerId;
  private long opportunityId;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new BuiltinFieldPermissionFixture(mockMvc, objectMapper, loginAndGetToken());
    fixture.ensureRole();
    token = fixture.createUser("fls_mask_").token();
    // 归该角色用户自己的客户：列表与详情都要过 012 的数据范围/归属判定，故必须有主
    ownedCustomerId = fixture.createCustomer(token, "掩码客户甲", "13900001111", "mask1@example.com");
    // 公海客户：owner 为空的只有 ADMIN 建得出来（非 ADMIN 建客户时会把自己设为负责人，见 064）
    fixture.createCustomer(fixture.adminToken(), "公海客户乙", "13900002222", "mask2@example.com");
    opportunityId = fixture.createOpportunity(token, ownedCustomerId, "掩码商机", 5000L, 20000L);

    fixture.configureBuiltin("CUSTOMER", "phone", "HIDDEN");
    fixture.configureBuiltin("OPPORTUNITY", "expectedAmountMin", "HIDDEN");
  }

  @Test
  @DisplayName("T8 客户 HIDDEN(phone)：列表 / 详情 / 公海三处都置 null，同响应其它字段不受影响")
  void customerPhoneIsMaskedInListDetailAndPool() throws Exception {
    JsonNode listRow = rowOf(token, "/api/v1/customers?pageSize=100", "掩码客户甲");
    assertInvisible(listRow, "phone", "列表");
    assertVisible(listRow, "email", "mask1@example.com");

    JsonNode detail =
        fixture
            .call(token, HttpMethod.GET, "/api/v1/customers/" + ownedCustomerId, null)
            .body()
            .path("data");
    assertInvisible(detail, "phone", "详情");
    assertVisible(detail, "email", "mask1@example.com");

    // 公海走的是另一份装配（CustomerPoolService.toResponse）——只改列表/详情的实现会让这条红
    JsonNode poolRow = rowOf(token, "/api/v1/customers/pool?pageSize=100", "公海客户乙");
    assertInvisible(poolRow, "phone", "公海");
    assertVisible(poolRow, "email", "mask2@example.com");

    // 正对照：ADMIN 仍看得见真值（否则"整段都是空的"也能让上面三条通过）
    JsonNode asAdmin =
        fixture
            .call(
                fixture.adminToken(), HttpMethod.GET, "/api/v1/customers/" + ownedCustomerId, null)
            .body()
            .path("data");
    assertVisible(asAdmin, "phone", "13900001111");
  }

  @Test
  @DisplayName("T9 商机 HIDDEN(expectedAmountMin)：详情型子类与列表都不可见，上限仍在")
  void opportunityAmountMinIsMaskedInDetailAndList() throws Exception {
    // 详情返回的是 OpportunityDetailResponse extends OpportunityResponse —— 掩码按 isInstance 匹配才覆盖得到它
    JsonNode detail =
        fixture
            .call(token, HttpMethod.GET, "/api/v1/opportunities/" + opportunityId, null)
            .body()
            .path("data");
    assertInvisible(detail, "expectedAmountMin", "详情（子类载体）");
    assertThat(detail.path("expectedAmountMax").asLong())
        .as("正对照：上限没配权限，必须仍是 20000")
        .isEqualTo(20000L);

    JsonNode listRow = rowOf(token, "/api/v1/opportunities?pageSize=100", "掩码商机");
    assertInvisible(listRow, "expectedAmountMin", "列表");
    assertThat(listRow.path("expectedAmountMax").asLong()).isEqualTo(20000L);

    JsonNode asAdmin =
        fixture
            .call(
                fixture.adminToken(),
                HttpMethod.GET,
                "/api/v1/opportunities/" + opportunityId,
                null)
            .body()
            .path("data");
    assertThat(asAdmin.path("expectedAmountMin").asLong()).isEqualTo(5000L);
  }

  /**
   * 「不可见」的判据：该字段**没有留下任何可读值**。
   *
   * <p>⚠️ 掩码的**机制**是就地置 null（{@code BuiltinFieldRegistry.nullify}），但线上形态由 Jackson 的全局 {@code
   * default-property-inclusion} 决定——本仓 {@code application.yml:22} 是 {@code non_null} ⇒ 空值**根本不出现**，
   * 故今天键是**缺失**的。 断言因此收成「缺失或 null 都算不可见」，钉的是**安全性质**（看不见）而不是序列化器的配置； 配置若哪天改成 {@code
   * always}，本断言不会假红，而 ADMIN 侧的正对照仍钉着「有值时键一定在」。
   */
  private static void assertInvisible(JsonNode node, String field, String where) {
    JsonNode value = node.get(field);
    assertThat(value == null || value.isNull())
        .as("%s：%s 应不可见（当前为 %s）", where, field, value)
        .isTrue();
  }

  /** 正对照：该字段必须看得见且等于期望值（防「整段空响应」被当成掩码成功）。 */
  private static void assertVisible(JsonNode node, String field, String expected) {
    assertThat(node.path(field).asText()).as("%s 应是真值（正对照）", field).isEqualTo(expected);
  }

  private JsonNode rowOf(String callerToken, String path, String name) throws Exception {
    return BuiltinFieldPermissionFixture.findByName(
        fixture.call(callerToken, HttpMethod.GET, path, null).body(), name);
  }
}
