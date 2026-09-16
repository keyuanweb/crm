package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.support.FixedClockTestSupport;
import com.crm.support.InMemoryRedisTestSupport;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 共享限流件走**真实 HTTP**（100-rate-limit-consolidation，T1–T4 / T12 / T14 / T15）。
 *
 * <p>被测对象是 C3 收敛后的公开邮件追踪端点：配额 <b>60 次 / 60 秒、按 IP</b>（改造前的数字，逐字未变）。 「端点 + DispatcherServlet + 切面 +
 * 异常处理器 + 响应头」这一整条链是本类唯一能覆盖的东西—— 组件单测（{@code RateLimitStoreTest}/{@code
 * RateLimiterShapeTest}）验的是调用形状， 与「接上线了没有」是两个问题。
 *
 * <p>⚠️ <b>T15（C4 追加）测的是<b>另一种形态</b>：公开表单提交</b>。它不是注解形态，而是 {@code FormService#submit} 在服务内部委托同一个
 * {@code RateLimiter}（见该类 {@code checkRateLimit} 的 javadoc：被限流单元是服务方法、配额写在本类常量上、必须保留 {@code request
 * == null → "unknown"} 这个退化字面量，三条理由）。本类同时覆盖两种形态是<b>有意的</b>：形态不同 ⇒ 失效方式不同（注解那条断在切面/顺序上，
 * 委托这条断在服务有没有真的调过去），"哪一条被破坏会红"必须都能点名。
 *
 * <p><b>⚠️ 本类必须装 {@link InMemoryRedisTestSupport}，这不是可选项</b>：父类默认的 {@code RedisTemplate} 是裸
 * mock，{@code opsForValue().increment()} 恒返回 {@code null} ⇒ 走 fail-open ⇒ <b>限流是 no-op</b>。那种状态下「连打
 * 61 次都得 200」看起来完全正常，用例会以假绿收场。 装了替身后 {@code increment} 才真的计数。
 *
 * <p><b>但装了替身也还不够，每个用例必须做满三件事</b>（缺一即视为假绿）：
 *
 * <ol>
 *   <li><b>正对照</b>：断言计数真的落到 Redis（{@link #theCounterReallyLandsInRedis}）—— 没接线时该键不存在 ⇒
 *       红。<b>这一条才是核心</b>，其余用例都建立在它成立之上。
 *   <li><b>负对照</b>：未达阈值时断言 200（T1/T3 里都带这句）。只断「第 61 次是 429」是不够的—— 「限流恒拒」这个反向劣解也能满足它，而它会让端点整体不可用。
 *   <li><b>独立的 {@code X-Forwarded-For}</b>：MockMvc 的默认 {@code remoteAddr} 是 {@code
 *       127.0.0.1}，它是<b>上下文级</b>的常量，而替身是类级安装、用例级清空。依赖默认值会让 计数桶在用例之间意外连通（本仓既有先例：{@code FormService}
 *       的进程内桶因为这条恰好凑满 3/3， 制造过一批与真因无关的假红）。故每个用例自带一个专用 IP。
 * </ol>
 *
 * <p><b>为什么冻结时钟</b>：T3 问的是「窗口走完能不能恢复」。真时钟下要么等 60 秒，要么把窗口配小—— 后者改的是被测配置而不是时间。{@link
 * FixedClockTestSupport#advanceSeconds} 让「过了 61 秒」在毫秒内可判， 且必须与 {@link InMemoryRedisTestSupport}
 * 共用同一个 {@code Clock}，否则「业务认为过了 61 秒」 与「替身认为 TTL 还没到」会各说各话。
 *
 * <p>⚠️ <b>T13（未授权者得 403 且不消耗配额）不在本类</b>：它需要的端点必须<b>同时</b>带 {@code @RequirePermission} 与
 * {@code @RateLimit}，而 C3/C4 里带限流的<b>全都是公开端点</b>（邮件追踪走注解、表单提交走服务侧委托，两者本来都不需要权限） ⇒ 此时写「403
 * 且无配额键」是**空断言**（无论如何都成立）。该用例随 P0 标注一起落在 C5。 ⚠️ <b>订正</b>：本句原文写「C3 里带限流的只有两个公开端点」（留痕：原文见 git 历史），C4
 * 之后这个数字变成「三个限流入口、 但仍全是公开端点」—— 判据（403 且无配额键）一个字不改，落点也不变，变的只是"为什么现在还写不了它"的事实描述。
 *
 * <p>⚠️ <b>2026-09-17（C5 实做）</b>：上面那段与它后面的订正<b>原文逐字保留</b>（它是 C4 时点的真实状态）；C5 的 T034 给 {@code
 * ExportController#create}（{@code POST /api/v1/exports}，{@code export:create} + {@code
 * export-generate}）挂上了 {@code @RateLimit}，于是本类终于有了一个**同时**带权限码与限流的端点， T13 就落在那里（见 {@link
 * #permissionIsCheckedBeforeTheQuotaIsConsumed}）。判据不变，仍然是「403 且不消耗配额」。
 */
class RateLimitIT extends FixedClockTestSupport {

  private static final String OPEN = "/api/v1/public/track/open/999";
  private static final String CLICK = "/api/v1/public/track/click/999";

  /** 与 {@code EmailTrackController} 上的 {@code @RateLimit} 逐字一致（改造前的数字）。 */
  private static final int LIMIT = 60;

  /** 与 {@code FormService} 的 {@code RATE_LIMIT} 逐字一致（改造前的数字）。 */
  private static final int FORM_LIMIT = 3;

  private static final long WINDOW_SECONDS = 60L;

  private static final String SCOPE = "public-email-track";

  private final InMemoryRedisTestSupport redis = new InMemoryRedisTestSupport();

  /** 父类的 {@code @BeforeEach} 先跑（先装裸 mock），故这里的替身生效。 */
  @BeforeEach
  void installFunctionalRedis() {
    redis.clear();
    redis.install(redisTemplate, clock);
  }

  private String keyFor(String ip) {
    return "rl:" + SCOPE + ":ip:" + ip;
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder tracked(
      String path, String ip) {
    return get(path).header("X-Forwarded-For", ip);
  }

  /**
   * 打到超限为止，返回那次被拒的响应。前 {@code LIMIT} 次必须是 {@code allowed}（<b>负对照</b>）。
   *
   * <p>「前 60 次放行」这一半不能省：只断「第 61 次是 429」的话，「限流恒拒」这个反向劣解也满足它，
   * 而它会让端点整体不可用。<b>两个端点的成功状态不同</b>，故由调用方传入：{@code /open} 回 200 + GIF 字节流，{@code /click} 回 302。
   */
  private MvcResult exhaust(String path, String ip, ResultMatcher allowed) throws Exception {
    for (int i = 1; i <= LIMIT; i++) {
      mockMvc.perform(tracked(path, ip)).andExpect(allowed);
    }
    return mockMvc.perform(tracked(path, ip)).andReturn();
  }

  /**
   * 成功态断言。⚠️ <b>不查 JSON 体</b>：{@code /open} 的响应是 GIF 字节流，{@code jsonPath} 在它上面 只会以「不是
   * JSON」的方式炸掉，而那种红指向的是断言工具而不是被测代码。
   */
  private static final ResultMatcher OPEN_ALLOWS = status().isOk();

  private static final ResultMatcher CLICK_ALLOWS = status().is3xxRedirection();

  // ===== T2：正对照 —— 计数真的落进了存储（其余用例的地基） =====

  /**
   * 请求计数必须真的写进 Redis 的那个键上。
   *
   * <p><b>这是本类唯一一条专杀「没接线」的用例</b>：切面没生效、注解拼错、键名算错、 或者被 fail-open
   * 静默放行，都会让这个键不存在或值不对，而<b>其余每一条用例在那种状态下都可能照绿</b> （没接线 ⇒ 全部放行 ⇒ 「未达阈值返回 200」那些断言一字不变）。
   */
  @Test
  @DisplayName("T2：计数真写进了 Redis —— rl:public-email-track:ip:<ip> 累加到请求次数")
  void theCounterReallyLandsInRedis() throws Exception {
    String ip = "203.0.113.7";

    for (int i = 1; i <= 3; i++) {
      mockMvc.perform(tracked(OPEN, ip)).andExpect(status().isOk());
    }

    assertThat(redis.snapshot()).as("没接线时这个键根本不存在 —— 本批最大的假绿通道").containsEntry(keyFor(ip), 3L);
  }

  // ===== T1 + T14：超限即 429，且带标准的 Retry-After =====

  @Test
  @DisplayName("T1：第 61 次得 429 + error.code=RATE_LIMITED（前 60 次 200）")
  void overTheLimitIsRejectedWithTheSharedErrorCode() throws Exception {
    String ip = "203.0.113.8";

    MvcResult denied = exhaust(OPEN, ip, OPEN_ALLOWS);

    assertThat(denied.getResponse().getStatus()).isEqualTo(429);
    assertThat(denied.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .as("✗ 若仍是 TOO_MANY_REQUESTS，说明旧处理器还在、共享件没接管")
        .contains("\"code\":\"RATE_LIMITED\"")
        .doesNotContain("TOO_MANY_REQUESTS");
    // 计数继续累加是固定窗口的既定语义（窗口内不再放行，但仍在计数）
    assertThat(redis.snapshot()).containsEntry(keyFor(ip), (long) LIMIT + 1);
  }

  /**
   * {@code Retry-After} 必须是**窗口内的正数**。
   *
   * <p>两个方向都要钉：没有这个头 ⇒ 客户端只能盲目重试；值大于窗口 ⇒ 客户端被劝退得比实际需要的更久 （本仓先例：{@code AuthMfaIT} 断言 429 的 message
   * 里要有剩余秒数，头是它的机器可读版本）。
   *
   * <p>走 {@code /click}（另一个端点、另一个 IP）：两个端点共用同一个 scope，故这里同时也在钉「同一 scope 的键在两个端点之间是通的」——改造前它们本来就共用一个
   * {@code rateBuckets}。
   */
  @Test
  @DisplayName("T14：429 带 Retry-After，取值落在 (0, 窗口秒数] 内")
  void theRetryAfterHeaderIsWithinTheWindow() throws Exception {
    String ip = "203.0.113.9";

    MvcResult denied = exhaust(CLICK, ip, CLICK_ALLOWS);

    assertThat(denied.getResponse().getStatus()).isEqualTo(429);
    String retryAfter = denied.getResponse().getHeader("Retry-After");
    assertThat(retryAfter).as("429 的标准机器可读部分，缺了客户端只能盲重试").isNotNull();
    assertThat(Long.parseLong(retryAfter)).isPositive().isLessThanOrEqualTo(WINDOW_SECONDS);
  }

  // ===== T3：窗口走完恢复放行（钉住「首次才设窗」，D3/D4 的判据） =====

  /**
   * 窗口过期后恢复放行，且键真的被 TTL 清掉。
   *
   * <p>两个反向劣解都会在这里红：① 照 {@code AuthService.recordFailure} 的无条件续窗 ⇒ 每来一次就把窗口推后， 推 61 秒后<b>仍然
   * 429</b>（一个稳定 4 次/分钟的客户端永远过不去）；② 键没设 TTL ⇒ 计数永远满着 ⇒ 该主体永久 429，重启进程也无效（键在 Redis 里）。
   *
   * <p>「键已消失」这一句是 ② 的专属判据：只看「推完 61 秒得 200」的话，一个<b>把计数清零但没有 TTL</b> 的实现也能绿，而那种实现在真实 Redis
   * 里会留下永不回收的键。
   */
  @Test
  @DisplayName("T3：推进 61 秒 ⇒ 窗口为空的键被 TTL 清掉，同一 IP 恢复放行")
  void theWindowExpiresAndAllowsAgain() throws Exception {
    String ip = "203.0.113.10";

    assertThat(exhaust(OPEN, ip, OPEN_ALLOWS).getResponse().getStatus()).isEqualTo(429);

    // ⚠️ 2026-09-17（C6 = D3 实测后补）：窗口**中途**必须再打一次，这是「首次才设窗」与「每次都续窗」
    // 唯一的区分点。C5 版本的 T3 只在前一瞬打满、再一次性推 61 秒 ⇒ 两种实现给出同一个结果（都过期），
    // 上面那句「①无条件续窗会在这里红」当时是**空断言**——C6 的定向破坏 D3 实测**全绿**才发现（见
    // falsification-evidence.md §C）。补了这一次请求之后，续窗的实现会把 TTL 推到 t=90 ⇒ 61 秒时键还在。
    advanceSeconds(30);
    assertThat(mockMvc.perform(tracked(OPEN, ip)).andReturn().getResponse().getStatus())
        .as("窗口中途仍在计数（固定窗口的既定语义：窗口内不再放行，但计数继续累加）")
        .isEqualTo(429);

    advanceSeconds(31);

    assertThat(redis.containsKey(keyFor(ip)))
        .as("键没有 TTL ⇒ 该主体永久 429，而重启进程救不了（键在 Redis 里）")
        .isFalse();
    mockMvc.perform(tracked(OPEN, ip)).andExpect(status().isOk());
    assertThat(redis.snapshot()).containsEntry(keyFor(ip), 1L);
  }

  // ===== T4：存储故障 ⇒ fail-open（可用性立场） =====

  /**
   * Redis 不可用时<b>放行</b>，而不是 429 / 500 —— 即便此刻计数已经打满。
   *
   * <p>「计数已满」是这条用例的要害：只在一个空计数器上注入故障，无法区分「fail-open 生效」与 「本来就还没超限」。先打满再注入，得到的 200 就只可能来自 fail-open。
   *
   * <p>立场依据 {@code MfaStateStore} 的类 javadoc：那里说 fail-close 是<b>为 2FA 语义</b> （跳过验证）而立的，并指出
   * fail-open「对限流是合理的——代价只是限流暂时失效，而不放行会让整个系统 在 Redis 抖动时不可用」。（对照：{@code MfaFailClosedIT}
   * 钉的是同一个替身、同一类故障下的<b>相反</b>结论。）
   */
  @Test
  @DisplayName("T4：计数已满 + Redis 故障 ⇒ 仍放行（fail open），不放 429/500")
  void aStorageFailureIsFailOpenEvenWithAFullCounter() throws Exception {
    String ip = "203.0.113.11";
    assertThat(exhaust(OPEN, ip, OPEN_ALLOWS).getResponse().getStatus()).isEqualTo(429);

    redis.failOnKeyPrefix("rl:");

    mockMvc.perform(tracked(OPEN, ip)).andExpect(status().isOk());
  }

  // ===== T15：表单提交（服务侧委托形态）—— 400→429 订正的证伪判据 =====

  /**
   * 公开表单提交的频控：<b>3 次 / 60 秒</b>（改造前的数字，逐字未变），拒绝码由 <b>400 订正为 429</b>。
   *
   * <p><b>为什么必须有这一条</b>：本批把 {@code FormService} 的 400「提交过于频繁」改成 429 是一次<b>对外可观测变更</b> （虽然它是「实现向 036
   * 冻结契约靠拢」），而定向破坏台账里的 D7（<b>把它改回 400</b>）当时<b>点不出任何一条会变红的用例</b> —— 一条点不出名字的破坏等于没有护栏。本用例就是那条护栏。
   *
   * <p>形态与上面几条<b>刻意不同</b>（不是不一致）：这里的被限流单元是<b>服务方法</b> （{@code
   * FormService#submit}，在字段校验之前就跑），配额写在服务侧常量上，IP 由 {@code ClientIpResolver} 解析后 交给同一个 {@code
   * RateLimiter}。故本用例同时钉两件事：① HTTP 层是 429 + 统一信封（与 T1 同理）； ② 计数落在<b>同一个键族</b> {@code
   * rl:<scope>:ip:<ip>} 上（scope 是 {@code public-form-submit}，<b>不与</b>邮件追踪共桶——
   * 合并会让邮件客户端加载像素的自然高频挤掉表单提交的配额）。
   */
  @Test
  @DisplayName("T15：表单提交第 4 次得 429 + RATE_LIMITED（前 3 次 200），计数落在 rl:public-form-submit:ip:<ip>")
  void formSubmitIsRateLimitedWithTheCorrectedStatus() throws Exception {
    String ip = "203.0.113.13";
    long formId = createEnabledForm();

    for (int i = 1; i <= FORM_LIMIT; i++) {
      mockMvc.perform(formSubmit(formId, "138000000" + i + "0", ip)).andExpect(status().isOk());
    }

    MvcResult denied = mockMvc.perform(formSubmit(formId, "13800000040", ip)).andReturn();

    assertThat(denied.getResponse().getStatus())
        .as("✗ 若这里是 400，说明 400→429 的订正被改回去了（或 036 契约要求的 429 从未生效）")
        .isEqualTo(429);
    assertThat(denied.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .contains("\"code\":\"RATE_LIMITED\"")
        .doesNotContain("提交过于频繁");
    assertThat(redis.snapshot())
        .as("没接线时这个键根本不存在 —— 服务侧委托形态的核心判据")
        .containsEntry("rl:public-form-submit:ip:" + ip, (long) FORM_LIMIT + 1);
  }

  /** 建一个 ENABLED 表单（照 {@code FormIT.createForm}），拿到它的 id。 */
  private long createEnabledForm() throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/forms")
                    .header("Authorization", bearer(loginAndGetToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"限流表单\",\"fields\":[{\"field\":\"name\",\"label\":\"姓名\",\"type\":\"TEXT\",\"required\":true},{\"field\":\"phone\",\"label\":\"手机\",\"type\":\"TEL\",\"required\":true}],\"source\":\"WEBSITE\",\"status\":\"ENABLED\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  /**
   * 一次公开提交。⚠️ 每次换一个 phone：{@code FormService} 的防重复（同 phone 已有线索 ⇒ 409）在限流<b>之后</b>才跑， 故前 {@code
   * FORM_LIMIT} 次必须真的提交成功，否则负对照断的不是 200。
   */
  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      formSubmit(long formId, String phone, String ip) {
    return post("/api/v1/public/forms/" + formId + "/submit")
        .header("X-Forwarded-For", ip)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"限流测试\",\"phone\":\"" + phone + "\"}");
  }

  // ===== T12：钉住用户裁决 —— 登录**不**走本组件 =====

  /**
   * 登录路径不得被共享限流件接管：它已有<b>三层</b>自有防护，且那三层的语义与本组件<b>相反</b>。
   *
   * <p>本仓登录的既有防护是：① 017 的图形验证码（test profile 下 {@code crm.captcha.enabled=false} 故不触发）； ② 用户名失败计数 5
   * 次 / 15 分钟；③ 同 IP 失败计数 10 次 / 15 分钟。②③ {**是无条件续窗的失败计数**}，
   * 语义是「直到窗口走完为止」，而共享件是「窗口内容量、只在首次设窗」——把两者叠在一起会让 「登录失败 5 次」与「限流阈值」互相掩盖，且 ②③ 的阈值写在 {@code
   * AuthService} 的常量里、 与本组件的注解毫无关系。用户 2026-09-16 的裁决是**不加**。
   *
   * <p><b>判据不是「登录返回 401」</b>（那在加了限流之后的前几次也一样成立），而是这两条：
   *
   * <ol>
   *   <li>失败计数落在 {@code auth:fail:} 键族上 —— <b>正对照</b>，证明替身确实在工作、 下面的「没有 rl: 键」不是因为整条 Redis 路径都是死的；
   *   <li>{@code redis.snapshot()} 里<b>一个 {@code rl:} 键都没有</b> —— 只要有人给 login 挂上
   *       {@code @RateLimit}，<b>第一次请求</b>就会建键，与阈值无关。这个判据对阈值免疫， 而「连打 N 次不得 429」在 N 小于阈值时是空断言。
   * </ol>
   */
  @Test
  @DisplayName("T12：连续失败登录走的是 auth:fail: 自有锁定，不产生任何 rl: 键、也从不 429")
  void loginKeepsItsOwnLockoutAndNeverProducesRateLimitKeys() throws Exception {
    for (int i = 1; i <= 5; i++) {
      MvcResult failed = loginAttempt("admin", "wrong-password-" + i);
      assertThat(failed.getResponse().getStatus())
          .as("第 " + i + " 次失败：登录的既有防护返回 401，绝不是本组件的 429")
          .isEqualTo(401);
      // ⚠️ 必须显式指定 UTF-8：MockHttpServletResponse 的默认字符集是 ISO-8859-1，
      // 响应头里又没有 charset ⇒ 用无参重载读中文只会得到一串乱码，而断言会以「文案不匹配」的
      // 形式红掉，指向的是解码而不是被测行为（本用例正是这么红过一次）。
      assertThat(failed.getResponse().getContentAsString(StandardCharsets.UTF_8))
          .contains("INVALID_CREDENTIALS");
    }

    // 第 6 次：用户名失败计数（5 次）已满 ⇒ 走 AuthService 自己的锁定分支
    MvcResult locked = loginAttempt("admin", "wrong-password-6");
    assertThat(locked.getResponse().getStatus()).isEqualTo(401);
    assertThat(locked.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .as("② 的锁定文案必须出现 —— 证明登录的自有防护仍然在岗，不是被本组件取代了")
        .contains("登录失败次数过多");

    assertThat(redis.snapshot().keySet())
        .as("正对照：替身确实在工作（失败计数落到了 auth:fail: 上）")
        .anyMatch(key -> key.startsWith("auth:fail:"));
    assertThat(redis.snapshot().keySet())
        .as("给 login 挂 @RateLimit 会让第一次请求就建 rl: 键，与本断言无关阈值")
        .noneMatch(key -> key.startsWith("rl:"));
  }

  private MvcResult loginAttempt(String username, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .header("X-Forwarded-For", "203.0.113.12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
  }

  // ===== T13：权限先于限流 —— 未授权者得 403，且不消耗配额 =====

  /**
   * 未持权限码的调用者在切面层就被拒，<b>不占用配额</b>。
   *
   * <p><b>为什么必须是这个端点</b>：{@code ExportController#create}（{@code POST /api/v1/exports}）是本批唯一
   * <b>同时</b>带 {@code @RequirePermission("export:create")} 与 {@code @RateLimit(export-generate)}
   * 的端点。 而 SALES_REP 刻意<b>不</b>持有 {@code export:create}（原因见 {@code
   * PermissionEnforcementIT}：商机/工单两条导出没有 范围过滤，是整表导出）—— 于是它正好充当「未授权调用者」：权限码与限流都接在这条路上，
   * 两层的先后才成为可观测事实。
   *
   * <p><b>钉住的是哪条契约</b>：{@code PermissionAspect} 的 {@code @Order(10)} 在 {@code RateLimitAspect} 的
   * {@code @Order(20)} 之前 ⇒ <b>权限先判定</b>。若顺序反了（或有人把 {@code @Order} 摘掉、两者并列）， 未授权者会先被计数：他能靠刷 403
   * 把某个**已授权同事**的配额打满（键是 {@code user:<id>}，而 403 的调用者有自己的 id ⇒ 打满的是他自己的桶）—— 更直接的后果是「403
   * 也消耗配额」这件事本身没有判据。本用例就是它的判据。
   *
   * <p><b>三个断言缺一不可</b>：
   *
   * <ol>
   *   <li>403 + {@code PERMISSION_DENIED} —— 证明这个调用<b>真的走到了切面</b>（不是路径写错、不是 404/400）；
   *   <li>403 之后 {@code redis.snapshot()} 里<b>一个 {@code rl:} 键都没有</b> —— 未授权调用不建键；
   *   <li>ADMIN 调通之后桶里是 <b>1</b>，此后再打两次 403，桶<b>仍是 1</b> —— 阴性对照 ② 只在「桶本来就是空的」
   *       时才成立，这一条才排除了「计数写到了别处」。
   * </ol>
   *
   * <p>ADMIN 那一次同时是<b>正对照</b>：它证明这条路径上的限流是活的（键真的落到了 {@code
   * rl:export-generate:user:<adminId>}），否则整条用例会在「限流根本没接线」的世界里照绿。
   */
  @Test
  @DisplayName("T13：无 export:create 的角色调用导出端点得 403 PERMISSION_DENIED，且不产生/不累加任何 rl: 键")
  void permissionIsCheckedBeforeTheQuotaIsConsumed() throws Exception {
    String admin = loginAndGetToken();
    String rep = createUserWithoutExportCreate();

    // ① 未授权者：403（且是权限拒绝，不是别的）
    mockMvc
        .perform(createExport(rep))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ② 未授权调用不建键 —— 权限先于限流
    assertThat(redis.snapshot().keySet())
        .as("未授权调用者不该在任何配额桶上留痕；出现 rl: 键说明限流切面跑在了权限切面之前")
        .noneMatch(key -> key.startsWith("rl:"));

    // ③ 正对照：ADMIN 有 export:create ⇒ 调通，且配额真的落在 user 维度上
    mockMvc.perform(createExport(admin)).andExpect(status().isOk());
    String adminKey =
        redis.snapshot().keySet().stream()
            .filter(key -> key.startsWith("rl:export-generate:user:"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("没接线时这个键不存在 —— ADMIN 这一半就是它的正对照"));
    assertThat(redis.snapshot()).containsEntry(adminKey, 1L);

    // ④ 再打两次 403，桶的值不动 —— 阴性对照 ② 的补强（排除「计数写到了别处」）
    mockMvc.perform(createExport(rep)).andExpect(status().isForbidden());
    mockMvc.perform(createExport(rep)).andExpect(status().isForbidden());
    assertThat(redis.snapshot()).as("未授权调用不得消耗配额：桶值必须仍是 1").containsEntry(adminKey, 1L);
    assertThat(redis.snapshot().keySet().stream().filter(key -> key.startsWith("rl:")).count())
        .as("配额桶总共只该有 ADMIN 那一个；多出来就说明 403 的调用者也在建键/计数")
        .isEqualTo(1L);
  }

  private static final String PROBE_PASSWORD = "Passw0rd!";

  /**
   * 建一个**不持** {@code export:create} 的 SALES_REP 并返回其令牌。
   *
   * <p>用「新建用户 + 登录」而不是拿种子里某个角色：SALES_REP 的权限集合由种子/V75 决定，本用例要的正是 <b>该角色确实没有 export:create</b>
   * 这一事实（{@code PermissionEnforcementIT} 已单独钉过），此处沿用同一角色以共享那条结论。
   */
  private String createUserWithoutExportCreate() throws Exception {
    String admin = loginAndGetToken();
    String username = "ratelimitrep";
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\""
                        + username
                        + "\",\"password\":\""
                        + PROBE_PASSWORD
                        + "\",\"displayName\":\"限流探针\",\"role\":\"SALES_REP\"}"))
        .andExpect(status().isCreated());
    return loginAndGetToken(username, PROBE_PASSWORD);
  }

  /** 建导出任务（{@code export:create} + {@code export-generate} 的那个端点）。 */
  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createExport(
      String token) {
    return post("/api/v1/exports")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"exportType\":\"CUSTOMER\"}");
  }
}
