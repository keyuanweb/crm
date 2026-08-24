package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 自定义报表集成测试（021 T004）：聚合查询 + 校验 + 导出。 */
class CustomReportsIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"报表测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private void createSalesOpportunity(String token, long customerId, long amount, String stage)
      throws Exception {
    String oppResp =
        mockMvc
            .perform(
                post("/api/v1/opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"name\": \"报表商机\", \"expectedAmountMin\": 100000, \"expectedAmountMax\": 500000}",
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
                        "{\"opportunityId\": %d, \"amount\": %d, \"stage\": \"%s\"}",
                        oppId, amount, stage)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("按阶段聚合：返回行与合计")
  void stageReport() throws Exception {
    String token = loginAndGetToken();
    long c1 = createCustomer(token, "报表客户A");
    createSalesOpportunity(token, c1, 300000L, "INITIAL_CONTACT");
    createSalesOpportunity(token, c1, 200000L, "NEGOTIATING");

    mockMvc
        .perform(
            post("/api/v1/reports/query")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"dimension\":\"STAGE\",\"metric\":\"AMOUNT\","
                        + "\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rows").isArray())
        .andExpect(jsonPath("$.data.totalAmount").value(500000));
  }

  @Test
  @DisplayName("非法时间范围返回 422")
  void invalidDateRangeThrows() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            post("/api/v1/reports/query")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"dimension\":\"STAGE\",\"metric\":\"AMOUNT\","
                        + "\"startDate\":\"2026-08-31\",\"endDate\":\"2026-08-01\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("导出报表返回 xlsx")
  void exportReport() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            get("/api/v1/reports/export")
                .header("Authorization", bearer(token))
                .param("dimension", "STAGE")
                .param("metric", "AMOUNT")
                .param("startDate", "2026-08-01")
                .param("endDate", "2026-08-31"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(
                    MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
  }
}
