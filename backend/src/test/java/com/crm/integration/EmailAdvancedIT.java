package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 邮件高级集成测试（052 T015）：退订/名单/排除/A-B/统计。 */
class EmailAdvancedIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String phone) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"邮件客户%s\", \"company\": \"邮件公司\", \"phone\": \"%s\", \"email\": \"%s@test.com\"}",
                            System.nanoTime(), phone, phone)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createTemplate(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/email-templates")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"邮件模板%d\", \"subject\": \"主题A\", \"content\": \"内容\"}",
                            System.nanoTime())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("退订流程：公开退订→名单→恢复")
  void unsubscribeFlow() throws Exception {
    String token = loginAndGetToken();

    // 公开退订
    mockMvc
        .perform(
            post("/api/v1/public/email/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"unsub@test.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("unsub@test.com"));

    // 名单
    mockMvc
        .perform(
            get("/api/v1/email/unsubscribes")
                .param("keyword", "unsub")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));

    // 恢复
    String list =
        mockMvc
            .perform(
                get("/api/v1/email/unsubscribes")
                    .param("keyword", "unsub")
                    .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(list).path("data").path("items").get(0).path("id").asLong();
    mockMvc
        .perform(
            delete("/api/v1/email/unsubscribes/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 恢复后名单为空
    mockMvc
        .perform(
            get("/api/v1/email/unsubscribes")
                .param("keyword", "unsub")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  @DisplayName("A/B 群发：创建成功 + 统计端点可用")
  void abCampaignFlow() throws Exception {
    String token = loginAndGetToken();
    long templateId = createTemplate(token);
    long c1 = createCustomer(token, "131" + (System.nanoTime() % 100000000));

    String resp =
        mockMvc
            .perform(
                post("/api/v1/email-campaigns")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"A/B测试\", \"templateId\": %d, \"sourceType\": \"CUSTOMER_IDS\", "
                                + "\"customerIds\": [%d], \"variant\": \"AB\", \"subjectB\": \"主题B\"}",
                            templateId, c1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.variant").value("AB"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long campaignId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 统计
    mockMvc
        .perform(
            get("/api/v1/email-campaigns/{id}/stats", campaignId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.variant").value("AB"))
        .andExpect(jsonPath("$.data.variantStats").isArray());
  }

  @Test
  @DisplayName("A/B 缺 B 主题 → 422")
  void abMissingSubjectThrows() throws Exception {
    String token = loginAndGetToken();
    long templateId = createTemplate(token);
    mockMvc
        .perform(
            post("/api/v1/email-campaigns")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"A/B缺B\", \"templateId\": %d, \"sourceType\": \"SEGMENT\", "
                            + "\"segmentId\": 1, \"variant\": \"AB\"}",
                        templateId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("EMAIL_AB_SUBJECT_REQUIRED"));
  }
}
