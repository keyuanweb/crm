package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KPI 大屏集成测试（023 T003）：聚合接口 + 权限。 */
class KpiBoardIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("ADMIN 访问 kpi-board 返回全部区块")
  void kpiBoardReturnsAllSections() throws Exception {
    String token = loginAndGetToken(); // admin

    mockMvc
        .perform(get("/api/v1/stats/kpi-board").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.kpi").exists())
        .andExpect(jsonPath("$.data.funnel").exists())
        .andExpect(jsonPath("$.data.leaderboard").isArray())
        .andExpect(jsonPath("$.data.healthDistribution").exists())
        .andExpect(jsonPath("$.data.suggestions").exists())
        .andExpect(jsonPath("$.data.trend").isArray());
  }

  @Test
  @DisplayName("SALES 角色访问 kpi-board 被拒 403")
  void salesForbidden() throws Exception {
    // 创建并登录销售用户
    String adminToken = loginAndGetToken();
    String createResp =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/users")
                    .header("Authorization", bearer(adminToken))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\": \"kpisales\", \"displayName\": \"大屏销售\", \"password\": \"pass1234\", \"role\": \"SALES\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String salesToken = loginAndGetToken("kpisales", "pass1234");

    mockMvc
        .perform(get("/api/v1/stats/kpi-board").header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());
  }
}
