package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 智能建议集成测试（022 T003）：建议列表 + 摘要 + 忽略。 */
class SmartSuggestionIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("建议列表返回 items 数组与 total")
  void suggestionsList() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(get("/api/v1/suggestions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.total").isNumber());
  }

  @Test
  @DisplayName("建议摘要返回四类计数")
  void suggestionsSummary() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(get("/api/v1/suggestions/summary").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.atRiskCustomers").isNumber())
        .andExpect(jsonPath("$.data.stalledOpportunities").isNumber())
        .andExpect(jsonPath("$.data.followUpCustomers").isNumber())
        .andExpect(jsonPath("$.data.highScoreLeads").isNumber());
  }

  @Test
  @DisplayName("忽略建议返回成功")
  void ignoreSuggestion() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            post("/api/v1/suggestions/CUSTOMER_AT_RISK/999/ignore")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
