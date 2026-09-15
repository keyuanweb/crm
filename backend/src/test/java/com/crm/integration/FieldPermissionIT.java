package com.crm.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
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
