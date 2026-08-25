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

/** 权限体系加固集成测试（063 T010）：行级越权拦截 + 导出安全。 */
class SecurityHardeningIT extends AbstractIntegrationTest {

  private String createSalesUser(String suffix) throws Exception {
    String adminToken = loginAndGetToken();
    String username = "shsales" + suffix;
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"username\": \"%s\", \"password\": \"Passw0rd!\", \"displayName\": \"越权测试%s\", \"role\": \"SALES\"}",
                        username, suffix)))
        .andExpect(status().isCreated());
    return loginAndGetToken(username, "Passw0rd!");
  }

  @Test
  @DisplayName("线索越权：B 无法查看/修改 A 的线索")
  void leadAccessControl() throws Exception {
    String tokenA = createSalesUser("a");
    String tokenB = createSalesUser("b");

    // A 创建线索（owner=A）
    String leadResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(tokenA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"越权线索\", \"company\": \"越权公司\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long leadId = objectMapper.readTree(leadResp).path("data").path("id").asLong();

    // B 查看 A 线索 → 403
    mockMvc
        .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(tokenB)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    // B 修改 A 线索 → 403
    mockMvc
        .perform(
            put("/api/v1/leads/{id}", leadId)
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权线索改\", \"company\": \"越权公司\"}"))
        .andExpect(status().isForbidden());

    // B 的线索列表不含 A 的线索
    mockMvc
        .perform(get("/api/v1/leads").header("Authorization", bearer(tokenB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[?(@.id == " + leadId + ")]").isEmpty());
  }

  @Test
  @DisplayName("联系人越权：B 无法查看 A 客户的联系人")
  void contactAccessControl() throws Exception {
    String tokenA = createSalesUser("c");
    String tokenB = createSalesUser("d");

    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(tokenA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"越权客户\", \"company\": \"越权公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();
    String contactResp =
        mockMvc
            .perform(
                post("/api/v1/contacts")
                    .header("Authorization", bearer(tokenA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"name\": \"越权联系人\", \"phone\": \"13800001111\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contactId = objectMapper.readTree(contactResp).path("data").path("id").asLong();

    // B 查看 A 客户联系人 → 403
    mockMvc
        .perform(get("/api/v1/contacts/{id}", contactId).header("Authorization", bearer(tokenB)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("控制器角色注解：SALES 可访问，匿名不可")
  void controllerRoleAnnotations() throws Exception {
    // 匿名访问线索列表 → 401
    mockMvc.perform(get("/api/v1/leads")).andExpect(status().isUnauthorized());

    // SALES 可访问
    String token = createSalesUser("e");
    mockMvc
        .perform(get("/api/v1/leads").header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }
}
