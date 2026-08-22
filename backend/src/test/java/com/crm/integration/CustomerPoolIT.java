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

/** 客户公海集成测试（011 T009/T015/T020）：公海/领取/扫描/批量转移/权限。 */
class CustomerPoolIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private long createCustomer(String token, String name, String company) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"" + company + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("公海流程：创建→公海可见→领取→我的客户→重复领取 409")
  void poolClaimFlow() throws Exception {
    String adminToken = loginAndGetToken();
    long customerId = createCustomer(adminToken, "公海客户A", "公海公司A");

    // 公海可见（无归属）
    mockMvc
        .perform(get("/api/v1/customers/pool").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 领取
    mockMvc
        .perform(
            post("/api/v1/customers/pool/{id}/claim", customerId)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ownerId").isNumber());

    // 我的客户可见
    mockMvc
        .perform(get("/api/v1/customers/my").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 公海不再可见该客户（按唯一名搜索不到）
    mockMvc
        .perform(
            get("/api/v1/customers/pool")
                .header("Authorization", bearer(adminToken))
                .param("keyword", "公海客户A"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));

    // 重复领取 409
    mockMvc
        .perform(
            post("/api/v1/customers/pool/{id}/claim", customerId)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_ALREADY_OWNED"));
  }

  @Test
  @DisplayName("公海扫描：超期客户退回公海")
  void poolScan() throws Exception {
    String token = loginAndGetToken();
    // 创建客户后直接用 SQL 置为超期（H2：通过 update 接口无法改 created_at，用 mapper 直查）
    long customerId = createCustomer(token, "扫描客户A", "扫描公司A");
    // 领取使其有归属
    mockMvc
        .perform(
            post("/api/v1/customers/pool/{id}/claim", customerId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 用 JdbcTemplate 把 created_at 改到 40 天前（模拟超期）
    jdbcTemplate.update(
        "UPDATE customer SET created_at = DATEADD('DAY', -40, CURRENT_TIMESTAMP) WHERE id = ?",
        customerId);

    // 扫描（管理员）
    mockMvc
        .perform(post("/api/v1/customers/pool/scan").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.returnedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 退回后公海可见
    mockMvc
        .perform(
            get("/api/v1/customers/pool")
                .header("Authorization", bearer(token))
                .param("keyword", "扫描客户A"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  @DisplayName("批量转移：归属更新，目标不存在 404")
  void batchTransfer() throws Exception {
    String token = loginAndGetToken();
    long c1 = createCustomer(token, "转移客户A", "转移公司A");
    long c2 = createCustomer(token, "转移客户B", "转移公司B");

    // 建目标用户
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"pooltarget\", \"password\": \"Passw0rd!\", \"displayName\": \"公海目标\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());

    // 批量转移
    mockMvc
        .perform(
            post("/api/v1/customers/batch-transfer")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerIds\": [%d, %d], \"targetOwnerId\": %d}",
                        c1, c2, targetUserId(token))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(2));

    // 目标不存在 404
    mockMvc
        .perform(
            post("/api/v1/customers/batch-transfer")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerIds\": [1], \"targetOwnerId\": 999999}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
  }

  @Test
  @DisplayName("非管理员扫描/批量转移返回 403")
  void nonAdminForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"poolsales\", \"password\": \"Passw0rd!\", \"displayName\": \"公海销售\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("poolsales", "Passw0rd!");

    mockMvc
        .perform(post("/api/v1/customers/pool/scan").header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/customers/batch-transfer")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerIds\": [1], \"targetOwnerId\": 1}"))
        .andExpect(status().isForbidden());
  }

  private long targetUserId(String adminToken) throws Exception {
    String users =
        mockMvc
            .perform(get("/api/v1/users").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    JsonNode items = objectMapper.readTree(users).path("data").path("items");
    for (JsonNode u : items) {
      if ("pooltarget".equals(u.path("username").asText())) {
        return u.path("id").asLong();
      }
    }
    return -1;
  }
}
