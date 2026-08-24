package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 线索评分与预测校准集成测试（019 T012）：自动评分 + 评分排序 + 预测概率来源。 */
class LeadScoringIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("创建线索自动评分：高分线索（REFERRAL+信息全）评分高于低分线索")
  void autoScoring() throws Exception {
    String token = loginAndGetToken();

    // 高分线索：REFERRAL + company/title/phone/email 全 → 30+30+15 = 75（无跟进而互动满分）
    String highResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"高分线索\",\"company\":\"Acme\",\"title\":\"CTO\","
                            + "\"phone\":\"13800138000\",\"email\":\"a@b.com\",\"source\":\"REFERRAL\"}"))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.score").value(org.hamcrest.Matchers.greaterThanOrEqualTo(70)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // 低分线索：COLD_CALL + 仅 company/电话 → 10+7.5+15 = 32.5 → 低于 60
    String lowResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"低分线索\",\"company\":\"小公司\",\"phone\":\"13900139000\",\"source\":\"COLD_CALL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.score").value(org.hamcrest.Matchers.lessThan(60)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    long highId = objectMapper.readTree(highResp).path("data").path("id").asLong();
    long lowId = objectMapper.readTree(lowResp).path("data").path("id").asLong();

    // 列表默认按评分降序：高分线索排在低分线索前
    String listResp =
        mockMvc
            .perform(get("/api/v1/leads?page=1&pageSize=50").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var items = objectMapper.readTree(listResp).path("data").path("items");
    int highIdx = -1;
    int lowIdx = -1;
    for (int i = 0; i < items.size(); i++) {
      long id = items.get(i).path("id").asLong();
      if (id == highId) {
        highIdx = i;
      }
      if (id == lowId) {
        lowIdx = i;
      }
    }
    org.assertj.core.api.Assertions.assertThat(highIdx).isGreaterThan(-1);
    org.assertj.core.api.Assertions.assertThat(lowIdx).isGreaterThan(-1);
    org.assertj.core.api.Assertions.assertThat(highIdx).isLessThan(lowIdx);
  }

  @Test
  @DisplayName("预测接口返回概率来源标注（probabilitySource）")
  void forecastProbabilitySource() throws Exception {
    String token = loginAndGetToken();
    // 创建客户+销售机会，确保 forecast.breakdown 非空
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"预测客户\",\"company\":\"预测公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();
    String oppResp =
        mockMvc
            .perform(
                post("/api/v1/opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"name\": \"预测商机\", \"expectedAmountMin\": 100000, \"expectedAmountMax\": 500000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long oppId = objectMapper.readTree(oppResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"opportunityId\": %d, \"amount\": 300000, \"stage\": \"INITIAL_CONTACT\"}",
                        oppId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/stats/dashboard").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.forecast.breakdown").isArray())
        .andExpect(jsonPath("$.data.forecast.breakdown[0].probabilitySource").exists());
  }
}
