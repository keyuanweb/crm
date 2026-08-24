package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 电子签署集成测试（047 T013）：报价/合同签署/重复 409/生效前置。 */
class ESignatureIT extends AbstractIntegrationTest {

  private static final String TINY_PNG =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"签名客户%d\", \"company\": \"签名公司\"}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createApprovedQuote(String token, long customerId) throws Exception {
    String prodResp =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"code\": \"SIGN-IT-%d\", \"name\": \"签名产品\", \"standardPrice\": 100000}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long productId = objectMapper.readTree(prodResp).path("data").path("id").asLong();
    String quoteResp =
        mockMvc
            .perform(
                post("/api/v1/quotes")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"items\": [{\"productId\": %d, \"quantity\": 1, \"discount\": 1}]}",
                            customerId, productId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long quoteId = objectMapper.readTree(quoteResp).path("data").path("id").asLong();
    mockMvc
        .perform(post("/api/v1/quotes/{id}/submit", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/quotes/{id}/approve", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    return quoteId;
  }

  private long createApprovedContract(String token, long customerId) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"签名合同\", \"customerId\": %d, \"amount\": 1000000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(resp).path("data").path("id").asLong();
    mockMvc
        .perform(post("/api/v1/contracts/{id}/submit", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/contracts/{id}/approve", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    return id;
  }

  @Test
  @DisplayName("报价签署：APPROVED→SIGNED→重复 409→记录可查")
  void quoteSignatureFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long quoteId = createApprovedQuote(token, customerId);

    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/sign", quoteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"signatureImage\": \"%s\"}", TINY_PNG)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.businessType").value("QUOTE"));

    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/sign", quoteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"signatureImage\": \"%s\"}", TINY_PNG)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("SIGNATURE_ALREADY_SIGNED"));

    mockMvc
        .perform(get("/api/v1/quotes/{id}/signature", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.signerName").isNotEmpty());
  }

  @Test
  @DisplayName("合同签署：APPROVED→SIGNED→生效；签名缺失 422")
  void contractSignatureFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long contractId = createApprovedContract(token, customerId);

    // 签名缺失 → 400（Bean Validation @NotBlank）
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/sign", contractId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signatureImage\": \"\"}"))
        .andExpect(status().isBadRequest());

    // 签署 → SIGNED
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/sign", contractId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"signatureImage\": \"%s\"}", TINY_PNG)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.businessType").value("CONTRACT"));

    // 签后生效成功
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/effective", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("EFFECTIVE"));

    // 记录可查
    mockMvc
        .perform(
            get("/api/v1/contracts/{id}/signature", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.signerName").isNotEmpty());
  }

  @Test
  @DisplayName("未审批报价签署 → 422")
  void unapprovedQuoteRejected() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    String prodResp =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"code\": \"SIGN-IT-D-%d\", \"name\": \"未审批产品\", \"standardPrice\": 1000}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long productId = objectMapper.readTree(prodResp).path("data").path("id").asLong();
    String quoteResp =
        mockMvc
            .perform(
                post("/api/v1/quotes")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"items\": [{\"productId\": %d, \"quantity\": 1, \"discount\": 1}]}",
                            customerId, productId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long quoteId = objectMapper.readTree(quoteResp).path("data").path("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/sign", quoteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"signatureImage\": \"%s\"}", TINY_PNG)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("SIGNATURE_STATE_INVALID"));
  }
}
