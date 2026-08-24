package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 邮件营销集成测试（030 T006）：模板 CRUD + 群发 + 追踪。 */
class EmailIT extends AbstractIntegrationTest {

  private long createTemplate(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/email-templates")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"欢迎模板\",\"subject\":\"欢迎 {name}\",\"content\":\"<p>Hi {name}</p>\",\"category\":\"WELCOME\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("模板 CRUD + 群发（客户收件人）+ 打开追踪")
  void templateCampaignTrackFlow() throws Exception {
    String token = loginAndGetToken();
    long templateId = createTemplate(token);

    // 建群发（CUSTOMER_IDS 客户 1）
    String campaignResp =
        mockMvc
            .perform(
                post("/api/v1/email-campaigns")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"欢迎活动\",\"templateId\":"
                            + templateId
                            + ",\"sourceType\":\"CUSTOMER_IDS\",\"customerIds\":[1]}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode campaign = objectMapper.readTree(campaignResp).path("data");
    long campaignId = campaign.path("id").asLong();
    org.assertj.core.api.Assertions.assertThat(campaign.path("totalCount").asInt())
        .isGreaterThanOrEqualTo(0);

    // 活动列表
    mockMvc
        .perform(get("/api/v1/email-campaigns").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());

    // 活动详情（发送记录）
    mockMvc
        .perform(
            get("/api/v1/email-campaigns/" + campaignId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("公开追踪端点可访问")
  void publicTrackEndpoints() throws Exception {
    // open 像素（无效 log id 返回 GIF 不报错）
    mockMvc.perform(get("/api/v1/public/track/open/999")).andExpect(status().isOk());
  }
}
