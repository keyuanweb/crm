package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 全局搜索集成测试（032 T002）。 */
class SearchIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("搜索返回分组结果（客户组 + path）")
  void searchGroups() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            get("/api/v1/search").param("keyword", "客户").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.groups").isArray());
  }

  @Test
  @DisplayName("结果页按类型过滤")
  void searchFullByType() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            get("/api/v1/search/full")
                .param("keyword", "CRM")
                .param("type", "PRODUCT")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.groups").isArray());
  }
}
