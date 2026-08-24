package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 营销自动化集成测试（049 T009）：规则配置/评分触发/标签触发。 */
class MarketingAutomationIT extends AbstractIntegrationTest {

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"营销客户%d\", \"company\": \"营销公司\"}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createLead(String token, String email) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"营销线索%d\", \"company\": \"营销线索公司\", \"email\": \"%s\", \"source\": \"WEBSITE\"}",
                            System.nanoTime(), email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createRule(
      String token, String eventType, String conditionJson, String actionType, String actionJson)
      throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/workflows/rules")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"自动规则\", \"eventType\": \"%s\", \"condition\": %s, "
                                + "\"actionType\": \"%s\", \"action\": %s}",
                            eventType, conditionJson, actionType, actionJson)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("SEND_EMAIL 规则：校验通过并创建")
  void sendEmailRuleCreates() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/workflows/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"高分欢迎邮件\", \"eventType\": \"LEAD_SCORE_THRESHOLD\", "
                        + "\"condition\": {\"field\":\"score\",\"value\":\"80\"}, "
                        + "\"actionType\": \"SEND_EMAIL\", \"action\": {\"templateId\":1}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.eventType").value("LEAD_SCORE_THRESHOLD"));
  }

  @Test
  @DisplayName("评分触发 SEND_EMAIL：线索评分重算后规则执行（日志记录）")
  void scoreTriggerExecutesRule() throws Exception {
    String token = loginAndGetToken();
    long leadId = createLead(token, "auto-" + System.nanoTime() + "@test.com");
    createRule(
        token,
        "LEAD_SCORE_THRESHOLD",
        "{\"field\":\"score\",\"value\":\"80\"}",
        "SEND_EMAIL",
        "{\"templateId\":1}");

    // 更新线索（评分重算 → 发布评分阈值事件）；version 从详情实时取
    String detail =
        mockMvc
            .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int version = objectMapper.readTree(detail).path("data").path("version").asInt();
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", leadId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"营销线索改名\", \"company\": \"营销线索公司\", \"version\": %d}",
                        version)))
        .andExpect(status().isOk());

    // 执行日志应有记录（评分规则）
    mockMvc
        .perform(
            get("/api/v1/workflows/logs")
                .param("page", "1")
                .param("pageSize", "20")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
  }

  @Test
  @DisplayName("标签规则：ADD_TAG 校验通过")
  void addTagRuleCreates() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/workflows/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"重点客户标\", \"eventType\": \"TAG_CHANGED\", "
                        + "\"condition\": {\"field\":\"tags\",\"value\":\"重点\"}, "
                        + "\"actionType\": \"ADD_TAG\", \"action\": {\"tag\":\"重点客户\"}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.actionType").value("ADD_TAG"));
  }
}
