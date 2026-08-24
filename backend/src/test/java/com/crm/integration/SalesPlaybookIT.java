package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 销售 Playbook 集成测试（045 T018）：模板配置/动作清单/勾选/重复 409。 */
class SalesPlaybookIT extends AbstractIntegrationTest {

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"Playbook客户%d\", \"company\": \"Playbook公司\"}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createOpportunity(String token, long customerId) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"name\": \"Playbook商机\", \"expectedAmountMax\": 1000000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createSalesOpportunity(String token, long opportunityId) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"opportunityId\": %d, \"amount\": 500000, \"stage\": \"INITIAL_CONTACT\"}",
                            opportunityId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("Playbook 流程：配置模板→清单→勾选→重复 409")
  void playbookFlow() throws Exception {
    String token = loginAndGetToken();

    // 配置动作模板
    String templateResp =
        mockMvc
            .perform(
                post("/api/v1/stage-actions")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"stage\": \"INITIAL_CONTACT\", \"actionName\": \"发送产品资料\", \"description\": \"首次接触后发送\", \"required\": true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.required").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long templateId = objectMapper.readTree(templateResp).path("data").path("id").asLong();

    // 创建客户→商机→销售机会
    long customerId = createCustomer(token);
    long opportunityId = createOpportunity(token, customerId);
    long soId = createSalesOpportunity(token, opportunityId);

    // 动作清单（未完成）
    mockMvc
        .perform(
            get("/api/v1/sales-opportunities/{id}/actions", soId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].templateId").value(templateId))
        .andExpect(jsonPath("$.data[0].completed").value(false));

    // 勾选完成
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/actions", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"templateId\": %d}", templateId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.completed").value(true));

    // 重复勾选 → 409
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/actions", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"templateId\": %d}", templateId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PLAYBOOK_ACTION_ALREADY_DONE"));
  }

  @Test
  @DisplayName("权限：SUPPORT 不可配置模板（403）")
  void permissionMatrix() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"playbooksupport\", \"password\": \"Passw0rd!\", \"displayName\": \"Playbook客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("playbooksupport", "Passw0rd!");

    mockMvc
        .perform(
            post("/api/v1/stage-actions")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\": \"INITIAL_CONTACT\", \"actionName\": \"越权动作\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("终态阶段不可配置模板（Bean Validation 400）")
  void terminalStageRejected() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/stage-actions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\": \"CLOSED_WON\", \"actionName\": \"x\"}"))
        .andExpect(status().isBadRequest());
  }
}
