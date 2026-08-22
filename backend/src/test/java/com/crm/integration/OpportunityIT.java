package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 商机→销售机会→关闭 集成测试（T031，US3）。 */
class OpportunityIT extends AbstractIntegrationTest {

  @Autowired private CustomerMapper customerMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"张三\", \"company\": \"XX 科技\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createOpportunity(String token, long customerId) throws Exception {
    String body =
        String.format(
            "{\"customerId\": %d, \"name\": \"年度合作\", \"expectedAmountMin\": 100000, \"expectedAmountMax\": 500000}",
            customerId);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("商机全流程：创建商机→创建销售机会→阶段流转→关闭")
  void opportunityPipelineLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);

    // 商机详情包含下属销售机会列表（空）
    mockMvc
        .perform(get("/api/v1/opportunities/{id}", oppId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customerName").value("张三"))
        .andExpect(jsonPath("$.data.salesOpportunities.length()").value(0));

    // 创建销售机会（INITIAL_CONTACT）
    String soBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 300000, \"stage\": \"INITIAL_CONTACT\", \"expectedCloseDate\": \"2026-09-30\"}",
            oppId);
    String soResp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(soBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long soId = objectMapper.readTree(soResp).path("data").path("id").asLong();

    // 阶段流转 → NEGOTIATING
    String updateBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 300000, \"stage\": \"NEGOTIATING\", \"version\": 0}",
            oppId);
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/sales-opportunities/{id}", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stage").value("NEGOTIATING"))
        .andExpect(jsonPath("$.data.version").value(1));

    // 关闭（WON）
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closeResult\": \"WON\", \"version\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stage").value("CLOSED_WON"))
        .andExpect(jsonPath("$.data.closeResult").value("WON"))
        .andExpect(jsonPath("$.data.closedAt").isNotEmpty());

    // 已关闭不可再编辑
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/sales-opportunities/{id}", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("ALREADY_CLOSED"));
  }

  @Test
  @DisplayName("SUPPORT 角色访问商机接口返回 403（权限矩阵）")
  void supportRoleForbidden() throws Exception {
    User support = new User();
    support.setUsername("support1");
    support.setPasswordHash(passwordEncoder.encode("pass1234"));
    support.setDisplayName("客服一");
    support.setRole("SUPPORT");
    support.setEnabled(true);
    userMapper.insert(support);

    String token = loginAndGetToken("support1", "pass1234");
    mockMvc
        .perform(get("/api/v1/opportunities").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("关闭缺少结果返回 422 CLOSE_RESULT_REQUIRED")
  void closeWithoutResultReturns422() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    String soBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 100000, \"stage\": \"INITIAL_CONTACT\"}", oppId);
    String soResp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(soBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long soId = objectMapper.readTree(soResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 0}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CLOSE_RESULT_REQUIRED"));
  }

  @Test
  @DisplayName("逻辑删除商机后其销售机会不再可见")
  void deleteOpportunityCascades() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    String soBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 100000, \"stage\": \"INITIAL_CONTACT\"}", oppId);
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(soBody))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/opportunities/{id}", oppId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/sales-opportunities")
                .header("Authorization", bearer(token))
                .param("opportunityId", String.valueOf(oppId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }
}
