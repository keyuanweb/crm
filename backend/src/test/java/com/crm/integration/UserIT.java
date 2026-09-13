package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.repository.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 用户管理集成测试（002-user-management：创建→登录→启停→重置密码→旧令牌失效）。 */
class UserIT extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private long createUser(String token, String username, String role) throws Exception {
    String body =
        String.format(
            "{\"username\": \"%s\", \"displayName\": \"%s\", \"role\": \"%s\", \"password\": \"pass1234\"}",
            username, username, role);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("用户全流程：创建→登录→列表→编辑角色→重置密码→旧令牌失效")
  void userLifecycle() throws Exception {
    String adminToken = loginAndGetToken();
    long userId = createUser(adminToken, "sales01", "SALES");

    // 新用户可登录（FR-001 验收 3）
    String salesToken = loginAndGetToken("sales01", "pass1234");

    // 列表（关键字/角色筛选，FR-001/FR-002）
    mockMvc
        .perform(
            get("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .param("keyword", "sales01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].role").value("SALES"))
        .andExpect(jsonPath("$.data.items[0].lastLoginAt").isNotEmpty());

    // 非管理员不可访问用户管理（FR-008）
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());

    // 编辑角色（FR-004）
    mockMvc
        .perform(
            put("/api/v1/users/{id}", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"销售一\", \"role\": \"SUPPORT\", \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("SUPPORT"));

    // 重置密码（FR-005）：旧密码失效、新密码可登录
    mockMvc
        .perform(
            put("/api/v1/users/{id}/password", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\": \"newPass456\"}"))
        .andExpect(status().isOk());
    // 旧密码登录失败
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"sales01\", \"password\": \"pass1234\"}"))
        .andExpect(status().isUnauthorized());
    // 新密码登录成功
    String refreshedToken = loginAndGetToken("sales01", "newPass456");

    // 重置密码后旧访问令牌失效（SC-004：令牌版本递增）
    mockMvc
        .perform(get("/api/v1/follow-ups").header("Authorization", bearer(salesToken)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/follow-ups").header("Authorization", bearer(refreshedToken)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("停用账号后无法登录且已登录会话失效")
  void disableUserRevokesAccess() throws Exception {
    String adminToken = loginAndGetToken();
    long userId = createUser(adminToken, "sales02", "SALES");
    String salesToken = loginAndGetToken("sales02", "pass1234");

    mockMvc
        .perform(
            put("/api/v1/users/{id}", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false, \"version\": 0}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"sales02\", \"password\": \"pass1234\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/follow-ups").header("Authorization", bearer(salesToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("登录不改变对外版本标识，但确实更新最后登录时间（FR-V01）")
  void loginDoesNotBumpVersion() throws Exception {
    String adminToken = loginAndGetToken();
    long userId = createUser(adminToken, "ver01", "SALES");

    // 该用户登录**两次**（验收场景 3：多次登录不得累积影响其他操作）
    loginAndGetToken("ver01", "pass1234");
    loginAndGetToken("ver01", "pass1234");

    // 仍以创建时的 version 0 提交编辑 → 必须成功。
    // 若登录推进过该用户的 version，此处会得到 409 VERSION_CONFLICT。
    mockMvc
        .perform(
            put("/api/v1/users/{id}", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"登录后编辑\", \"role\": \"SALES\", \"version\": 0}"))
        .andExpect(status().isOk());

    // 且"最后登录时间"**确实更新了** —— 本修复不得以"干脆不更新 lastLoginAt"来实现
    mockMvc
        .perform(
            get("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .param("keyword", "ver01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].lastLoginAt").isNotEmpty());
  }

  @Test
  @DisplayName("真正的并发编辑仍被拒绝（FR-V02）：用已失效的版本标识提交 → 409")
  void staleVersionStillRejected() throws Exception {
    String adminToken = loginAndGetToken();
    long userId = createUser(adminToken, "con01", "SALES");

    // 第一次编辑成功：version 0 → 1
    mockMvc
        .perform(
            put("/api/v1/users/{id}", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"改一次\", \"role\": \"SALES\", \"version\": 0}"))
        .andExpect(status().isOk());

    // 再用**已经用掉**的 version 0 提交 → 必须仍是 409。
    // 本用例是 FR-V01 的反向守卫：它防止"让登录不推进 version"被实现成"把乐观锁一起关掉"。
    mockMvc
        .perform(
            put("/api/v1/users/{id}", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"再改一次\", \"role\": \"SALES\", \"version\": 0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
  }

  @Test
  @DisplayName("重复用户名返回 409 USER_DUPLICATE")
  void duplicateUsernameReturns409() throws Exception {
    String adminToken = loginAndGetToken();
    createUser(adminToken, "sales03", "SALES");
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"sales03\", \"displayName\": \"重复\", \"role\": \"SALES\", \"password\": \"pass1234\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("USER_DUPLICATE"));
  }

  @Test
  @DisplayName("弱密码创建返回 400")
  void weakPasswordReturns400() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"sales04\", \"displayName\": \"弱密码\", \"role\": \"SALES\", \"password\": \"abcdefgh\"}"))
        .andExpect(status().isBadRequest());
  }
}
