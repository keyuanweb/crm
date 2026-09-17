package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.AbstractIntegrationTest;
import com.crm.integration.BuiltinFieldPermissionFixture.Res;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * T14：配置面「可用字段」端点（102）——{@code GET /field-permissions/available-fields}。
 *
 * <h2>为什么这条用例决定「本批能不能被用起来」</h2>
 *
 * <p>掩码链做得再对，只要管理员在下拉里**选不到**内置字段，那套权限就等于没有入口（前端此前只列自定义字段）。故这条用例断的不是「端点返回了东西」， 而是「内置字段带着中文名与可提交的
 * {@code fieldKey} 出现在**前端能直接渲染**的形状里」。
 *
 * <h2>为什么用 ADMIN 调</h2>
 *
 * <p>本端点挂 {@code field_permission:manage}——配权限本身就是管理员动作，且今天该码无人被授（见 {@code
 * FieldPermissionController} 的类注释）。这里要验的是**契约形状**，不是鉴权；鉴权面由 {@code PermissionEnforcementIT} 一族管。
 */
class FieldPermissionAvailableFieldsIT extends AbstractIntegrationTest {

  private BuiltinFieldPermissionFixture fixture;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new BuiltinFieldPermissionFixture(mockMvc, objectMapper, loginAndGetToken());
  }

  @Test
  @DisplayName("T14 内置字段带中文名出现，且 fieldId 为空、fieldKey 可提交（两个实体合计 11 条）")
  void builtinFieldsAreListedWithChineseNames() throws Exception {
    JsonNode customer = availableFields("CUSTOMER");
    assertThat(items(customer)).hasSize(7);
    assertThat(customer.path("data").path("total").asLong()).isEqualTo(7);
    assertThat(builtinFlags(customer)).as("该实体还没有自定义字段 ⇒ 应当全是内置").containsOnly(true);
    assertThat(fieldIdsOf(customer)).as("内置项不给 fieldId（给了前端就会把 null 发回来）").containsOnlyNulls();
    assertThat(fieldKeysOf(customer))
        .containsExactly(
            "contactPerson", "phone", "email", "address", "remark", "status", "campaignId");
    // 前端表格直接渲染 fieldName ⇒ 它必须是给人看的中文，而不是属性名/数字 id
    assertThat(fieldNameOf(customer, "phone")).isEqualTo("电话");

    JsonNode opportunity = availableFields("OPPORTUNITY");
    assertThat(fieldKeysOf(opportunity))
        .containsExactly("expectedAmountMin", "expectedAmountMax", "remark", "status");
    assertThat(builtinFlags(opportunity)).containsOnly(true);
  }

  @Test
  @DisplayName("T14 自定义字段排在内置之后，带 fieldId 与字段名（两类字段在同一个下拉里共存）")
  void customFieldsFollowBuiltinFields() throws Exception {
    long first = createCustomField("CUSTOMER", "客户等级", 1);
    long second = createCustomField("CUSTOMER", "年采购额", 2);

    JsonNode resp = availableFields("CUSTOMER");
    assertThat(resp.path("data").path("total").asLong()).isEqualTo(9);
    List<JsonNode> rows = items(resp);
    assertThat(rows).hasSize(9);
    assertThat(rows.subList(0, 7))
        .allSatisfy(row -> assertThat(row.path("builtin").asBoolean()).isTrue());
    assertThat(rows.subList(7, 9))
        .allSatisfy(row -> assertThat(row.path("builtin").asBoolean()).isFalse());
    assertThat(rows.get(7).path("fieldId").asLong()).isEqualTo(first);
    assertThat(rows.get(7).path("fieldName").asText()).isEqualTo("客户等级");
    assertThat(rows.get(8).path("fieldId").asLong()).isEqualTo(second);
    // 自定义项不得给出 fieldKey（与内置项「恰好一个标识」互为镜像）——前端据此二选一拼请求体
    assertThat(rows.get(7).has("fieldKey")).as("fieldKey 为 null ⇒ 键不该出现").isFalse();
  }

  @Test
  @DisplayName("T14 分页参数被透传（下拉默认一页取完，但 pageSize 仍生效）")
  void pagingIsHonoured() throws Exception {
    JsonNode resp = availableFieldsPage("CUSTOMER", 1, 5);
    assertThat(items(resp)).hasSize(5);
    assertThat(resp.path("data").path("total").asLong()).as("total 是总数而不是本页条数").isEqualTo(7);
  }

  @Test
  @DisplayName("T14 无任何可配字段 ⇒ 422 FIELD_PERMISSION_INVALID（空表与拼错实体不可区分）")
  void emptyResultIsRejectedAsInvalid() throws Exception {
    // 未知实体与「已知但没配内置/自定义字段」的实体走同一条判据——刻意不另立一份实体清单
    for (String entityType : List.of("NO_SUCH_ENTITY", "LEAD")) {
      Res res =
          fixture.call(
              fixture.adminToken(),
              HttpMethod.GET,
              "/api/v1/field-permissions/available-fields?entityType=" + entityType,
              null);
      assertThat(res.status()).as("%s 应 422：%s", entityType, res.body()).isEqualTo(422);
      assertThat(res.body().path("error").path("code").asText())
          .isEqualTo("FIELD_PERMISSION_INVALID");
    }
  }

  // ===== 封装 =====

  private JsonNode availableFields(String entityType) throws Exception {
    return availableFieldsPage(entityType, 1, 200);
  }

  private JsonNode availableFieldsPage(String entityType, long page, long pageSize)
      throws Exception {
    Res res =
        fixture.call(
            fixture.adminToken(),
            HttpMethod.GET,
            "/api/v1/field-permissions/available-fields?entityType="
                + entityType
                + "&page="
                + page
                + "&pageSize="
                + pageSize,
            null);
    assertThat(res.status()).as("可用字段端点应 200：%s", res.body()).isEqualTo(200);
    return res.body();
  }

  private long createCustomField(String entityType, String name, int sortOrder) throws Exception {
    Res res =
        fixture.call(
            fixture.adminToken(),
            HttpMethod.POST,
            "/api/v1/custom-fields",
            "{\"entityType\":\""
                + entityType
                + "\",\"name\":\""
                + name
                + "\",\"fieldType\":\"TEXT\",\"sortOrder\":"
                + sortOrder
                + "}");
    assertThat(res.status()).as("建自定义字段应 201：%s", res.body()).isEqualTo(201);
    return res.body().path("data").path("id").asLong();
  }

  private static List<JsonNode> items(JsonNode envelope) {
    List<JsonNode> rows = new ArrayList<>();
    envelope.path("data").path("items").forEach(rows::add);
    return rows;
  }

  private static List<String> fieldKeysOf(JsonNode envelope) {
    List<String> keys = new ArrayList<>();
    for (JsonNode row : items(envelope)) {
      keys.add(row.path("fieldKey").asText(null));
    }
    return keys;
  }

  private static List<Boolean> builtinFlags(JsonNode envelope) {
    List<Boolean> flags = new ArrayList<>();
    for (JsonNode row : items(envelope)) {
      flags.add(row.path("builtin").asBoolean());
    }
    return flags;
  }

  private static List<Long> fieldIdsOf(JsonNode envelope) {
    List<Long> ids = new ArrayList<>();
    for (JsonNode row : items(envelope)) {
      ids.add(row.has("fieldId") ? row.path("fieldId").asLong() : null);
    }
    return ids;
  }

  private static String fieldNameOf(JsonNode envelope, String fieldKey) {
    for (JsonNode row : items(envelope)) {
      if (fieldKey.equals(row.path("fieldKey").asText())) {
        return row.path("fieldName").asText();
      }
    }
    throw new AssertionError("可用字段里没有 " + fieldKey + "：" + envelope);
  }
}
