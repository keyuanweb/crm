package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 商机管道统计集成测试（T048，US6）。 */
class StatsIT extends AbstractIntegrationTest {

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"统计客户\", \"company\": \"统计公司\"}"))
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
                    .content(String.format("{\"customerId\": %d, \"name\": \"统计商机\"}", customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createSalesOpportunity(String token, long oppId, long amount, String stage)
      throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"opportunityId\": %d, \"amount\": %d, \"stage\": \"%s\"}",
                            oppId, amount, stage)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("各阶段数量与金额汇总正确（FR-011/SC-006）")
  void pipelineStatsAggregatesCorrectly() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppA = createOpportunity(token, customerId);
    long oppB = createOpportunity(token, customerId);
    createSalesOpportunity(token, oppA, 100000, "INITIAL_CONTACT"); // 10 万
    createSalesOpportunity(token, oppA, 200000, "NEGOTIATING"); // 20 万
    createSalesOpportunity(token, oppB, 300000, "NEGOTIATING"); // 30 万

    mockMvc
        .perform(get("/api/v1/stats/opportunity-pipeline").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.grandTotal.count").value(3))
        .andExpect(jsonPath("$.data.grandTotal.amountTotal").value(600000))
        .andExpect(jsonPath("$.data.stages[0].stage").value("INITIAL_CONTACT"))
        .andExpect(jsonPath("$.data.stages[0].count").value(1))
        .andExpect(jsonPath("$.data.stages[0].amountTotal").value(100000))
        .andExpect(jsonPath("$.data.stages[1].stage").value("NEGOTIATING"))
        .andExpect(jsonPath("$.data.stages[1].count").value(2))
        .andExpect(jsonPath("$.data.stages[1].amountTotal").value(500000));
  }

  @Test
  @DisplayName("关闭为赢单后统计同步更新")
  void closeUpdatesStats() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    long soId = createSalesOpportunity(token, oppId, 400000, "NEGOTIATING");

    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closeResult\": \"WON\", \"version\": 0}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/stats/opportunity-pipeline").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stages[2].stage").value("CLOSED_WON"))
        .andExpect(jsonPath("$.data.stages[2].count").value(1))
        .andExpect(jsonPath("$.data.stages[2].amountTotal").value(400000))
        .andExpect(jsonPath("$.data.stages[1].count").value(0));
  }
}
