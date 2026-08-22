package com.crm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 集成测试基类：H2 + MockMvc + 模拟 Redis（research.md R9）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  @Autowired protected MockMvc mockMvc;

  @Autowired protected ObjectMapper objectMapper;

  @MockBean protected RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void stubRedis() {
    when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));
  }

  /** 使用默认种子账号登录并返回 access token。 */
  protected String loginAndGetToken() throws Exception {
    return loginAndGetToken("admin", "admin123");
  }

  protected String loginAndGetToken(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginBody(username, password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn();
    String json = result.getResponse().getContentAsString();
    return objectMapper.readValue(json, ApiEnvelopeAuth.class).data().getAccessToken();
  }

  protected String bearer(String token) {
    return "Bearer " + token;
  }

  private record LoginBody(String username, String password) {}

  private record ApiEnvelopeAuth(boolean success, AuthResponse data, Object error) {}
}
