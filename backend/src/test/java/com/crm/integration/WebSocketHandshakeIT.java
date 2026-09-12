package com.crm.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 实时通道握手鉴权（FR-G12、T030）。
 *
 * <p><b>为什么不能复用 {@code AbstractIntegrationTest}</b>：握手是 WebSocket 协议层的升级请求 （HTTP Upgrade），{@code
 * MockMvc} 不经过 servlet 容器、无法完成握手，故本类改以真实嵌入式容器 （{@code webEnvironment = RANDOM_PORT}）+ 真实 WebSocket
 * 客户端发起握手。代价是本类会另建一个 应用上下文（不复用 MockMvc 那套），换来的是"握手确实被拒"这一可直接观测的证据。
 *
 * <p><b>改造前必然失败</b>：当前握手只从查询串解析令牌取 {@code userId}，不校验 {@code enabled} 与 {@code tokenVersion}（{@code
 * JwtAuthFilter.validateUserState} 的等效校验未接入），且 {@code setAllowedOrigins("*")} 允许任意来源。故"已停用用户 /
 * 已失效令牌 / 非允许来源"三种握手 如今都能成功。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketHandshakeIT {

  /** 后端测试环境的跨域允许来源（application.yml 默认值，未被 application-test.yml 覆盖）。 */
  private static final String ALLOWED_ORIGIN = "http://localhost:5173";

  private static final String DISALLOWED_ORIGIN = "http://evil.example.com";

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void stubRedis() {
    when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));
  }

  @Test
  @DisplayName("有效令牌握手成功（正对照，避免断言因握手整体不可用而恒真）")
  void validTokenHandshakeAccepted() throws Exception {
    TestUser user = createUserAndLogin();
    assertHandshakeAccepted(user.token(), null, "有效令牌应能完成握手");
  }

  @Test
  @DisplayName("已停用用户的旧令牌握手被拒（FR-G12）")
  void disabledUserHandshakeRejected() throws Exception {
    TestUser user = createUserAndLogin();
    disableUser(user.id());

    assertHandshakeRejected(user.token(), null, "已停用用户的旧令牌不应建立连接");
  }

  @Test
  @DisplayName("令牌版本失效（管理员重置密码）后握手被拒（FR-G12）")
  void staleTokenVersionHandshakeRejected() throws Exception {
    TestUser user = createUserAndLogin();
    // 管理员重置密码 → tokenVersion 递增，旧令牌失效（与 HTTP 路径一致）
    //
    // 端点与状态码都必须钉死：本用例的**场景**完全由这一次调用建立，若它打错地址或未被授权，
    // 令牌版本根本没变，断言就变成"断言一个从未失效的令牌被拒"——必然红，且红得与本用例考察的行为无关。
    // 初版即犯此错（写成不存在的 /reset-password，404 被静默吞掉）。故此处断言状态码，
    // 让地址或权限漂移在 setup 阶段就显形，而不是伪装成"实现没修好"。
    ResponseEntity<String> reset =
        putJson(
            "/api/v1/users/" + user.id() + "/password",
            adminToken(),
            "{\"newPassword\": \"newpass1234\"}");
    if (reset.getStatusCode().value() / 100 != 2) {
      throw new AssertionError(
          "场景未建立：管理员重置密码失败（状态 " + reset.getStatusCode() + "，响应体 " + reset.getBody() + "）");
    }

    assertHandshakeRejected(user.token(), null, "令牌版本失效后不应建立连接");
  }

  /**
   * 允许来源可握手。
   *
   * <p>与下一条"非允许来源被拒"<b>分成两个用例，各自只发起一次握手</b>：同一用例内连续两次握手时，
   * 第二次可能因连接复用等原因失败，届时"被拒"与"客户端本身没连上"无法区分——那样的断言即使转绿也不构成证据。
   */
  @Test
  @DisplayName("跨域允许来源内的握手成功（FR-G12 正对照）")
  void allowedOriginHandshakeAccepted() throws Exception {
    TestUser user = createUserAndLogin();
    assertHandshakeAccepted(user.token(), ALLOWED_ORIGIN, "允许来源内的握手应成功");
  }

  @Test
  @DisplayName("非允许来源握手被拒（FR-G12）")
  void disallowedOriginHandshakeRejected() throws Exception {
    TestUser user = createUserAndLogin();
    assertHandshakeRejected(user.token(), DISALLOWED_ORIGIN, "允许来源之外的手感应被拒绝");
  }

  // ===== 辅助 =====

  private record TestUser(long id, String token) {}

  private String adminToken() throws Exception {
    return login("admin", "admin123");
  }

  private TestUser createUserAndLogin() throws Exception {
    String username = "ws_" + System.nanoTime();
    ResponseEntity<String> created =
        postJson(
            "/api/v1/users",
            adminToken(),
            "{\"username\": \""
                + username
                + "\", \"displayName\": \"握手测试\", \"role\": \"SALES\", \"password\": \"pass1234\"}");
    long id = objectMapper.readTree(created.getBody()).path("data").path("id").asLong();
    return new TestUser(id, login(username, "pass1234"));
  }

  private String login(String username, String password) throws Exception {
    ResponseEntity<String> resp =
        postJson(
            "/api/v1/auth/login",
            null,
            "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}");
    return objectMapper.readTree(resp.getBody()).path("data").path("accessToken").asText();
  }

  /** 停用用户（乐观锁：先读当前 version）。 */
  private void disableUser(long id) throws Exception {
    String token = adminToken();
    ResponseEntity<String> detail =
        restTemplate.exchange(
            url("/api/v1/users/" + id),
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders(token)),
            String.class);
    JsonNode data = objectMapper.readTree(detail.getBody()).path("data");

    putJson(
        "/api/v1/users/" + id,
        token,
        "{\"enabled\": false, \"version\": " + data.path("version").asInt() + "}");
  }

  private ResponseEntity<String> postJson(String path, String bearer, String body) {
    return restTemplate.exchange(
        url(path), HttpMethod.POST, new HttpEntity<>(body, jsonHeaders(bearer)), String.class);
  }

  private ResponseEntity<String> putJson(String path, String bearer, String body) {
    return restTemplate.exchange(
        url(path), HttpMethod.PUT, new HttpEntity<>(body, jsonHeaders(bearer)), String.class);
  }

  private HttpHeaders jsonHeaders(String bearer) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (bearer != null) {
      headers.setBearerAuth(bearer);
    }
    return headers;
  }

  private HttpHeaders bearerHeaders(String bearer) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(bearer);
    return headers;
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  /**
   * 发起一次真实握手；成功返回 {@code null}，失败返回失败原因摘要。
   *
   * <p><b>为什么返回原因而不是布尔值</b>：布尔口径下"服务端拒绝了升级"与"客户端自己没连上"
   * 无法区分，那种断言即使显示通过也不构成"服务端确实拒绝"的证据。把失败原因带进断言消息后， 转绿才真正意味着服务端拒绝了这次握手。
   */
  private String handshakeFailure(String token, String origin) {
    StandardWebSocketClient client = new StandardWebSocketClient();
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    if (origin != null) {
      headers.setOrigin(origin);
    }
    try {
      WebSocketSession session =
          client
              .doHandshake(
                  new TextWebSocketHandler(),
                  headers,
                  URI.create("ws://localhost:" + port + "/ws/notifications?token=" + token))
              .get(10, TimeUnit.SECONDS);
      session.close();
      return null;
    } catch (Exception ex) {
      return ex.toString();
    }
  }

  private void assertHandshakeAccepted(String token, String origin, String message) {
    String failure = handshakeFailure(token, origin);
    if (failure != null) {
      throw new AssertionError(message + "（实际失败：" + failure + "）");
    }
  }

  private void assertHandshakeRejected(String token, String origin, String message) {
    String failure = handshakeFailure(token, origin);
    if (failure == null) {
      throw new AssertionError(message + "（实际握手成功）");
    }
  }
}
