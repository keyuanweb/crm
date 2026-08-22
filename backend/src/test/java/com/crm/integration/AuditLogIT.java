package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 审计日志查询集成测试（T070，FR-017：仅管理员可查）。 */
class AuditLogIT extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"name\": \"%s\", \"company\": \"审计公司\"}", name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("管理员可查询审计日志且包含刚写入的记录")
  void adminCanQueryAuditLogs() throws Exception {
    String token = loginAndGetToken();
    long id = createCustomer(token, "审计客户A");

    mockMvc
        .perform(get("/api/v1/audit-logs").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").isNumber())
        .andExpect(jsonPath("$.data.items[0].action").value("CREATE"))
        .andExpect(jsonPath("$.data.items[0].entityType").value("CUSTOMER"))
        .andExpect(jsonPath("$.data.items[0].entityId").value(id))
        .andExpect(jsonPath("$.data.items[0].actorName").value("admin"));
  }

  @Test
  @DisplayName("按动作/对象类型筛选正确")
  void auditLogFilters() throws Exception {
    String token = loginAndGetToken();
    createCustomer(token, "审计客户B");
    createCustomer(token, "审计客户C");

    mockMvc
        .perform(
            get("/api/v1/audit-logs")
                .header("Authorization", bearer(token))
                .param("action", "CREATE")
                .param("entityType", "CUSTOMER"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.items[*].action")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("CREATE"))))
        .andExpect(jsonPath("$.data.total").isNumber());

    mockMvc
        .perform(
            get("/api/v1/audit-logs")
                .header("Authorization", bearer(token))
                .param("action", "DELETE"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("非管理员访问审计日志返回 403")
  void nonAdminForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    User support = new User();
    support.setUsername("support_audit");
    support.setPasswordHash(passwordEncoder.encode("pass1234"));
    support.setDisplayName("客服审计");
    support.setRole("SUPPORT");
    support.setEnabled(true);
    userMapper.insert(support);

    String supportToken = loginAndGetToken("support_audit", "pass1234");
    mockMvc
        .perform(get("/api/v1/audit-logs").header("Authorization", bearer(supportToken)))
        .andExpect(status().isForbidden());
  }
}
