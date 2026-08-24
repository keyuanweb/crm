package com.crm.integration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.auth.CaptchaResponse;
import com.crm.service.CaptchaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** 登录验证码集成测试（017 T002）：captcha 端点 + login 验证码校验。 */
@TestPropertySource(properties = "crm.captcha.enabled=true")
class AuthCaptchaIT extends AbstractIntegrationTest {

  @MockBean private CaptchaService captchaService;

  @Test
  @DisplayName("GET /auth/captcha 返回 captchaId 与图片 base64")
  void captchaEndpoint() throws Exception {
    when(captchaService.generate())
        .thenReturn(new CaptchaResponse("test-captcha-id", "data:image/png;base64,AAAA"));

    mockMvc
        .perform(get("/api/v1/auth/captcha"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.captchaId").value("test-captcha-id"))
        .andExpect(jsonPath("$.data.imageBase64").value("data:image/png;base64,AAAA"));
  }

  @Test
  @DisplayName("验证码错误时登录返回 422 CAPTCHA_INVALID")
  void loginWithWrongCaptcha() throws Exception {
    doThrow(new BusinessException(ErrorCode.CAPTCHA_INVALID))
        .when(captchaService)
        .validate(anyString(), anyString());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"admin\",\"password\":\"admin123\",\"captchaId\":\"id1\",\"captchaCode\":\"wrong\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CAPTCHA_INVALID"));
  }

  @Test
  @DisplayName("验证码通过且凭证正确时登录成功")
  void loginWithValidCaptcha() throws Exception {
    // validate 不抛异常即视为通过
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"admin\",\"password\":\"admin123\",\"captchaId\":\"id1\",\"captchaCode\":\"7gk2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
  }
}
