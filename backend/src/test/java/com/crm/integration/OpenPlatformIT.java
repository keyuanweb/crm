package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 开放平台集成测试（055 T015）：Key 鉴权/吊销/Webhook 订阅。 */
class OpenPlatformIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("API Key 流程：创建→鉴权访问→吊销 401")
  void apiKeyFlow() throws Exception {
    String token = loginAndGetToken();

    // 创建 Key
    String keyResp =
        mockMvc
            .perform(
                post("/api/v1/platform/api-keys")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"数据同步\", \"scopes\": [\"customer:read\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.key").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String apiKey = objectMapper.readTree(keyResp).path("data").path("key").asText();
    long keyId = objectMapper.readTree(keyResp).path("data").path("id").asLong();

    // 无 Key → 401
    mockMvc
        .perform(get("/api/v1/open/customers"))
        .andExpect(status().isUnauthorized());

    // 有效 Key → 200（customer:read 有权限）
    mockMvc
        .perform(get("/api/v1/open/customers").header("X-API-Key", apiKey))
        .andExpect(status().isOk());

    // 吊销
    mockMvc
        .perform(
            post("/api/v1/platform/api-keys/{id}/revoke", keyId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 吊销后 401
    mockMvc
        .perform(get("/api/v1/open/customers").header("X-API-Key", apiKey))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("API Key 范围不足 → 403")
  void apiKeyScopeDenied() throws Exception {
    String token = loginAndGetToken();
    String keyResp =
        mockMvc
            .perform(
                post("/api/v1/platform/api-keys")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"只读\", \"scopes\": [\"customer:read\"]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String apiKey = objectMapper.readTree(keyResp).path("data").path("key").asText();

    // 无 lead:write 权限 → 403
    mockMvc
        .perform(
            post("/api/v1/open/leads")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权线索\", \"company\": \"X\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Webhook 订阅流程：创建→列表→toggle")
  void webhookFlow() throws Exception {
    String token = loginAndGetToken();

    String resp =
        mockMvc
            .perform(
                post("/api/v1/platform/webhooks")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\": \"LEAD_CREATED\", \"callbackUrl\": \"http://localhost:9999/hook\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.secret").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode data = objectMapper.readTree(resp).path("data");
    long webhookId = data.path("id").asLong();
    String secret = data.path("secret").asText();
    assertThatCustom(secret.length() > 10);

    mockMvc
        .perform(get("/api/v1/platform/webhooks").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].eventType").value("LEAD_CREATED"));

    mockMvc
        .perform(
            post("/api/v1/platform/webhooks/{id}/toggle", webhookId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));
  }

  private void assertThatCustom(boolean condition) {
    if (!condition) {
      throw new AssertionError("condition failed");
    }
  }
}
