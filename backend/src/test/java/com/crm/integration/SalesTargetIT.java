package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 销售目标集成测试（006 T019）：PUT 幂等/GET/非 ADMIN 403/格式 400。 */
class SalesTargetIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("管理员：设置目标→重复设置幂等更新→查询返回")
  void adminUpsertAndGet() throws Exception {
    String token = loginAndGetToken(); // admin

    // 首次设置
    mockMvc
        .perform(
            put("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\": \"2026-09\", \"targetAmount\": 1000000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.month").value("2026-09"))
        .andExpect(jsonPath("$.data.targetAmount").value(1000000));

    // 重复设置（upsert 更新金额，不报冲突）
    mockMvc
        .perform(
            put("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\": \"2026-09\", \"targetAmount\": 2000000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetAmount").value(2000000));

    // 查询
    mockMvc
        .perform(
            get("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .param("month", "2026-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetAmount").value(2000000));
  }

  @Test
  @DisplayName("未设置目标：查询返回 targetAmount=null 而非 404")
  void getMissingMonthReturnsNull() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            get("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .param("month", "2030-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.month").value("2030-01"))
        .andExpect(jsonPath("$.data.targetAmount").doesNotExist());
  }

  @Test
  @DisplayName("非管理员设置目标返回 403")
  void nonAdminPutForbidden() throws Exception {
    // 注册一个 SALES 用户并登录
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"salesdash\", \"password\": \"Passw0rd!\", \"displayName\": \"仪表盘销售\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("salesdash", "Passw0rd!");

    mockMvc
        .perform(
            put("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\": \"2026-10\", \"targetAmount\": 100000}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("月份格式错误返回 400")
  void badMonthThrows400() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            put("/api/v1/stats/sales-targets")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\": \"2026-13\", \"targetAmount\": 100000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
  }
}
