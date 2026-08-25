package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 异常处理加固集成测试（063 T011）：400/404/409 状态码。 */
class ExceptionHandlerIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("坏 JSON → 400（不再 500）")
  void badJsonReturns400() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
  }

  @Test
  @DisplayName("路径参数类型错误 → 400")
  void typeMismatchReturns400() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(get("/api/v1/customers/abc").header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
  }

  @Test
  @DisplayName("唯一键冲突（重复用户名）→ 409")
  void duplicateKeyReturns409() throws Exception {
    String token = loginAndGetToken();
    String username = "dupuser" + (System.nanoTime() % 100000);
    String body =
        String.format(
            "{\"username\": \"%s\", \"password\": \"Passw0rd!\", \"displayName\": \"重复用户\", \"role\": \"SALES\"}",
            username);
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    // 重复创建 → 409（UserService 显式拦截 USER_DUPLICATE）
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("USER_DUPLICATE"));
  }

  @Test
  @DisplayName("资源路径不存在 → 404")
  void noResourceReturns404() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(get("/api/v1/no-such-resource").header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }
}
