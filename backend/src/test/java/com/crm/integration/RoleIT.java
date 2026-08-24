package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 角色权限集成测试（028 T005）：CRUD + me 返回菜单/权限 + 无权限 403。 */
class RoleIT extends AbstractIntegrationTest {

  private String adminToken() throws Exception {
    return loginAndGetToken();
  }

  private long createRole(String token, String code, String name, String dataScope)
      throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/roles")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\""
                            + code
                            + "\",\"name\":\""
                            + name
                            + "\",\"dataScope\":\""
                            + dataScope
                            + "\",\"menus\":[\"customers\",\"orders\"],\"permissions\":[\"customer:create\"]}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("管理员创建角色并配置菜单/权限")
  void adminCreatesRole() throws Exception {
    String token = adminToken();

    long roleId = createRole(token, "VIEWER", "只读查看", "SELF");

    mockMvc
        .perform(get("/api/v1/roles").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[?(@.code == 'VIEWER')].menus[0]").value("customers"));
    org.assertj.core.api.Assertions.assertThat(roleId).isPositive();
  }

  @Test
  @DisplayName("me 返回菜单与操作权限（ADMIN 全量）")
  void meReturnsMenusAndPermissions() throws Exception {
    String token = adminToken();

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.menus").isArray())
        .andExpect(jsonPath("$.data.menus.length()").value(28))
        .andExpect(jsonPath("$.data.permissions").isArray())
        .andExpect(jsonPath("$.data.permissions.length()").value(35));
  }

  @Test
  @DisplayName("内建角色不可删除")
  void builtInNotDeletable() throws Exception {
    String token = adminToken();
    JsonNode roles =
        objectMapper
            .readTree(
                mockMvc
                    .perform(get("/api/v1/roles").header("Authorization", bearer(token)))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data")
            .path("items");
    long adminRoleId = 0;
    for (JsonNode r : roles) {
      if ("ADMIN".equals(r.path("code").asText())) {
        adminRoleId = r.path("id").asLong();
        break;
      }
    }

    mockMvc
        .perform(delete("/api/v1/roles/" + adminRoleId).header("Authorization", bearer(token)))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("无权限用户调删除客户 API 返回 403（SALES 无 customer:delete）")
  void noPermissionReturns403() throws Exception {
    String token = adminToken();
    // 创建 SALES 用户并登录
    String userResp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"permuser\",\"password\":\"pass1234\",\"displayName\":\"权限测试\",\"role\":\"SALES\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long userId = objectMapper.readTree(userResp).path("data").path("id").asLong();
    org.assertj.core.api.Assertions.assertThat(userId).isPositive();

    String salesLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"permuser\",\"password\":\"pass1234\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String salesToken = objectMapper.readTree(salesLogin).path("data").path("accessToken").asText();

    // SALES 无 customer:delete → 删除客户 403
    mockMvc
        .perform(delete("/api/v1/customers/1").header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());
  }
}
