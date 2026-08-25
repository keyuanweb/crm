package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 多币种集成测试（057 T012）：汇率 CRUD/折算/产品价/基准保护。 */
class MultiCurrencyIT extends AbstractIntegrationTest {

  private long createProduct(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"code\": \"MC-%d\", \"name\": \"多币种产品\", \"standardPrice\": 100000}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("多币种流程：新增汇率→折算→产品价→基准保护")
  void multiCurrencyFlow() throws Exception {
    String token = loginAndGetToken();

    // H2 无 seed：先创建 CNY 基准
    String cnyResp =
        mockMvc
            .perform(
                post("/api/v1/currencies")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\": \"CNY\", \"name\": \"人民币\", \"rate\": 1}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long cnyId = objectMapper.readTree(cnyResp).path("data").path("id").asLong();

    // 新增 USD 汇率
    mockMvc
        .perform(
            post("/api/v1/currencies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"USD\", \"name\": \"美元\", \"rate\": 7.2}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.rate").value(7.2));

    // 折算 100000 CNY → USD ≈ 13889
    mockMvc
        .perform(
            post("/api/v1/currencies/convert")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100000, \"fromCurrency\": \"CNY\", \"toCurrency\": \"USD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.convertedAmount").value(13889));

    // 基准保护：更新 CNY → 422
    mockMvc
        .perform(
            put("/api/v1/currencies/{id}", cnyId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"CNY\", \"name\": \"人民币\", \"rate\": 1, \"version\": 0}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CURRENCY_BASE_IMMUTABLE"));

    // 产品设置 USD 价（upsert）
    long productId = createProduct(token);
    mockMvc
        .perform(
            post("/api/v1/products/{id}/prices", productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currencyCode\": \"USD\", \"price\": 14000}"))
        .andExpect(status().isCreated());

    // 产品价格视图：配置价优先
    mockMvc
        .perform(get("/api/v1/products/{id}/prices", productId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.basePrice").value(100000))
        .andExpect(jsonPath("$.data.prices[0].currencyCode").value("USD"))
        .andExpect(jsonPath("$.data.prices[0].price").value(14000))
        .andExpect(jsonPath("$.data.prices[0].configured").value(true));

    // 删除产品价 → 视图回退折算价
    mockMvc
        .perform(
            delete("/api/v1/products/{id}/prices/USD", productId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/products/{id}/prices", productId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.prices[0].configured").value(false));
  }

  @Test
  @DisplayName("重复币种 → 409")
  void duplicateCurrencyThrows() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/currencies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"EUR\", \"name\": \"欧元\", \"rate\": 8.5}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/currencies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"EUR\", \"name\": \"欧元\", \"rate\": 8.5}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CURRENCY_CODE_DUPLICATE"));
  }
}
