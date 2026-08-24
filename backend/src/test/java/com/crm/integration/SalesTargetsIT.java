package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 团队销售目标与排行集成测试（020 T004）：个人目标互不干扰 + 排行。 */
class SalesTargetsIT extends AbstractIntegrationTest {

  private long createUser(String token, String username) throws Exception {
    String resp =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\": \""
                            + username
                            + "\", \"displayName\": \"测试销售\", \"password\": \"pass1234\", \"role\": \"SALES\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("个人目标：不同销售设置互不干扰，可独立查询")
  void personalTargetsIndependent() throws Exception {
    String token = loginAndGetToken(); // admin
    long userA = createUser(token, "tgtsales_a");
    long userB = createUser(token, "tgtsales_b");

    // 销售 A 目标 100 万
    mockMvc
        .perform(
            put("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"month\":\"2026-08\",\"targetAmount\":1000000,\"userId\":" + userA + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetAmount").value(1000000))
        .andExpect(jsonPath("$.data.userId").value(userA));

    // 销售 B 目标 200 万
    mockMvc
        .perform(
            put("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"month\":\"2026-08\",\"targetAmount\":2000000,\"userId\":" + userB + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetAmount").value(2000000));

    // 独立查询：销售 A 目标不受 B 影响
    mockMvc
        .perform(
            get("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .param("month", "2026-08")
                .param("userId", String.valueOf(userA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetAmount").value(1000000));

    // 全局目标（userId 空）不受个人目标影响
    mockMvc
        .perform(
            get("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .param("month", "2026-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetAmount").doesNotExist());
  }

  @Test
  @DisplayName("团队排行：返回 items 数组与月份")
  void leaderboardReturnsItems() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            get("/api/v1/stats/leaderboard")
                .header("Authorization", bearer(token))
                .param("month", "2026-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.month").value("2026-08"));
  }
}
