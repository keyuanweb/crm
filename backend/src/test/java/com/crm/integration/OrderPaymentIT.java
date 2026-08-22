package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 订单与回款集成测试（009 T013/T024/T029）：创建→期次→回款→台账→提醒。 */
class OrderPaymentIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"订单测试公司\"}"))
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
                            "{\"title\": \"订单合同-%s\", \"customerId\": %d, \"amount\": 300000}",
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
            "{\"title\": \"订单IT-001\", \"customerId\": %d, \"contractId\": %d,"
                + " \"plans\": [{\"amount\": 100000, \"dueDate\": \"%s\", \"description\": \"首期\"},"
                + "             {\"amount\": 200000, \"dueDate\": \"%s\", \"description\": \"尾期\"}]}",
            customerId, contractId, dueDate, dueDate);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.orderNo").value(org.hamcrest.Matchers.startsWith("SO-")))
            .andExpect(jsonPath("$.data.amount").value(300000))
            .andExpect(jsonPath("$.data.plans.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("订单生命周期：基于合同创建→回款→状态驱动→超额拒绝→删除")
  void orderLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "订单客户A");
    long contractId = createEffectiveContract(token, customerId);
    long orderId = createOrder(token, customerId, contractId);

    // 详情：2 期计划，未收=应收
    String detailResp =
        mockMvc
            .perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode detail = objectMapper.readTree(detailResp);
    long planId = detail.path("data").path("plans").get(0).path("id").asLong();

    // 登记第 1 期全额回款
    mockMvc
        .perform(
            post("/api/v1/orders/{id}/payments", orderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"planId\": %d, \"amount\": 100000, \"paidAt\": \"%s\", \"method\": \"TRANSFER\"}",
                        planId, LocalDate.now())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PARTIAL"))
        .andExpect(jsonPath("$.data.paidAmount").value(100000))
        .andExpect(jsonPath("$.data.plans[0].status").value("PAID"))
        .andExpect(jsonPath("$.data.plans[0].receivedAmount").value(100000))
        .andExpect(jsonPath("$.data.plans[0].unpaidAmount").value(0));

    // 超额登记第 1 期 → 400
    mockMvc
        .perform(
            post("/api/v1/orders/{id}/payments", orderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"planId\": %d, \"amount\": 999999, \"paidAt\": \"%s\", \"method\": \"TRANSFER\"}",
                        planId, LocalDate.now())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_EXCEEDS"));

    // 登记第 2 期 → 订单 PAID
    String detail2 =
        mockMvc
            .perform(get("/api/v1/orders/{id}", orderId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long plan2Id =
        objectMapper.readTree(detail2).path("data").path("plans").get(1).path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/orders/{id}/payments", orderId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"planId\": %d, \"amount\": 200000, \"paidAt\": \"%s\", \"method\": \"TRANSFER\"}",
                        plan2Id, LocalDate.now())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"));

    // 存在回款记录 → 删除 409
    mockMvc
        .perform(delete("/api/v1/orders/{id}", orderId).header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ORDER_HAS_PAYMENTS"));
  }

  @Test
  @DisplayName("期次金额合计不等于订单金额返回 400")
  void planAmountMismatchThrows400() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "订单客户B");
    String dueDate = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
    String body =
        String.format(
            "{\"title\": \"订单IT-002\", \"customerId\": %d, \"amount\": 100000,"
                + " \"plans\": [{\"amount\": 30000, \"dueDate\": \"%s\"}, {\"amount\": 40000, \"dueDate\": \"%s\"}]}",
            customerId, dueDate, dueDate);
    mockMvc
        .perform(
            post("/api/v1/orders")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PLAN_AMOUNT_MISMATCH"));
  }

  @Test
  @DisplayName("未提供期次自动生成一期")
  void autoSinglePlan() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "订单客户C");
    String resp =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"订单IT-003\", \"customerId\": %d, \"amount\": 50000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.plans.length()").value(1))
            .andExpect(jsonPath("$.data.plans[0].amount").value(50000))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(
            objectMapper
                .readTree(resp)
                .path("data")
                .path("plans")
                .get(0)
                .path("reminderStatus")
                .asText())
        .isIn("NORMAL", "DUE_SOON");
  }

  @Test
  @DisplayName("逾期期次标识 OVERDUE 且提醒汇总正确")
  void overdueReminder() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "订单客户D");
    // 计划日期设为过去（逾期）
    String pastDate = LocalDate.now().minusDays(10).format(DateTimeFormatter.ISO_LOCAL_DATE);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"订单IT-004\", \"customerId\": %d, \"amount\": 50000,"
                                + " \"plans\": [{\"amount\": 50000, \"dueDate\": \"%s\"}]}",
                            customerId, pastDate)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode plans = objectMapper.readTree(resp).path("data").path("plans");
    assertThat(plans.get(0).path("reminderStatus").asText()).isEqualTo("OVERDUE");
    assertThat(plans.get(0).path("overdueDays").asLong()).isEqualTo(10L);

    // 提醒汇总
    mockMvc
        .perform(get("/api/v1/orders/reminder-summary").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.overdueCount").isNumber())
        .andExpect(jsonPath("$.data.dueSoonCount").isNumber());
  }

  @Test
  @DisplayName("非管理员删除订单返回 403")
  void nonAdminDeleteForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"salesorder\", \"password\": \"Passw0rd!\", \"displayName\": \"订单销售\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("salesorder", "Passw0rd!");

    long customerId = createCustomer(salesToken, "订单客户E");
    String resp =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header("Authorization", bearer(salesToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"订单IT-005\", \"customerId\": %d, \"amount\": 50000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long orderId = objectMapper.readTree(resp).path("data").path("id").asLong();

    mockMvc
        .perform(delete("/api/v1/orders/{id}", orderId).header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());
  }
}
