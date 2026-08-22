package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 跟进记录集成测试（T039，US4）。 */
class FollowUpIT extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"跟进客户\", \"company\": \"跟进公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createFollowUp(String token, long customerId) throws Exception {
    String body =
        String.format(
            "{\"customerId\": %d, \"method\": \"PHONE\", \"content\": \"沟通续约意向\", \"nextFollowUpAt\": \"2026-08-28T10:00:00\"}",
            customerId);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/follow-ups")
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
  @DisplayName("跟进记录：添加→时间线可见→编辑")
  void followUpLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long followUpId = createFollowUp(token, customerId);

    mockMvc
        .perform(
            get("/api/v1/follow-ups")
                .header("Authorization", bearer(token))
                .param("customerId", String.valueOf(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].content").value("沟通续约意向"));

    String updateBody =
        String.format(
            "{\"customerId\": %d, \"method\": \"EMAIL\", \"content\": \"已发邮件补充方案\", \"version\": 0}",
            customerId);
    mockMvc
        .perform(
            put("/api/v1/follow-ups/{id}", followUpId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("已发邮件补充方案"))
        .andExpect(jsonPath("$.data.method").value("EMAIL"));
  }

  @Test
  @DisplayName("非本人编辑他人跟进记录返回 403")
  void editOthersFollowUpForbidden() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long followUpId = createFollowUp(token, customerId);

    // 创建 SUPPORT 用户并登录
    User support = new User();
    support.setUsername("support2");
    support.setPasswordHash(passwordEncoder.encode("pass1234"));
    support.setDisplayName("客服二");
    support.setRole("SUPPORT");
    support.setEnabled(true);
    userMapper.insert(support);
    String supportToken = loginAndGetToken("support2", "pass1234");

    String updateBody =
        String.format(
            "{\"customerId\": %d, \"method\": \"PHONE\", \"content\": \"越权修改\", \"version\": 0}",
            customerId);
    mockMvc
        .perform(
            put("/api/v1/follow-ups/{id}", followUpId)
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("商机与客户不匹配返回 422")
  void opportunityCustomerMismatch() throws Exception {
    String token = loginAndGetToken();
    long customerA = createCustomer(token);
    // 客户 B + 其商机
    String customerB =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"客户B\", \"company\": \"B 公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerBId = objectMapper.readTree(customerB).path("data").path("id").asLong();
    String oppBody = String.format("{\"customerId\": %d, \"name\": \"B 的商机\"}", customerBId);
    String oppResp =
        mockMvc
            .perform(
                post("/api/v1/opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(oppBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long oppId = objectMapper.readTree(oppResp).path("data").path("id").asLong();

    // 给客户 A 添加跟进，但挂 B 的商机 → 422
    String body =
        String.format(
            "{\"customerId\": %d, \"opportunityId\": %d, \"method\": \"PHONE\", \"content\": \"错挂\"}",
            customerA, oppId);
    mockMvc
        .perform(
            post("/api/v1/follow-ups")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("OPPORTUNITY_CUSTOMER_MISMATCH"));
  }
}
