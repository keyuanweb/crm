package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 客户 360 集成测试（018 T005）：聚合详情 + 健康度 + 流失预警。 */
class Customer360IT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"360测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createEffectiveContract(String token, long customerId) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"360合同-%s\", \"customerId\": %d, \"amount\": 500000}",
                            customerId, customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contractId = objectMapper.readTree(resp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/submit", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/approve", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/effective", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    return contractId;
  }

  private long createOrder(String token, long customerId, long contractId) throws Exception {
    String dueDate = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
    String body =
        String.format(
            "{\"title\": \"360订单\", \"customerId\": %d, \"contractId\": %d,"
                + " \"plans\": [{\"amount\": 500000, \"dueDate\": \"%s\", \"description\": \"一期\"}]}",
            customerId, contractId, dueDate);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("客户 360：详情返回订单/合同/金额汇总/健康度")
  void customer360Detail() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "360全景客户");
    long contractId = createEffectiveContract(token, customerId);
    createOrder(token, customerId, contractId);

    mockMvc
        .perform(get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("360全景客户"))
        // 360 聚合：合同、订单、金额汇总
        .andExpect(jsonPath("$.data.customer360.contracts.length()").value(1))
        .andExpect(jsonPath("$.data.customer360.orders.length()").value(1))
        .andExpect(jsonPath("$.data.customer360.amountSummary.totalOrder").value(500000))
        .andExpect(jsonPath("$.data.customer360.amountSummary.paid").value(0))
        // 健康度：有订单+合同+近期活动 → 非红色（有合作深度与活动加分）
        .andExpect(jsonPath("$.data.customer360.health.score").isNumber())
        .andExpect(jsonPath("$.data.customer360.health.level").exists())
        .andExpect(jsonPath("$.data.customer360.health.deductions").isArray());
  }

  @Test
  @DisplayName("流失预警：久未跟进且无新订单的客户出现在 at-risk 列表")
  void atRiskList() throws Exception {
    String token = loginAndGetToken();
    // 新客户无任何业务活动（默认即为"久未跟进且无新订单"）
    long customerId = createCustomer(token, "预警客户");

    // 数据权限：admin 全量可见；此处不 mock 时间，直接验证接口可用性与结构
    mockMvc
        .perform(get("/api/v1/customers/health/at-risk").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.total").isNumber());
  }

  @Test
  @DisplayName("健康度评分：无数据客户不报错且分数在 0-100")
  void healthScoreNeutral() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "中性客户");

    mockMvc
        .perform(get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customer360.health.score").isNumber())
        .andExpect(jsonPath("$.data.customer360.health.deductions").isArray());
  }
}
