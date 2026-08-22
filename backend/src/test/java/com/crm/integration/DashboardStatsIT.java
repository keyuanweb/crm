package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 销售仪表盘集成测试（006 T009）：dashboard 聚合端点六段数据正确性。 */
class DashboardStatsIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"仪表盘测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("仪表盘聚合：六段数据齐全且结构正确")
  void dashboardSections() throws Exception {
    String token = loginAndGetToken();
    createCustomer(token, "仪表盘客户A");

    mockMvc
        .perform(get("/api/v1/stats/dashboard").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary").exists())
        .andExpect(jsonPath("$.data.summary.customerCount").isNumber())
        .andExpect(jsonPath("$.data.funnel.stages").isArray())
        .andExpect(jsonPath("$.data.forecast.weightedAmount").isNumber())
        .andExpect(jsonPath("$.data.performance.month").isNotEmpty())
        .andExpect(jsonPath("$.data.followUps.total").isNumber())
        .andExpect(jsonPath("$.data.stalledOpportunities").isArray())
        .andExpect(jsonPath("$.data.generatedAt").isNotEmpty());
  }

  @Test
  @DisplayName("仪表盘：未设目标时 performance.configured=false")
  void performanceNotConfigured() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(get("/api/v1/stats/dashboard").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.performance.configured").value(false))
        .andExpect(jsonPath("$.data.performance.targetAmount").doesNotExist());
  }
}
