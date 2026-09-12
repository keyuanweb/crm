package com.crm.integration;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 商机管道统计集成测试（T048，US6）。
 *
 * <p><b>断言必须按阶段码整体比对，不能按下标取值</b>：1.2 起漏斗列出的是阶段字典里**全部**阶段，顺序即 {@code sort_order}。按下标写的 {@code
 * stages[1]} 在字典插入新阶段后会静默指向另一个阶段——这类断言不会报 「找不到阶段」，只会报一个看起来莫名其妙的数字不符。整条链一起比，字典一变就当场红。
 */
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
        // 字典 6 个阶段全在列（含 0 商机的），顺序即 sort_order；金额单位是分
        .andExpect(
            jsonPath("$.data.stages[*].stage")
                .value(
                    contains(
                        "INITIAL_CONTACT",
                        "NEEDS_CONFIRMED",
                        "PROPOSAL_QUOTED",
                        "NEGOTIATING",
                        "CLOSED_WON",
                        "CLOSED_LOST")))
        .andExpect(jsonPath("$.data.stages[*].count").value(contains(1, 0, 0, 2, 0, 0)))
        .andExpect(
            jsonPath("$.data.stages[*].amountTotal").value(contains(100000, 0, 0, 500000, 0, 0)));
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
        // 关单后唯一有商机的是终态 CLOSED_WON；NEGOTIATING 已归零
        .andExpect(
            jsonPath("$.data.stages[*].stage")
                .value(
                    contains(
                        "INITIAL_CONTACT",
                        "NEEDS_CONFIRMED",
                        "PROPOSAL_QUOTED",
                        "NEGOTIATING",
                        "CLOSED_WON",
                        "CLOSED_LOST")))
        .andExpect(jsonPath("$.data.stages[*].count").value(contains(0, 0, 0, 0, 1, 0)))
        .andExpect(jsonPath("$.data.stages[*].amountTotal").value(contains(0, 0, 0, 0, 400000, 0)));
  }
}
