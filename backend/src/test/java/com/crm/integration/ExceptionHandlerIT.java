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

  @Test
  @DisplayName("其他端点的参数校验失败仍返回 400（FR-V15 —— 关闭端点的 422 映射不得外溢）")
  void otherEndpointValidationStillReturns400() throws Exception {
    String token = loginAndGetToken();
    // displayName 超过 50 字 → 违反 UserCreateRequest 的 @Size(max = 50) →
    // MethodArgumentNotValidException。
    // 这是**另一个**端点的边界校验失败，用于钉住 085 新增的 422 映射（只作用于 CloseRequest.closeResult）
    // 没有变成"全局把 400 改成 422"。
    String longName = "长".repeat(60);
    String body =
        String.format(
            "{\"username\": \"badname1\", \"displayName\": \"%s\", \"role\": \"SALES\", \"password\": \"pass1234\"}",
            longName);

    String resp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isBadRequest())
            // 断言它走的确实是 handleValidation 那条路径（服务层抛的 BusinessException 文案不同），
            // 否则本用例可能"碰巧"因为别的原因返回 400 而失去区分度。
            .andExpect(jsonPath("$.error.message").value("参数校验失败"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("displayName"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // 不得出现关闭端点专属的错误码 —— 那将意味着映射外溢
    org.assertj.core.api.Assertions.assertThat(resp).doesNotContain("CLOSE_RESULT_REQUIRED");
    org.assertj.core.api.Assertions.assertThat(resp).contains("BAD_REQUEST");
  }
}
