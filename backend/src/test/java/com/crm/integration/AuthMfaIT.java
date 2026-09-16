package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.TotpGenerator;
import com.crm.entity.AuditLog;
import com.crm.entity.User;
import com.crm.repository.AuditLogMapper;
import com.crm.repository.UserMapper;
import com.crm.service.MfaService;
import com.crm.service.MfaStateStore;
import com.crm.support.FixedClockTestSupport;
import com.crm.support.InMemoryRedisTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 登录路径上的二次验证，走**真实 HTTP**（082 第 9 步，FR-M07/FR-M08/FR-M11/FR-M12、SC-M04/SC-M05）。
 *
 * <p><b>本类与 {@code MfaLifecycleIT} 的分工</b>：那个类管"改账号安全配置"（setup/enable/disable/管理员重置），
 * 在服务层直调；本类管"能不能进来"，一律经 {@code POST /api/v1/auth/login} 与 {@code POST
 * /api/v1/auth/2fa/verify}，因为这一批性质里有几条<b>只在协议层才成立</b>—— 比如 {@code mfaRequired} 分支、以及 {@code
 * SecurityConfig} 的那条精确放行。
 *
 * <p><b>为什么必须用 {@link FixedClockTestSupport}</b>：本类有三条断言完全由时间决定——同一个动态码不能重放、 15
 * 分钟锁定到期自动恢复、绑定时间与最后登录时间要相等。真时钟下"到期自动恢复"要么等到 15 分钟后（不可行）、 要么靠把锁定窗口配成 1
 * 秒（那改的是被测配置，不是时间）。冻结时钟让这三条都在毫秒内可判。
 *
 * <p><b>为什么必须装 {@link InMemoryRedisTestSupport}</b>：父类的默认值是一个裸 mock， {@code opsForValue().set}
 * 是空操作、{@code get} 恒为 {@code null}——于是"票据写进去了、待会儿还能读出来"
 * 这条前提在默认值下根本不成立，本类会以"票据无效"红遍全场，而那种红<b>指向的是测试夹具而不是被测代码</b>。 替身必须用应用自己那个 {@code Clock} 安装（本类通过
 * {@link FixedClockTestSupport} 拿到它）， 否则 900 秒的锁定窗口在"业务时间过了 901 秒"之后仍被替身认为没到期。
 *
 * <p><b>防重放那条为什么用"新票据 + 同一个码"</b>：用"同一张票据 + 同一个码"去测重放是测不出来的—— 票据在第一次验证时就被消费了，第二次会以 {@code
 * MFA_TICKET_INVALID} 结束， 于是断言"401"在<b>防重放被删掉之后照样绿</b>，而失败原因已经换了一个 （详见 {@code
 * falsification-evidence.md} 里关于"用错的断言会让破坏不转红"的教训）。
 */
class AuthMfaIT extends FixedClockTestSupport {

  /** 应用注入的那个时钟。传给替身，使"业务时间"与"Redis 时间"是同一个。 */
  private final InMemoryRedisTestSupport redis = new InMemoryRedisTestSupport();

  @Autowired private UserMapper userMapper;
  @Autowired private AuditLogMapper auditLogMapper;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MfaService mfaService;
  @Autowired private MfaStateStore stateStore;

  /** 父类的 {@code @BeforeEach} 先跑（先装裸 mock），故这里的替身生效。 */
  @BeforeEach
  void installFunctionalRedis() {
    redis.clear();
    redis.install(redisTemplate, clock);
  }

  // ===== 密码阶段 =====

  @Test
  @DisplayName("已启用 2FA 的账号：登录只回票据，不回令牌，也不写 last_login_at")
  void loginReturnsChallengeInsteadOfTokens() throws Exception {
    Enrolled enrolled = enroll("mfa_login_challenge");
    java.time.LocalDateTime loginAtBefore =
        userMapper.selectById(enrolled.userId()).getLastLoginAt();

    JsonNode data = loginData("mfa_login_challenge");

    assertThat(data.path("mfaRequired").asBoolean()).isTrue();
    assertThat(data.path("mfaToken").asText()).isNotBlank();
    // 契约 §3：expiresIn 只在这一支出现，指的是**票据**的 TTL（默认 300 秒）。
    assertThat(data.path("expiresIn").asInt()).isEqualTo(300);
    // 结构上不该有令牌：这一支若是漏发了 accessToken，前端会直接把它当登录成功用掉。
    assertThat(data.has("accessToken")).isFalse();
    assertThat(data.has("refreshToken")).isFalse();
    assertThat(data.has("user")).isFalse();

    // 登录**尚未完成**，故 last_login_at 不动：写在这里的话，一次失败的二次验证在库里看起来像一次成功登录。
    assertThat(userMapper.selectById(enrolled.userId()).getLastLoginAt()).isEqualTo(loginAtBefore);
  }

  // ===== 验证成功 =====

  @Test
  @DisplayName("验证通过：签发令牌、同时写 last_login_at 与 last_2fa_verified_at（两者必然一致）")
  void verifyIssuesTokensAndStampsBothTimestamps() throws Exception {
    Enrolled enrolled = enroll("mfa_verify_ok");
    int versionBefore = userMapper.selectById(enrolled.userId()).getVersion();
    String mfaToken = loginData("mfa_verify_ok").path("mfaToken").asText();

    // verifyData 返回的是**根节点**（失败时要从根上取 error），成功时令牌在 data 里。
    JsonNode data = verifyData(mfaToken, currentCode(enrolled), null, 200).path("data");

    assertThat(data.path("accessToken").asText()).isNotBlank();
    assertThat(data.path("refreshToken").asText()).isNotBlank();
    assertThat(data.path("user").path("username").asText()).isEqualTo("mfa_verify_ok");

    User after = userMapper.selectById(enrolled.userId());
    java.time.LocalDateTime expected = java.time.LocalDateTime.now(clock);
    assertThat(after.getLastLoginAt()).isEqualTo(expected);
    assertThat(after.getLast2faVerifiedAt()).isEqualTo(expected);
    // 两个时间戳由**同一条语句**写入，故必须逐字相等；分开两次写就会出现"登录时间与二次验证时间差几毫秒"
    // 这种没人能解释的记录。
    assertThat(after.getLastLoginAt()).isEqualTo(after.getLast2faVerifiedAt());
    // 085 的回归护栏：整条流程（setup/enable/登录/验证）都不许自增 version。
    // 同一条性质在 UserIT.mfaVerifyDoesNotBumpVersion 里另有一层 HTTP 断言（管理员能否拿旧版本号编辑成功）。
    assertThat(after.getVersion()).isEqualTo(versionBefore);

    // 换到的令牌真的能用（否则"签发成功"可能只是一个形状正确的字符串）。
    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header("Authorization", bearer(data.path("accessToken").asText())))
        .andExpect(status().isOk());
  }

  // ===== 票据一次性 =====

  @Test
  @DisplayName("一张票据只能换一个会话：第二次提交即使带另一个**有效**的恢复码也必须被拒")
  void ticketIsSingleUse() throws Exception {
    Enrolled enrolled = enroll("mfa_ticket_once");
    String mfaToken = loginData("mfa_ticket_once").path("mfaToken").asText();

    verifyData(mfaToken, null, enrolled.recoveryCodes().get(0), 200);

    // 关键在"另一个有效的码"：若第二次提交仍用恢复码 0，它会因为**已消费**而被拒（401），
    // 于是"票据没被消费"这个缺陷会被一个同样返回 401 的原因掩盖过去。用一个全新的、确定有效的码，
    // 才能把 401 的成因唯一地锁在"票据已被消费"上。
    JsonNode retry = verifyData(mfaToken, null, enrolled.recoveryCodes().get(1), 401);
    assertThat(retry.path("error").path("code").asText()).isEqualTo("MFA_TICKET_INVALID");
    // 且那张票据确实从 Redis 里消失了（不是"读到了但判为无效"）。
    assertThat(redis.snapshot().keySet().stream().anyMatch(k -> k.startsWith("auth:2fa-ticket:")))
        .isFalse();
  }

  // ===== 防重放（FR-M08 / SC-M05）=====

  @Test
  @DisplayName("同一个动态码不能在两张票据上各用一次（FR-M08 防重放）")
  void sameCodeCannotBeReplayedOnANewTicket() throws Exception {
    Enrolled enrolled = enroll("mfa_replay");
    // 时钟冻结 ⇒ 两次登录落在同一个时间步，提交的是**逐字相同**的码。
    String firstTicket = loginData("mfa_replay").path("mfaToken").asText();
    verifyData(firstTicket, currentCode(enrolled), null, 200);

    String secondTicket = loginData("mfa_replay").path("mfaToken").asText();
    assertThat(secondTicket).isNotEqualTo(firstTicket);

    // 若删掉 markTimeStepUsed（或让它恒返回 true），这里会 200 —— 同一个码换出第二个会话。
    JsonNode replayed = verifyData(secondTicket, currentCode(enrolled), null, 401);
    assertThat(replayed.path("error").path("code").asText()).isEqualTo("MFA_CODE_INVALID");
    // 第二张票据**没有被消费**：这次提交根本没通过验证，票据该留给下一次正确的尝试。
    // （若实现顺序写反——先消费票据再验码——用户会在输错一次之后被迫重新登录。）
    assertThat(
            redis.snapshot().keySet().stream()
                .anyMatch(k -> k.equals("auth:2fa-ticket:" + secondTicket)))
        .isTrue();
  }

  // ===== 锁定（SC-M04）=====

  @Test
  @DisplayName("5 次错码后锁定：第 5 次即 429，此后**即使码正确**也 429，901 秒后自动恢复")
  void lockoutAfterFiveFailuresAndAutoRecovery() throws Exception {
    Enrolled enrolled = enroll("mfa_lockout");
    String wrong = wrongCodeFor(enrolled.secret());

    // 前 4 次：401，且每次都回一个**新**票据（票据不因失败而作废，用户该有第 5 次机会）。
    for (int attempt = 1; attempt <= 4; attempt++) {
      String ticket = loginData("mfa_lockout").path("mfaToken").asText();
      JsonNode failed = verifyData(ticket, wrong, null, 401);
      assertThat(failed.path("error").path("code").asText())
          .as("第 %s 次失败应当是码错误而不是锁定", attempt)
          .isEqualTo("MFA_CODE_INVALID");
    }

    // 第 5 次：达阈值，直接 429（不是 401 —— 契约要求"连续 5 次失败锁定"）。
    String fifthTicket = loginData("mfa_lockout").path("mfaToken").asText();
    JsonNode locked = verifyData(fifthTicket, wrong, null, 429);
    assertThat(locked.path("error").path("code").asText()).isEqualTo("MFA_LOCKED");
    // 429 的 message 里要有剩余锁定秒数（契约 §3 明确要求）。
    assertThat(locked.path("error").path("message").asText()).contains("900");

    // 锁定生效期间：**正确的码**也必须被拒。这一条是"锁定检查在验码之前"的护栏——
    // 若顺序反过来（先验码、通过就放行），爆破者一旦蒙对就直接进来了，锁定形同虚设。
    String lockedTicket = loginData("mfa_lockout").path("mfaToken").asText();
    JsonNode stillLocked = verifyData(lockedTicket, currentCode(enrolled), null, 429);
    assertThat(stillLocked.path("error").path("code").asText()).isEqualTo("MFA_LOCKED");
    // 锁定期间的尝试不记数：记了也只会让窗口更容易被延长，而它对"要不要放行"没有任何影响。
    assertThat(stateStore.failureCount(enrolled.userId())).isEqualTo(5);
    // 被拒的那次没有消费票据，也没有写第二条审计（审计条数仍是 5 条失败）。
    assertThat(auditCount(enrolled.userId(), "MFA_VERIFY_FAILED")).isEqualTo(5);

    // 901 秒后（锁定窗口 900 秒）自动恢复：不需要任何人干预。
    advanceSeconds(901);
    String freshTicket = loginData("mfa_lockout").path("mfaToken").asText();
    verifyData(freshTicket, currentCode(enrolled), null, 200);
    assertThat(stateStore.failureCount(enrolled.userId())).isZero();
  }

  // ===== 失败审计（FR-M12）=====

  @Test
  @DisplayName("失败的二次验证写一条以 system 为主体的审计，且**不含**提交的码")
  void failedVerificationIsAuditedAsSystem() throws Exception {
    Enrolled enrolled = enroll("mfa_failed_audit");
    String wrong = wrongCodeFor(enrolled.secret());
    String ticket = loginData("mfa_failed_audit").path("mfaToken").asText();

    verifyData(ticket, wrong, null, 401);

    List<AuditLog> rows = auditRows(enrolled.userId(), "MFA_VERIFY_FAILED");
    assertThat(rows).hasSize(1);
    AuditLog row = rows.get(0);
    // 用 recordAsSystem 而不是 record：这条路径上没有登录主体（SecurityContext 是空的），
    // 用 record 会写出一行 actorId 为 null 的"无主体审计行"——既归因不到人，也无法与
    // "用户被删除后 actor_id 悬空"区分。
    assertThat(row.getActorId()).isZero();
    assertThat(row.getActorName()).isEqualTo("system");
    assertThat(row.getEntityType()).isEqualTo("USER");
    assertThat(row.getDetail()).contains("[system]").contains("第 1/5 次");
    // 提交的码是凭证（恢复码更是长期有效的凭据），任何一条把它写进审计的实现都等于
    // 把"能换一个会话的字符串"递给了能读审计表的人。
    assertThat(row.getDetail()).doesNotContain(wrong);
  }

  // ===== 恢复码（逃生通道）=====

  @Test
  @DisplayName("恢复码：用一次即失效，而**下一个**码仍然有效（一次失败不该关上逃生通道）")
  void recoveryCodeIsSingleUseAndTheNextStillWorks() throws Exception {
    Enrolled enrolled = enroll("mfa_recovery_login");
    List<String> codes = enrolled.recoveryCodes();

    verifyData(loginData("mfa_recovery_login").path("mfaToken").asText(), null, codes.get(0), 200);

    JsonNode reused =
        verifyData(
            loginData("mfa_recovery_login").path("mfaToken").asText(), null, codes.get(0), 401);
    assertThat(reused.path("error").path("code").asText()).isEqualTo("RECOVERY_CODE_INVALID");

    verifyData(loginData("mfa_recovery_login").path("mfaToken").asText(), null, codes.get(1), 200);
  }

  // ===== 票据所凭的前提变了（"对着当下的行判"）=====

  @Test
  @DisplayName("拿到票据之后被管理员重置 2FA：验证必须被拒，而不是「顺手放行」")
  void adminResetBetweenChallengeAndVerifyIsRejected() throws Exception {
    Enrolled enrolled = enroll("mfa_reset_midway");
    String ticket = loginData("mfa_reset_midway").path("mfaToken").asText();

    mfaService.resetByAdmin(enrolled.userId());

    // 若实现用的是"① 里读到的那个 user 对象"（而不是消费票据后重读），这里会 200 ——
    // 一次本应需要第二因素的验证被静默跳过。让他重新登录：那时账号确实已是单因素，走正常路径。
    JsonNode rejected = verifyData(ticket, currentCode(enrolled), null, 401);
    assertThat(rejected.path("error").path("code").asText()).isEqualTo("MFA_TICKET_INVALID");
  }

  @Test
  @DisplayName("重置发生在「① 读用户」与「⑤ 重读」之间：⑤ 必须当场拒（这条才钉住 ⑤ 的重读本身）")
  void adminResetInsideTheVerificationWindowIsCaughtByTheReRead() throws Exception {
    Enrolled enrolled = enroll("mfa_reset_in_window");
    String ticket = loginData("mfa_reset_in_window").path("mfaToken").asText();

    // 与上一条的区别是**变更发生的时刻**，而这一点决定了它钉住的是哪一处判据：
    //   上一条在**请求之前**就重置了 —— 那么 ① 读到的行已经是"未启用"，请求在 ① 的早退判据上就结束了，
    //   ⑤ 的重读根本没被考到（实测：把 ⑤ 的 `userMapper.selectById` 换成 ① 的对象，上一条照样绿）。
    //   本条的变更插在 ④（消费票据）那一刻：① 已经读过、③ 已经验过码，还能拦住它的**只有** ⑤ 的重读。
    //   而 MockMvc 是同步的，用例没法从外面插进这个窗口 —— 故借用替身的一次性钩子，在 getAndDelete 上触发。
    redis.onGetAndDeleteKeyPrefix(
        "auth:2fa-ticket:", () -> mfaService.resetByAdmin(enrolled.userId()));

    JsonNode rejected = verifyData(ticket, currentCode(enrolled), null, 401);
    assertThat(rejected.path("error").path("code").asText()).isEqualTo("MFA_TICKET_INVALID");
    // 反方向的守卫：不能因为"改判成拒了"就连正常路径一起拒掉（否则本用例会因为一个恒真的实现而变绿）。
    assertThat(redis.containsKey("auth:2fa-ticket:" + ticket)).isFalse();
  }

  @Test
  @DisplayName("拿到票据之后账号被停用：403，且不签发任何令牌")
  void disabledAccountCannotCompleteVerification() throws Exception {
    Enrolled enrolled = enroll("mfa_disabled_midway");
    String ticket = loginData("mfa_disabled_midway").path("mfaToken").asText();

    User update = new User();
    update.setId(enrolled.userId());
    update.setEnabled(false);
    userMapper.updateById(update);

    JsonNode rejected = verifyData(ticket, currentCode(enrolled), null, 403);
    assertThat(rejected.path("error").path("code").asText()).isEqualTo("FORBIDDEN");
    assertThat(rejected.has("accessToken")).isFalse();
  }

  // ===== SecurityConfig：精确放行（而不是通配）=====

  @Test
  @DisplayName("verify 免 JWT 能到达控制器（得到 MFA_TICKET_INVALID，而不是空体 401）")
  void verifyIsReachableWithoutJwt() throws Exception {
    // 到达控制器 = 响应体是错误信封（过滤器链拦下的话是 HttpStatusEntryPoint 的**空体** 401）。
    JsonNode body =
        raw(
            post("/api/v1/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"not-a-ticket\",\"code\":\"000000\"}"),
            401);
    assertThat(body.path("error").path("code").asText()).isEqualTo("MFA_TICKET_INVALID");
  }

  @Test
  @DisplayName("其余 2FA 端点仍要求 JWT：未带令牌是**空体** 401（通配化会让它变成 JSON）")
  void otherMfaEndpointsStillRequireJwt() throws Exception {
    // 这一条是为了钉住"那条例外必须写精确路径"。若有人把 SecurityConfig 里的
    // /api/v1/auth/2fa/verify 改成 /api/v1/auth/2fa/**，这些端点会被放行、进入控制器，
    // 而控制器取 SecurityUtil.currentUserId() 得到 null —— 响应就从"空体 401"变成带
    // {"code":"UNAUTHORIZED"} 的 JSON（或参数解析的 400）。两种失败方向完全不同：
    // 一个是"少了认证"，一个只是"少了授权参数"。
    assertBareUnauthorized(get("/api/v1/auth/2fa/status"));
    assertBareUnauthorized(post("/api/v1/auth/2fa/setup"));
    assertBareUnauthorized(
        post("/api/v1/auth/2fa/disable").contentType(MediaType.APPLICATION_JSON).content("{}"));
  }

  // ===== 辅助 =====

  /** 一个已启用 2FA 的用户及其绑定材料。 */
  private record Enrolled(long userId, String secret, List<String> recoveryCodes) {}

  /**
   * 建一个启用 2FA 的用户：**走真实的 setup/enable 两个端点**，不直接改库。
   *
   * <p>理由：本类的主题是登录路径，而绑定的正确性由 {@code MfaLifecycleIT} 负责。这里若直接写库， 一旦 setup/enable
   * 的写列行为退化（例如密钥列名写错），本类会一起红，而红的原因指向登录； 走端点则让"夹具构造失败"与"被测路径失败"在失败现场上可分。
   */
  private Enrolled enroll(String username) throws Exception {
    long userId = createUser(username);
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

    JsonNode enable =
        objectMapper
            .readTree(
                body(
                    mockMvc
                        .perform(
                            post("/api/v1/auth/2fa/enable")
                                .header("Authorization", bearer(jwt))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"" + codeFor(secret) + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()))
            .path("data");
    List<String> codes = new ArrayList<>();
    enable.path("recoveryCodes").forEach(node -> codes.add(node.asText()));
    assertThat(codes).hasSize(10);
    return new Enrolled(userId, secret, List.copyOf(codes));
  }

  /** 登录并取回 {@code data} 子树；2FA 账号会拿到票据分支，非 2FA 账号会拿到令牌分支。 */
  private JsonNode loginData(String username) throws Exception {
    return objectMapper
        .readTree(
            body(
                mockMvc
                    .perform(
                        post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"username\":\"" + username + "\",\"password\":\"pass1234\"}"))
                    .andExpect(status().isOk())
                    .andReturn()))
        .path("data");
  }

  /** 提交一次二次验证，断言状态码并返回响应 JSON（失败时是错误信封，故从根节点取 {@code error}）。 */
  private JsonNode verifyData(String mfaToken, String code, String recoveryCode, int expectedStatus)
      throws Exception {
    StringBuilder json = new StringBuilder("{\"mfaToken\":\"").append(mfaToken).append("\"");
    if (code != null) {
      json.append(",\"code\":\"").append(code).append("\"");
    }
    if (recoveryCode != null) {
      json.append(",\"recoveryCode\":\"").append(recoveryCode).append("\"");
    }
    json.append("}");
    return objectMapper.readTree(
        body(
            mockMvc
                .perform(
                    post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().is(expectedStatus))
                .andReturn()));
  }

  private JsonNode raw(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      int expectedStatus)
      throws Exception {
    return objectMapper.readTree(
        body(mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn()));
  }

  /**
   * 断言"未认证被**过滤器链**拦下"，判据是响应体为空。
   *
   * <p>{@code HttpStatusEntryPoint} 只写状态码、不写响应体；而一旦请求进到控制器， {@code GlobalExceptionHandler} 会写一个带
   * {@code code} 的错误信封。故"空体"是"没被放行"的判据， 也是本仓库里唯一能把这两种 401 分开的可观测差异。
   */
  private void assertBareUnauthorized(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    String body = body(mockMvc.perform(request).andExpect(status().isUnauthorized()).andReturn());
    assertThat(body).as("未带令牌的请求应当被过滤器链拦下（空体 401），实际响应体：%s", body).isEmpty();
  }

  private static String body(MvcResult result) throws Exception {
    // 显式按 UTF-8 解码：MockHttpServletResponse 的 characterEncoding 默认是 ISO-8859-1。
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  /** 用被冻结的时钟算当前时间步的动态码（与 {@code TotpService} 校验时同一个时钟、同一步长）。 */
  private String codeFor(String base32Secret) {
    long step = TotpGenerator.timeStepOf(clock.instant().getEpochSecond(), 30);
    return TotpGenerator.codeAt(TotpGenerator.decodeSecret(base32Secret), step, 6);
  }

  /**
   * 某个已绑定账号**当前该提交的那个码**。
   *
   * <p>存在的理由是一次真实踩过的错：本类原先有 7 处把 {@code Enrolled.secret}（Base32 密钥）直接当成 {@code code} 传进 {@code
   * verifyData} —— 于是它们**全都不是**在测自己声称的那件事。密钥串当然不会命中任何时间步， 于是"验证成功"那几条会以 401 结束，而"必须被拒"那几条的 401
   * 则来自一个<b>与被测性质无关</b>的原因 （码不对，而不是票据无效/账号停用/已被重置）。多一个中间方法，是为了让每一处调用点上都写着"码"这个字。
   */
  private String currentCode(Enrolled enrolled) {
    return codeFor(enrolled.secret());
  }

  /**
   * 一个**确定不等于**正确码的 6 位码（末位翻转）。
   *
   * <p>不写死 {@code "000000"}：那个值有百万分之一的概率恰好等于当前时间步的正确码， 而那会让"错码 5 次 ⇒
   * 锁定"这条用例以极低概率变绿/变红且不可复现。翻转末位是确定性的。
   */
  private String wrongCodeFor(String base32Secret) {
    String correct = codeFor(base32Secret);
    char last = correct.charAt(correct.length() - 1);
    char flipped = last == '9' ? '8' : (char) (last + 1);
    return correct.substring(0, correct.length() - 1) + flipped;
  }

  private long auditCount(long entityId, String action) {
    return auditLogMapper.selectCount(
        new LambdaQueryWrapper<AuditLog>()
            .eq(AuditLog::getEntityId, entityId)
            .eq(AuditLog::getAction, action));
  }

  private List<AuditLog> auditRows(long entityId, String action) {
    return auditLogMapper.selectList(
        new LambdaQueryWrapper<AuditLog>()
            .eq(AuditLog::getEntityId, entityId)
            .eq(AuditLog::getAction, action));
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
