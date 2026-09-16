package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.common.TotpGenerator;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import com.crm.support.FixedClockTestSupport;
import com.crm.support.InMemoryRedisTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Redis 故障时的 fail closed（082 第 9 步，FR-M11）：**宁可登不进来，不可静默降级为单因素**。
 *
 * <p><b>为什么用"按 key 前缀注入故障"的替身，而不是 mock 一个抛异常的 store</b>：若把 store 换成 mock， "fail closed"就会退化成"mock
 * 抛的异常被处理了"——它永远不经过 {@code MfaStateStore} 里那个真正的 catch 边界 （也就是将来可能被写成 {@code catch { return null;
 * }} 的那一行），键格式、TTL、原子原语也一次都不会被执行。 那样测出来的绿，与"缺陷已经不存在"是两件事。
 *
 * <p><b>为什么"2FA 挂掉"与"普通登录照常"必须在同一个用例、同一次故障注入里断言</b>：
 * 拆成两个用例各自注入一次的话，"普通登录不受影响"那一条其实是在<b>一个没有故障的替身</b>上跑的 （另一个用例的故障注入不跨用例），于是隔离性等于没验证过。同一个进程、同一份替身、同一秒，
 * 唯一差别只有键前缀——这才叫"隔离"。
 *
 * <p><b>注入的前缀选 {@code auth:2fa-}</b>：它与 {@code AuthService} 的 {@code auth:fail:} / {@code
 * auth:refresh:} 不重叠，故"2FA 的键挂了"与"普通登录用的键完好"可以同时成立。
 */
class MfaFailClosedIT extends FixedClockTestSupport {

  private final InMemoryRedisTestSupport redis = new InMemoryRedisTestSupport();

  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void installFunctionalRedis() {
    redis.clear();
    redis.install(redisTemplate, clock);
  }

  @Test
  @DisplayName("Redis 挂了：2FA 账号登录 503 且拿不到任何令牌；同一次故障下普通账号照常登录")
  void storeOutageRejectsTwoFactorLoginButNotPlainLogin() throws Exception {
    enroll("mfa_fc_enrolled");
    createUser("mfa_fc_plain");

    redis.failOnKeyPrefix("auth:2fa-");

    // ① 2FA 账号：密码是对的，但票据写不进去 ⇒ 拒绝。这里若"降级"成放行，响应会是一个正常的登录响应。
    JsonNode rejected = loginRaw("mfa_fc_enrolled", 503);
    assertThat(rejected.path("error").path("code").asText()).isEqualTo("MFA_STORE_UNAVAILABLE");
    assertThat(rejected.path("data").has("accessToken")).isFalse();
    assertThat(rejected.path("data").has("mfaToken")).isFalse();

    // ② 同一次注入、同一秒：未启用 2FA 的账号不碰 auth:2fa- 的键，必须照常登录成功。
    //    若有人把 MFA 的调用挂到普通登录路径的公共出口上（例如在 login 开头无条件建票据），这里会红。
    JsonNode plain = loginRaw("mfa_fc_plain", 200);
    assertThat(plain.path("data").path("accessToken").asText()).isNotBlank();
  }

  @Test
  @DisplayName("故障只落在票据键上：验证提交被拒（503），**不**因为「验不了」就放过")
  void verifyFailsClosedWhenTicketStoreIsDown() throws Exception {
    String secret = enroll("mfa_fc_verify");
    String mfaToken = loginData("mfa_fc_verify").path("mfaToken").asText();

    // 只让票据键挂掉：动态码本身仍能算出正确答案，所以这一条考的是"读不到票据时怎么办"。
    redis.failOnKeyPrefix("auth:2fa-ticket:");

    JsonNode rejected = verifyRaw(mfaToken, codeFor(secret), 503);
    assertThat(rejected.path("error").path("code").asText()).isEqualTo("MFA_STORE_UNAVAILABLE");
    assertThat(rejected.has("accessToken")).isFalse();
  }

  @Test
  @DisplayName("故障只落在防重放键上：验证同样被拒，**不**静默跳过「这个码用过没有」")
  void verifyFailsClosedWhenReplayMarkerCannotBeWritten() throws Exception {
    String secret = enroll("mfa_fc_used");
    String mfaToken = loginData("mfa_fc_used").path("mfaToken").asText();

    // 这一条针对的是最容易被写成 fail open 的一处：markTimeStepUsed 的返回值是 boolean，
    // 一个"写不进去就当写成功了"的 catch 会让防重放静默失效 —— 而防重放失效是完全不可观测的
    // （单次验证照常成功，只有重放时才发现），故本断言是它唯一的护栏。
    redis.failOnKeyPrefix("auth:2fa-used:");

    JsonNode rejected = verifyRaw(mfaToken, codeFor(secret), 503);
    assertThat(rejected.path("error").path("code").asText()).isEqualTo("MFA_STORE_UNAVAILABLE");
    assertThat(rejected.has("accessToken")).isFalse();
  }

  @Test
  @DisplayName("故障恢复后同一账号立即可登录：拒绝是暂时的，不是把账号锁死")
  void loginResumesAfterStoreRecovers() throws Exception {
    enroll("mfa_fc_recover");
    redis.failOnKeyPrefix("auth:2fa-");
    assertThat(loginRaw("mfa_fc_recover", 503).path("error").path("code").asText())
        .isEqualTo("MFA_STORE_UNAVAILABLE");

    redis.healAll();

    JsonNode data = loginData("mfa_fc_recover");
    assertThat(data.path("mfaRequired").asBoolean()).isTrue();
    assertThat(data.path("mfaToken").asText()).isNotBlank();
  }

  // ===== 辅助 =====

  /** 登录并断言状态码，返回**根节点**（成功时错误信封不存在，失败时 data 不存在）。 */
  private JsonNode loginRaw(String username, int expectedStatus) throws Exception {
    return objectMapper.readTree(
        body(
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pass1234\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn()));
  }

  private JsonNode loginData(String username) throws Exception {
    return loginRaw(username, 200).path("data");
  }

  private JsonNode verifyRaw(String mfaToken, String code, int expectedStatus) throws Exception {
    return objectMapper.readTree(
        body(
            mockMvc
                .perform(
                    post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn()));
  }

  /**
   * 把某个用户绑定成 2FA 账号（在**注入故障之前**完成，否则夹具自己会 503），返回其 Base32 密钥。
   *
   * <p>返回**密钥**而不是 userId：本类需要在故障注入之后自己算动态码（"票据读不到但码是对的"才有区分度）， 而 userId 在类里没有第二处用处。
   */
  private String enroll(String username) throws Exception {
    createUser(username);
    String jwt = loginAndGetToken(username, "pass1234");
    JsonNode setup =
        objectMapper
            .readTree(
                body(
                    mockMvc
                        .perform(
                            post("/api/v1/auth/2fa/setup").header("Authorization", bearer(jwt)))
                        .andExpect(status().isOk())
                        .andReturn()))
            .path("data");
    String secret = setup.path("secret").asText();
    assertThat(secret).isNotBlank();
    mockMvc
        .perform(
            post("/api/v1/auth/2fa/enable")
                .header("Authorization", bearer(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + codeFor(secret) + "\"}"))
        .andExpect(status().isOk());
    return secret;
  }

  private String codeFor(String base32Secret) {
    long step = TotpGenerator.timeStepOf(clock.instant().getEpochSecond(), 30);
    return TotpGenerator.codeAt(TotpGenerator.decodeSecret(base32Secret), step, 6);
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private long createUser(String username) {
    User user = new User();
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode("pass1234"));
    user.setDisplayName(username);
    user.setRole("SUPPORT");
    user.setEnabled(true);
    user.setTokenVersion(0);
    userMapper.insert(user);
    return user.getId();
  }
}
