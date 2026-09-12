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

  /** 建一个带邮箱的客户：只有邮箱非空才会成为收件人，否则这批发信记录是空的、断言无从谈起。 */
  private long createCustomerWithEmail(String token, String email) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"邮件测试客户\",\"company\":\"XX 科技\",\"email\":\"" + email + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  /**
   * 一期诚信修复的端到端回归：未配置 SMTP 时，一个字节都没发出去，任何一层都不许说"已发送"。
   *
   * <p>测试 profile 未配置 {@code crm.mail.host}，走的正是生产上"没接邮件服务"的情形——改造前这里返回 DONE、sentCount = 收件人数，
   * 打开率/点击率再以这个假分母计算，销售与市场据此做决策。
   */
  @Test
  @DisplayName("未配置 SMTP：批次与每封记录均为 SKIPPED，统计口径不含未发出的邮件")
  void campaignWithoutSmtpRecordsSkippedNotSent() throws Exception {
    String token = loginAndGetToken();
    long templateId = createTemplate(token);
    long customerId = createCustomerWithEmail(token, "smtp-check@example.com");

    String campaignResp =
        mockMvc
            .perform(
                post("/api/v1/email-campaigns")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"未配置SMTP\",\"templateId\":"
                            + templateId
                            + ",\"sourceType\":\"CUSTOMER_IDS\",\"customerIds\":["
                            + customerId
                            + "]}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode campaign = objectMapper.readTree(campaignResp).path("data");
    long campaignId = campaign.path("id").asLong();

    // 收件人先断言：若这一条先失败，说明客户邮箱没落库，后面的状态断言就无从谈起
    org.assertj.core.api.Assertions.assertThat(campaign.path("totalCount").asInt()).isEqualTo(1);
    // 批次：SKIPPED 而不是 DONE，成功数 0 而不是收件人数
    org.assertj.core.api.Assertions.assertThat(campaign.path("status").asText())
        .isEqualTo("SKIPPED");
    org.assertj.core.api.Assertions.assertThat(campaign.path("sentCount").asInt()).isZero();

    // 每封的真实状态与原因：前端据此显示"未发送"并在悬停时给出原因
    mockMvc
        .perform(
            get("/api/v1/email-campaigns/" + campaignId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].status").value("SKIPPED"))
        .andExpect(
            jsonPath("$.data.items[0].errorMessage")
                .value(org.hamcrest.Matchers.containsString("SMTP 未配置")));

    // 统计口径：skipped 单列，sent 为 0，打开/点击率不再用假分母算出一个"看起来有转化"的数字
    mockMvc
        .perform(
            get("/api/v1/email-campaigns/" + campaignId + "/stats")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sent").value(0))
        .andExpect(jsonPath("$.data.skipped").value(1))
        .andExpect(jsonPath("$.data.openRate").value(0.0));
  }
}
