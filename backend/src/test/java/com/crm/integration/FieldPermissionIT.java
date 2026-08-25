package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
