package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 字段权限集成测试（056 T012）：配置/标记/保存拦截/ADMIN 豁免。 */
class FieldPermissionIT extends AbstractIntegrationTest {

  private long createCustomField(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"entityType\": \"LEAD\", \"name\": \"敏感字段%d\", \"fieldType\": \"TEXT\", \"required\": false}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private String createSalesUser(String adminToken) throws Exception {
    String username = "fpsales" + (System.nanoTime() % 100000);
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"username\": \"%s\", \"password\": \"Passw0rd!\", \"displayName\": \"字段销售\", \"role\": \"SALES\"}",
                        username)))
        .andExpect(status().isCreated());
    return loginAndGetToken(username, "Passw0rd!");
  }

  @Test
  @DisplayName("字段权限流程：配置只读→SALES 修改 422→ADMIN 豁免")
  void fieldPermissionFlow() throws Exception {
    String adminToken = loginAndGetToken();
    long fieldId = createCustomField(adminToken);
    String salesToken = createSalesUser(adminToken);

    // 创建线索（SALES，含自定义字段值）
    String leadResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(salesToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"权限线索%d\", \"company\": \"权限公司\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"v1\"}]}",
                            System.nanoTime(), fieldId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long leadId = objectMapper.readTree(leadResp).path("data").path("id").asLong();
    int leadVersion = objectMapper.readTree(leadResp).path("data").path("version").asInt();

    // 配置 SALES 对该字段只读
    mockMvc
        .perform(
            post("/api/v1/field-permissions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"roleCode\": \"SALES\", \"entityType\": \"LEAD\", \"fieldId\": %d, \"permission\": \"READ_ONLY\"}",
                        fieldId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.permission").value("READ_ONLY"));

    // SALES 修改该字段值 → 422 FIELD_READ_ONLY
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", leadId)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"权限线索改\", \"company\": \"权限公司\", \"version\": %d, \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"v2\"}]}",
                        leadVersion, fieldId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("FIELD_READ_ONLY"));

    // ADMIN 修改豁免 → 200
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", leadId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"权限线索A\", \"company\": \"权限公司\", \"version\": %d, \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"admin-v\"}]}",
                        leadVersion, fieldId)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("读路径：HIDDEN 字段的值不下发（详情与列表），且改单时不丢原值")
  void hiddenFieldValueIsNeitherLeakedNorDeleted() throws Exception {
    String adminToken = loginAndGetToken();
    long hiddenField = createCustomField(adminToken);
    long visibleField = createCustomField(adminToken);
    String salesToken = createSalesUser(adminToken);

    String leadResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(salesToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"泄漏线索%d\", \"company\": \"泄漏公司\", \"customFieldValues\":"
                                + " [{\"fieldId\": %d, \"value\": \"机密\"}, {\"fieldId\": %d, \"value\": \"旧\"}]}",
                            System.nanoTime(), hiddenField, visibleField)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long leadId = objectMapper.readTree(leadResp).path("data").path("id").asLong();
    int version = objectMapper.readTree(leadResp).path("data").path("version").asInt();

    // ADMIN 把第一个字段对 SALES 设为 HIDDEN（第二个不配置 → 默认 EDITABLE）
    mockMvc
        .perform(
            post("/api/v1/field-permissions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"roleCode\": \"SALES\", \"entityType\": \"LEAD\", \"fieldId\": %d, \"permission\": \"HIDDEN\"}",
                        hiddenField)))
        .andExpect(status().isCreated());

    // 1) 详情：HIDDEN 字段的值不下发
    mockMvc
        .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(salesToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customFieldValues[*].fieldId", not(hasItem((int) hiddenField))))
        .andExpect(jsonPath("$.data.customFieldValues[*].fieldId", hasItem((int) visibleField)));

    // 2) 列表：走批量读（readValuesBatch），同样不下发
    String listResp =
        mockMvc
            .perform(
                get("/api/v1/leads")
                    .header("Authorization", bearer(salesToken))
                    .param("pageSize", "100"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var item = objectMapper.readTree(listResp).path("data").path("items");
    var leadNode =
        java.util.stream.StreamSupport.stream(item.spliterator(), false)
            .filter(n -> n.path("id").asLong() == leadId)
            .findFirst()
            .orElseThrow();
    var listedFieldIds = new java.util.ArrayList<Long>();
    leadNode.path("customFieldValues").forEach(v -> listedFieldIds.add(v.path("fieldId").asLong()));
    org.assertj.core.api.Assertions.assertThat(listedFieldIds)
        .contains(visibleField)
        .doesNotContain(hiddenField);

    // 3) ADMIN 仍看得到（未误伤）
    mockMvc
        .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(adminToken)))
        .andExpect(jsonPath("$.data.customFieldValues[*].value", hasItem("机密")));

    // 4) SALES 只提交可见字段 → 先删后插不得把 HIDDEN 字段的原值删掉
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", leadId)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"泄漏线索改\", \"company\": \"泄漏公司\", \"version\": %d,"
                            + " \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"新\"}]}",
                        version, visibleField)))
        .andExpect(status().isOk());

    // 5) 原值仍在
    mockMvc
        .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(adminToken)))
        .andExpect(jsonPath("$.data.customFieldValues[*].value", hasItem("机密")));
  }

  // ===== 103：受保护字段（HIDDEN ∪ READ_ONLY）被省略时不得销毁其值 =====

  /**
   * 给某角色配置某自定义字段的权限（056 配置面；此处是 `fieldId` 形态，`BuiltinFieldPermissionFixture` 那种 `fieldKey` 形态不适用）。
   */
  private void configureCustom(String adminToken, String roleCode, long fieldId, String permission)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/field-permissions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"roleCode\": \"%s\", \"entityType\": \"LEAD\", \"fieldId\": %d, \"permission\": \"%s\"}",
                        roleCode, fieldId, permission)))
        .andExpect(status().isCreated());
  }

  /** 建一个**必填**的自定义字段（`createCustomField` 固定 required=false，I5 需要另一形态）。 */
  private long createRequiredCustomField(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"entityType\": \"LEAD\", \"name\": \"必填敏感字段%d\", \"fieldType\": \"TEXT\", \"required\": true}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  /** 建一条带两个自定义字段值的线索，返回 {id, version}。 */
  private long[] createLeadWithTwoValues(
      String token, long fieldA, String valueA, long fieldB, String valueB) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"受保护线索%d\", \"company\": \"受保护公司\", \"customFieldValues\":"
                                + " [{\"fieldId\": %d, \"value\": \"%s\"}, {\"fieldId\": %d, \"value\": \"%s\"}]}",
                            System.nanoTime(), fieldA, valueA, fieldB, valueB)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    var data = objectMapper.readTree(resp).path("data");
    return new long[] {data.path("id").asLong(), data.path("version").asLong()};
  }

  /**
   * 以给定身份读回某线索的自定义字段值结点数组（显式 UTF-8：本仓有 ISO-8859-1 假红先例）。
   *
   * <p>「值是否被销毁」一律取 **ADMIN 读回**：受限调用方看不见它要检查的字段（ADMIN 豁免掩码），且 {@code default-property-inclusion:
   * non_null} 让「缺键」与「null」不可区分——只有 ADMIN 视角能把「值还在」与「值没了」分开。I4 是唯一例外：那里要证的正是**受限角色看得见**。
   */
  private com.fasterxml.jackson.databind.JsonNode customValuesOf(long leadId, String token)
      throws Exception {
    String resp =
        mockMvc
            .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(resp).path("data").path("customFieldValues");
  }

  /** 该字段在返回值里出现了几次（回补若与主循环撞车，这里会变成 2）。 */
  private long countField(com.fasterxml.jackson.databind.JsonNode values, long fieldId) {
    long n = 0;
    for (com.fasterxml.jackson.databind.JsonNode v : values) {
      if (v.path("fieldId").asLong() == fieldId) {
        n++;
      }
    }
    return n;
  }

  /** 该字段的值；不存在返回 null（供「已消失」判定）。 */
  private String valueOf(com.fasterxml.jackson.databind.JsonNode values, long fieldId) {
    for (com.fasterxml.jackson.databind.JsonNode v : values) {
      if (v.path("fieldId").asLong() == fieldId) {
        return v.path("value").asText();
      }
    }
    return null;
  }

  @Test
  @DisplayName("103：SALES 省略 READ_ONLY 自定义字段 → 原值保留（正对照：同一次 PUT 改的字段确实变了）")
  void omittedReadOnlyCustomFieldValueSurvivesUpdate() throws Exception {
    String adminToken = loginAndGetToken();
    long readOnlyField = createCustomField(adminToken);
    long editableField = createCustomField(adminToken);
    String salesToken = createSalesUser(adminToken);

    long[] lead = createLeadWithTwoValues(salesToken, readOnlyField, "只读原值", editableField, "旧");
    configureCustom(adminToken, "SALES", readOnlyField, "READ_ONLY");

    // READ_ONLY 字段是**结构性省略**：客户端改不动它，自然不会提交它（只提交可编辑的那个）
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", lead[0])
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"受保护线索改\", \"company\": \"受保护公司\", \"version\": %d,"
                            + " \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"新\"}]}",
                        lead[1], editableField)))
        .andExpect(status().isOk());

    var values = customValuesOf(lead[0], adminToken);
    assertThat(valueOf(values, readOnlyField)).isEqualTo("只读原值");
    assertThat(valueOf(values, editableField)).isEqualTo("新");
  }

  @Test
  @DisplayName("103：原样回传 READ_ONLY 的值 → 200 且该字段恰好一个值（不得撞 uk_field_entity_value）")
  void echoingReadOnlyCustomFieldValueDoesNotFail() throws Exception {
    String adminToken = loginAndGetToken();
    long readOnlyField = createCustomField(adminToken);
    long editableField = createCustomField(adminToken);
    String salesToken = createSalesUser(adminToken);

    long[] lead = createLeadWithTwoValues(salesToken, readOnlyField, "只读原值", editableField, "旧");
    configureCustom(adminToken, "SALES", readOnlyField, "READ_ONLY");

    // 值相等 ⇒ validateWrite 判为「未变更」⇒ 不 422 ⇒ 主循环会插这一行；回补必须跳过它
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", lead[0])
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"受保护线索改\", \"company\": \"受保护公司\", \"version\": %d,"
                            + " \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"只读原值\"}, {\"fieldId\": %d, \"value\": \"新\"}]}",
                        lead[1], readOnlyField, editableField)))
        .andExpect(status().isOk());

    var values = customValuesOf(lead[0], adminToken);
    assertThat(countField(values, readOnlyField)).isEqualTo(1);
    assertThat(valueOf(values, readOnlyField)).isEqualTo("只读原值");
    assertThat(valueOf(values, editableField)).isEqualTo("新");
  }

  @Test
  @DisplayName("103 反方向：无任何权限配置时，省略的自定义字段仍被清空（清空能力不得被过度回补毁掉）")
  void omittedEditableCustomFieldValueIsStillCleared() throws Exception {
    String adminToken = loginAndGetToken();
    long fieldA = createCustomField(adminToken);
    long fieldB = createCustomField(adminToken);
    String salesToken = createSalesUser(adminToken);

    long[] lead = createLeadWithTwoValues(salesToken, fieldA, "旧一", fieldB, "旧二");
    // 刻意不配置任何字段权限 ⇒ 两个字段都是 EDITABLE ⇒ 省略即清空（客户端清除自定义字段的唯一手段）

    mockMvc
        .perform(
            put("/api/v1/leads/{id}", lead[0])
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"受保护线索改\", \"company\": \"受保护公司\", \"version\": %d,"
                            + " \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"新\"}]}",
                        lead[1], fieldB)))
        .andExpect(status().isOk());

    var values = customValuesOf(lead[0], adminToken);
    assertThat(countField(values, fieldA)).isZero();
    assertThat(valueOf(values, fieldB)).isEqualTo("新");
  }

  @Test
  @DisplayName("103：读路径仍只挡 HIDDEN——受限角色必须看得见 READ_ONLY 字段的值（否则无从原样回传）")
  void readOnlyCustomFieldValueIsStillVisibleToTheRestrictedRole() throws Exception {
    String adminToken = loginAndGetToken();
    long readOnlyField = createCustomField(adminToken);
    long editableField = createCustomField(adminToken);
    String salesToken = createSalesUser(adminToken);

    long[] lead = createLeadWithTwoValues(salesToken, readOnlyField, "只读原值", editableField, "旧");
    configureCustom(adminToken, "SALES", readOnlyField, "READ_ONLY");

    var asSales = customValuesOf(lead[0], salesToken);
    assertThat(countField(asSales, readOnlyField)).isEqualTo(1);
    assertThat(valueOf(asSales, readOnlyField)).isEqualTo("只读原值");
  }

  @Test
  @DisplayName("103 边界（非 fix）：必填自定义字段配成 READ_ONLY 后被省略 → 422，必填校验早于回补")
  void requiredProtectedCustomFieldSaveIsRejected() throws Exception {
    String adminToken = loginAndGetToken();
    long requiredField = createRequiredCustomField(adminToken);
    long editableField = createCustomField(adminToken);
    String salesToken = createSalesUser(adminToken);

    long[] lead = createLeadWithTwoValues(salesToken, requiredField, "必填值", editableField, "别的");
    configureCustom(adminToken, "SALES", requiredField, "READ_ONLY");

    // 触发这个边界必须提交一个**非空**的 customFieldValues 且其中不含必填字段。
    // 实测（本用例第一版红过）：整个 customFieldValues 缺席时是 200 而非 422——调用点
    // LeadService.update 是 `if (req.getCustomFieldValues() != null && !isEmpty())` 才进
    // saveValues，缺席时 saveValues 根本不被调用，必填校验与回补一起被跳过（也因此不会销毁
    // 任何值）。故「省略」在这里有两层：省掉整个列表 = 调用点直接不进来；省掉列表里的某一项
    // = 才轮到 saveValues 的必填校验。本用例钉的是后者。
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", lead[0])
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"受保护线索改\", \"company\": \"受保护公司\", \"version\": %d,"
                            + " \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"新\"}]}",
                        lead[1], editableField)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CUSTOM_FIELD_REQUIRED"));
  }

  @Test
  @DisplayName("删除权限配置")
  void deletePermission() throws Exception {
    String adminToken = loginAndGetToken();
    long fieldId = createCustomField(adminToken);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/field-permissions")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"roleCode\": \"SUPPORT\", \"entityType\": \"CUSTOMER\", \"fieldId\": %d, \"permission\": \"HIDDEN\"}",
                            fieldId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long permId = objectMapper.readTree(resp).path("data").path("id").asLong();

    mockMvc
        .perform(
            delete("/api/v1/field-permissions/{id}", permId)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());
  }
}
