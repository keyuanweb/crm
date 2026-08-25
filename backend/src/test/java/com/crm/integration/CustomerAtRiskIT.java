package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 性能与数据完整性集成测试（064 T008）：默认 owner + 流失预警。 */
class CustomerAtRiskIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private String createSalesUser(String suffix) throws Exception {
    String adminToken = loginAndGetToken();
    String username = "pit" + suffix;
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"username\": \"%s\", \"password\": \"Passw0rd!\", \"displayName\": \"完整性%s\", \"role\": \"SALES\"}",
                        username, suffix)))
        .andExpect(status().isCreated());
    return loginAndGetToken(username, "Passw0rd!");
  }

  @Test
  @DisplayName("默认 owner：SALES 创建客户/线索 → owner=当前用户")
  void defaultOwnerOnCreate() throws Exception {
    String token = createSalesUser("owner");
    // 当前用户 id（登录响应 user 对象）
    String loginResp =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\": \"pitowner\", \"password\": \"Passw0rd!\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long myId = objectMapper.readTree(loginResp).path("data").path("user").path("id").asLong();

    // 创建客户（未指定 owner）→ owner=当前用户
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"默认负责人客户\", \"company\": \"默认公司\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.ownerId").value(myId));

    // 创建线索（未指定 owner）→ owner=当前用户
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"默认负责人线索\", \"company\": \"默认公司\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ownerId").value(myId));
  }

  @Test
  @DisplayName("流失预警：无跟进旧客户进入预警，有跟进的不进入")
  void atRiskFilters() throws Exception {
    String token = createSalesUser("risk");

    // 创建客户 A（无跟进）
    String respA =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"预警客户A\", \"company\": \"预警公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerA = objectMapper.readTree(respA).path("data").path("id").asLong();
    // 模拟无活动：把 A 的 created_at 改到 40 天前
    jdbcTemplate.update(
        "UPDATE customer SET created_at = DATEADD('DAY', -40, CURRENT_TIMESTAMP) WHERE id = ?",
        customerA);

    // 创建客户 B + 一条跟进（不应进入预警）
    String respB =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"正常客户B\", \"company\": \"正常公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerB = objectMapper.readTree(respB).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/follow-ups")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"method\": \"PHONE\", \"content\": \"跟进一次\"}",
                        customerB)))
        .andExpect(status().isCreated());

    // 预警接口（daysInactive=1）：仅 A 进入（B 有跟进）
    String riskResp =
        mockMvc
            .perform(
                get("/api/v1/customers/health/at-risk")
                    .param("daysInactive", "1")
                    .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode items = objectMapper.readTree(riskResp).path("data").path("items");
    java.util.List<Long> ids = new java.util.ArrayList<>();
    items.forEach(n -> ids.add(n.path("id").asLong()));
    org.assertj.core.api.Assertions.assertThat(ids).contains(customerA);
    org.assertj.core.api.Assertions.assertThat(ids).doesNotContain(customerB);
  }
}
