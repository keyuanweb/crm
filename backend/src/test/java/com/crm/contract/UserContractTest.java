package com.crm.contract;

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

/** 用户管理契约测试（002：请求/响应结构与 contracts/users.md 一致）。 */
class UserContractTest extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("未认证访问用户管理返回 401")
  void unauthenticatedReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("管理员列表返回分页信封与契约字段")
  void adminListReturnsEnvelope() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.total").isNumber())
        .andExpect(jsonPath("$.data.items[0].username").value("admin"))
        .andExpect(jsonPath("$.data.items[0].role").value("ADMIN"))
        .andExpect(jsonPath("$.data.items[0].enabled").isBoolean());
  }

  @Test
  @DisplayName("创建用户返回契约字段（id/username/displayName/role/enabled/version）")
  void createReturnsContractFields() throws Exception {
    String token = loginAndGetToken();
    String body =
        """
        {"username": "sales_contract", "displayName": "契约用户", "role": "SALES", "password": "pass1234"}
        """;
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").isNumber())
        .andExpect(jsonPath("$.data.username").value("sales_contract"))
        .andExpect(jsonPath("$.data.role").value("SALES"))
        .andExpect(jsonPath("$.data.enabled").value(true))
        .andExpect(jsonPath("$.data.version").isNumber());
  }

  @Test
  @DisplayName("不存在的角色返回 400（角色须存在于角色表）")
  void invalidRoleReturns400() throws Exception {
    String token = loginAndGetToken();
    String body =
        """
        {"username": "bad_role", "displayName": "非法", "role": "BOSS", "password": "pass1234"}
        """;
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.error.message").value("角色不存在或已停用：BOSS"));
  }

  @Test
  @DisplayName("弱密码（纯字母）返回 400 BAD_REQUEST")
  void weakPasswordReturns400() throws Exception {
    String token = loginAndGetToken();
    String body =
        """
        {"username": "weak_pwd", "displayName": "弱密码", "role": "SALES", "password": "abcdefgh"}
        """;
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
  }

  @Test
  @DisplayName("非管理员访问用户管理返回 403（契约权限矩阵）")
  void nonAdminForbidden() throws Exception {
    User support = new User();
    support.setUsername("support_contract");
    support.setPasswordHash(passwordEncoder.encode("pass1234"));
    support.setDisplayName("客服契约");
    support.setRole("SUPPORT");
    support.setEnabled(true);
    userMapper.insert(support);

    String token = loginAndGetToken("support_contract", "pass1234");
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
  }
}
