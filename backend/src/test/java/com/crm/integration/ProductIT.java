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

/** 产品集成测试（007 T014）：CRUD/权限 403/编码唯一 409。 */
class ProductIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("产品生命周期：创建→列表→编辑→逻辑删除")
  void productLifecycle() throws Exception {
    String token = loginAndGetToken();

    // 创建
    String createBody =
        """
        {"code": "P-IT-001", "name": "集成产品", "unit": "套", "standardPrice": 500000}
        """;
    String resp =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 列表搜索
    mockMvc
        .perform(
            get("/api/v1/products").header("Authorization", bearer(token)).param("keyword", "集成产品"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].code").value("P-IT-001"));

    // 编辑
    mockMvc
        .perform(
            put("/api/v1/products/{id}", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\": \"P-IT-001\", \"name\": \"集成产品-改\", \"standardPrice\": 600000, \"status\": \"INACTIVE\", \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("集成产品-改"))
        .andExpect(jsonPath("$.data.status").value("INACTIVE"));

    // 逻辑删除后列表不可见
    mockMvc
        .perform(delete("/api/v1/products/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/products")
                .header("Authorization", bearer(token))
                .param("keyword", "集成产品-改"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  @DisplayName("编码重复创建返回 409 PRODUCT_DUPLICATE")
  void duplicateCodeThrows409() throws Exception {
    String token = loginAndGetToken();
    String body = "{\"code\": \"P-IT-DUP\", \"name\": \"重复产品\"}";
    mockMvc
        .perform(
            post("/api/v1/products")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/products")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_DUPLICATE"));
  }

  @Test
  @DisplayName("非管理员创建产品返回 403")
  void nonAdminCreateForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"supportprod\", \"password\": \"Passw0rd!\", \"displayName\": \"产品支持\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("supportprod", "Passw0rd!");

    mockMvc
        .perform(
            post("/api/v1/products")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"P-IT-X\", \"name\": \"无权产品\"}"))
        .andExpect(status().isForbidden());
  }
}
