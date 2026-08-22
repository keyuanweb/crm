package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 报价单集成测试（007 T021/T028/T032）：创建→编辑→提交→审批→PDF。 */
class QuoteIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"报价测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createProduct(String token, String code, String name, long price) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"code\": \"%s\", \"name\": \"%s\", \"unit\": \"套\", \"standardPrice\": %d}",
                            code, name, price)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createQuote(String token, long customerId, long productId) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/quotes")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"items\": [{\"productId\": %d, \"quantity\": 2, \"discount\": 0.75}]}",
                            customerId, productId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("报价生命周期：创建→编辑→提交→审批通过→拒绝需意见")
  void quoteLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "报价客户A");
    long productId = createProduct(token, "Q-IT-001", "报价产品", 100000);

    // 1. 创建（自动算额：100000×2×0.75 = 150000）
    long quoteId = createQuote(token, customerId, productId);
    mockMvc
        .perform(get("/api/v1/quotes/{id}", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalAmount").value(150000));

    // 2. 详情（含行明细快照）
    String detailResp =
        mockMvc
            .perform(get("/api/v1/quotes/{id}", quoteId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.items[0].productName").value("报价产品"))
            .andExpect(jsonPath("$.data.items[0].lineTotal").value(150000))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    JsonNode detail = objectMapper.readTree(detailResp);
    assertThat(detail.path("data").path("customerName").asText()).isEqualTo("报价客户A");

    // 3. 编辑（改数量 3 → 总额 100000×3×0.75 = 225000）
    mockMvc
        .perform(
            put("/api/v1/quotes/{id}", quoteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"items\": [{\"productId\": %d, \"quantity\": 3, \"discount\": 0.75}], \"version\": 0}",
                        customerId, productId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalAmount").value(225000));

    // 4. 提交审批
    mockMvc
        .perform(post("/api/v1/quotes/{id}/submit", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

    // 5. 提交后不可编辑
    mockMvc
        .perform(
            put("/api/v1/quotes/{id}", quoteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"items\": [{\"productId\": %d, \"quantity\": 1, \"discount\": 1}], \"version\": 0}",
                        customerId, productId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("QUOTE_INVALID_STATE"));

    // 6. 拒绝需意见 → 400
    mockMvc
        .perform(post("/api/v1/quotes/{id}/reject", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());

    // 7. 审批通过
    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/approve", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.approverId").isNumber());

    // 8. 终态重复审批 → 409
    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/approve", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("QUOTE_INVALID_STATE"));
  }

  @Test
  @DisplayName("拒绝流程：提交→拒绝填意见→可编辑重提")
  void rejectAndResubmit() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "报价客户B");
    long productId = createProduct(token, "Q-IT-002", "拒绝产品", 50000);
    long quoteId = createQuote(token, customerId, productId);
    mockMvc
        .perform(get("/api/v1/quotes/{id}", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalAmount").value(75000)); // 50000×2×0.75

    mockMvc
        .perform(post("/api/v1/quotes/{id}/submit", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/reject", quoteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"价格过高\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"))
        .andExpect(jsonPath("$.data.rejectReason").value("价格过高"));

    // 被拒后可编辑（改折扣 0.5；先取当前 version）
    String afterReject =
        mockMvc
            .perform(get("/api/v1/quotes/{id}", quoteId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    int version = objectMapper.readTree(afterReject).path("data").path("version").asInt();
    mockMvc
        .perform(
            put("/api/v1/quotes/{id}", quoteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"items\": [{\"productId\": %d, \"quantity\": 2, \"discount\": 0.5}], \"version\": %d}",
                        customerId, productId, version)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalAmount").value(50000));

    // 重提
    mockMvc
        .perform(post("/api/v1/quotes/{id}/submit", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.data.rejectReason").doesNotExist());
  }

  @Test
  @DisplayName("非管理员审批返回 403")
  void nonAdminApproveForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"salesquote\", \"password\": \"Passw0rd!\", \"displayName\": \"报价销售\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("salesquote", "Passw0rd!");

    long customerId = createCustomer(salesToken, "报价客户C");
    long productId = createProduct(adminToken, "Q-IT-003", "权限产品", 30000);
    long quoteId = createQuote(salesToken, customerId, productId);
    mockMvc
        .perform(get("/api/v1/quotes/{id}", quoteId).header("Authorization", bearer(salesToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalAmount").value(45000)); // 30000×2×0.75
    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/submit", quoteId).header("Authorization", bearer(salesToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/approve", quoteId)
                .header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PDF 导出：返回 application/pdf 且非空")
  void pdfExport() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "报价客户D");
    long productId = createProduct(token, "Q-IT-004", "PDF产品", 200000);
    long quoteId = createQuote(token, customerId, productId);

    byte[] bytes =
        mockMvc
            .perform(get("/api/v1/quotes/{id}/pdf", quoteId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(bytes).isNotEmpty();
    // PDF 魔数 %PDF
    assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF");
  }
}
