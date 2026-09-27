package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import com.crm.AbstractIntegrationTest;
import com.crm.config.AiClientFactory;
import com.crm.config.AiStatus;
import com.crm.support.AnthropicTestResponses;
import com.crm.support.InMemoryRedisTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 104 生成端点（P1 邮件草稿）的<b>集成面</b>：I2–I6 + 日预算闸的端到端读数。
 *
 * <p><b>本类问的是"装配与接线"，不是"出网点本身"</b>（后者在 {@code AiContentServiceTest}）。所以它必须走真实 HTTP
 * 链路、真实数据库、真实权限切面——只有把提示词真的<b>捕下来</b>，才能回答"客户的哪些数据离开了本系统"。
 *
 * <p><b>唯一的出站拦截点是 {@code @MockBean AiClientFactory}</b>：{@code AiContentService} 通过它拿 {@code
 * AnthropicClient}，而 {@link com.crm.config.AiClientFactory} 在已配置时会建出真的 OkHttp 客户端。整条链上再没有第二个
 * 注入点，故把它换成桩、再由 {@code client.messages().create(...)} 捕获 {@code MessageCreateParams}，是本类唯一的
 * 观测手段（也是"零出站"这条断言的落点：没被踩到时它就是"一个字节都没出去"）。
 *
 * <p><b>为什么本类要自己装功能型 Redis 替身</b>：父类 {@code AbstractIntegrationTest} 装的是裸 {@code
 * mock(ValueOperations)}——{@code get} 恒为 {@code null} ⇒ {@code RateLimitStore.usage} 恒返回 0 ⇒
 * <b>日预算闸是个静默 no-op</b>。在那种替身上写"预算耗尽 ⇒ 429"，它只会以"怎么点都是 200"的形式红，或者更糟：把闸写坏了也照样绿。 故 {@link
 * InMemoryRedisTestSupport} 由本类自己装（键族隔离那条 U7 也因此能验到<b>接线</b>，而不是只验到 mock）。
 *
 * <p><b>为什么配置项要在这里覆盖</b>：本仓 {@code application.yml} 的出厂档是 {@code crm.ai.enabled=false}（零出站、 零成本）。要把
 * I2–I6 跑到出网点之前，必须显式打开开关并给出 base-url/api-key，同时把模型主机加进出站白名单—— 否则 {@code AiStatus} 这个 {@code
 * ApplicationRunner} 会在启动期直接拒绝启动（那是 FR-005 的判据，不是本类的障碍）。
 *
 * <p>⚠️ <b>I1（未配置 ⇒ 409 + 零出站 + 零审计）不在此类</b>：它要的是"出厂档"这个<b>相反</b>的配置，而同一个 Spring 上下文里 {@code
 * AiStatus} 只有一个实例。硬塞进来的话，要么两个配置互相覆盖（谁跑在后面谁说了算），要么为一段配置 造两个上下文。故 I1 归 {@code
 * AiContentUnconfiguredIT}——那是<b>按配置分档</b>，不是把一条判据拆散。
 */
@TestPropertySource(
    properties = {
      "crm.ai.enabled=true",
      "crm.ai.base-url=https://api.anthropic.com",
      // 明显的假值：本类不连任何真实服务（客户端工厂已被换成桩），但它必须"非空"才能让 isConfigured() 为真。
      "crm.ai.api-key=test-key-not-a-real-secret",
      // 出厂档白名单为空 = 默认全拒，故必须点名放行——这也是启动期那一次校验的唯一依据。
      "crm.outbound.allowed-hosts=api.anthropic.com"
    })
class AiContentIT extends AbstractIntegrationTest {

  /** 本项唯一的权限码；夹具角色必须拿到它，否则请求会卡在权限切面上（那条判据在 {@code AiPermissionGrantIT}）。 */
  private static final String AI_GENERATE = "ai:generate";

  private static final String ENDPOINT = "/api/v1/ai/email-draft";

  /** 哨兵：写进库、又必须不出现在提示词里的那些值。 */
  private static final String NAME_SENTINEL = "哨兵客户名甲";

  private static final String CONTACT_SENTINEL = "哨兵联系人甲";

  private static final String STATUS_SENTINEL = "哨兵状态甲";

  /** 联系人表里的那条：客户档案上的联系人被遮住时，装配器会退到这张表（本类要证明这条退路真的跑了）。 */
  private static final String CONTACT_TABLE_NAME = "哨兵联系人表乙";

  /** 生成是**只读**的：这五张业务表的行数在整个调用前后一行都不该变。 */
  private static final List<String> BUSINESS_TABLES =
      List.of("customer", "opportunity", "sales_opportunity", "contact", "follow_up");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private AiStatus aiStatus;

  @MockBean private AiClientFactory clientFactory;

  private InMemoryRedisTestSupport redis;

  private AnthropicClient client;

  private MessageService messageService;

  private BuiltinFieldPermissionFixture fixture;

  @BeforeEach
  void setUp() throws Exception {
    // 父类的 stubRedis() 先跑（JUnit 5 保证父类 @BeforeEach 在前），这里把它换成功能型替身。
    // 每个方法一份新实例 ⇒ 计数不跨方法泄漏，无需 clear()。
    redis = new InMemoryRedisTestSupport();
    redis.install(redisTemplate);

    // 出站的唯一拦截点。两个 mock 都**不在此处打桩 create**：I3/I5 要断"一个字节都没出去"，而在 setUp 里打桩
    // 会让那些断言失去意义（打桩本身是一次调用，虽然 Mockito 会把打桩调用从校验里剔除，但读代码的人无从分辨）。
    client = mock(AnthropicClient.class);
    messageService = mock(MessageService.class);
    when(clientFactory.client()).thenReturn(Optional.of(client));
    when(client.messages()).thenReturn(messageService);

    fixture = new BuiltinFieldPermissionFixture(mockMvc, objectMapper, loginAndGetToken());
    // extraPermissions 而不是改夹具基线：那个角色的权限面被 102 的六个用例类共用（见夹具里本重载的 javadoc）。
    fixture.ensureRole(List.of(AI_GENERATE, "contact:create"));
  }

  // ===== I2：字段级权限（最值钱的一条） =====

  /**
   * HIDDEN 的内建字段：<b>库里真有值、提示词里绝不出现</b>。
   *
   * <p>三个方向一起断，少一个都能让它假绿：
   *
   * <ol>
   *   <li><b>值真的在库里</b>——否则"提示词里没有"可能只是因为那个值压根没被存过（负断言的经典假绿通道）；
   *   <li><b>没遮的字段照常进</b>（正对照）——否则"整段上下文都是空的"也能让第 3 条通过；
   *   <li><b>被遮的字段的值不出现</b>，且<b>退路真的跑了</b>（客户档案上的联系人被遮 ⇒ 退到联系人表取那一条）。 第 3
   *       条里的两个见证是配套的：只断"不含哨兵值"的话，一个"把 contactPerson 整段丢掉"的实现照样绿， 而那是把功能写坏了（FR-013
   *       要求的是"该遮的遮"，不是"什么都不送"）。
   * </ol>
   *
   * <p>⚠️ 提示词<b>不走</b>出参收口点（{@code FieldMaskingResponseBodyAdvice} 只擦 HTTP 响应体），所以这道过滤 没有任何隐式兜底：漏判
   * = 客户数据直接出网，而响应里什么都看不出来。
   */
  @Test
  @DisplayName("I2 FLS HIDDEN(contactPerson/status)：值在库里、不进提示词；未遮字段照常进、退到联系人表的那条也进")
  void hiddenBuiltinFieldsAreNeverSentToTheModel() throws Exception {
    BuiltinFieldPermissionFixture.Actor actor = fixture.createUser("ai_fls_");
    long customerId = createCustomerWithSentinels(actor.token());
    createContact(actor.token(), customerId, CONTACT_TABLE_NAME);

    assertThat(storedContactAndStatus(customerId))
        .as("前提：哨兵真写进了库。否则下面的「不含」可能只因它们从未被存过")
        .containsEntry("contact_person", CONTACT_SENTINEL)
        .containsEntry("status", STATUS_SENTINEL);

    assertThat(fixture.configureBuiltin("CUSTOMER", "contactPerson", "HIDDEN").status())
        .as("配权限失败会让下面那条断言以「本来就没遮」以外的原因说话")
        .isEqualTo(201);
    assertThat(fixture.configureBuiltin("CUSTOMER", "status", "HIDDEN").status()).isEqualTo(201);

    stubReply("草稿正文");
    BuiltinFieldPermissionFixture.Res res = draft(actor.token(), bodyFor(customerId));

    assertThat(res.status()).isEqualTo(200);
    String prompt = promptSentToTheModel();
    assertThat(prompt)
        .as("正对照：没配权限的字段必须在提示词里，否则下面的「不含」会以「什么也没送」的形式假绿")
        .contains(NAME_SENTINEL, "哨兵公司甲");
    assertThat(prompt)
        .as("客户档案上的联系人被遮住 ⇒ 装配器应退到联系人表（这是 FR-013 的实现，不是可选的优化）")
        .contains(CONTACT_TABLE_NAME);
    assertThat(prompt)
        .as("HIDDEN 的字段值绝不出网：提示词不经过出参收口点，只能靠装配时自己判")
        .doesNotContain(CONTACT_SENTINEL)
        .doesNotContain(STATUS_SENTINEL);
  }

  // ===== I3：跨 owner 不泄漏 =====

  /**
   * 别人名下的客户：<b>403 且一个字节都不出网</b>。
   *
   * <p>两条断言必须成对：只断 403 的话，一个"先把上下文装配好、最后才判权"的实现照样绿——而那时数据已经进过提示词了； 只断"没出网"的话，一个"静默返回空草稿"的实现也绿。
   *
   * <p>正对照用<b>同一个请求体</b>换成归属者：拿到 200 且提示词里确实是那条数据 ⇒ 上面那条 403 讲的是"归属"， 不是"端点坏了 / 请求体不对"。
   */
  @Test
  @DisplayName("I3 跨 owner：不可见的客户 ⇒ 403 且零出站；归属者同一请求 ⇒ 200（正对照）")
  void anotherOwnersCustomerIsRejectedBeforeEgress() throws Exception {
    BuiltinFieldPermissionFixture.Actor owner = fixture.createUser("ai_owner_");
    BuiltinFieldPermissionFixture.Actor outsider = fixture.createUser("ai_outsider_");
    // 客户由 owner 建 ⇒ 归 owner（新建用户的数据范围是 SELF）；outsider 与它同角色、同权限，只是不是归属者。
    long customerId = createCustomerWithSentinels(owner.token());

    BuiltinFieldPermissionFixture.Res denied = draft(outsider.token(), bodyFor(customerId));

    assertThat(denied.status()).as("同一个角色、同一个请求体，只有归属不同：这条 403 只能来自数据范围判定").isEqualTo(403);
    assertThat(denied.body().path("error").path("code").asText())
        .as(
            "本仓有两个 403 出口：权限切面给 PERMISSION_DENIED，数据范围判定给 FORBIDDEN。"
                + "这条请求已经过了切面（该角色持 ai:generate），故它必须是后者——写成 PERMISSION_DENIED 就是另一件事在说话")
        .isEqualTo("FORBIDDEN");
    verifyNoInteractions(messageService);

    stubReply("草稿正文");
    BuiltinFieldPermissionFixture.Res allowed = draft(owner.token(), bodyFor(customerId));
    assertThat(allowed.status()).isEqualTo(200);
    assertThat(promptSentToTheModel()).contains(NAME_SENTINEL);
  }

  // ===== I4：上游不可用 =====

  @Test
  @DisplayName("I4 上游不可达 ⇒ 503（受控失败，不是 500），且不写审计、不动业务表")
  void unreachableUpstreamIsAControlledFailure() throws Exception {
    BuiltinFieldPermissionFixture.Actor actor = fixture.createUser("ai_upstream_");
    long customerId = createCustomerWithSentinels(actor.token());
    Map<String, Long> rowsBefore = businessRowCounts();
    long auditsBefore = generateAuditCount();

    AnthropicIoException down = new AnthropicIoException("connection reset");
    when(messageService.create(any(MessageCreateParams.class))).thenThrow(down);

    BuiltinFieldPermissionFixture.Res res = draft(actor.token(), bodyFor(customerId));

    assertThat(res.status()).isEqualTo(503);
    assertThat(res.body().path("error").path("code").asText()).isEqualTo("AI_UPSTREAM_UNAVAILABLE");
    assertThat(businessRowCounts()).as("失败路径不留半条记录").isEqualTo(rowsBefore);
    assertThat(generateAuditCount()).as("失败路径不写审计（也没法写 token 用量）").isEqualTo(auditsBefore);
  }

  // ===== I5：入参校验在出站之前 =====

  @Test
  @DisplayName("I5 入参非法 ⇒ 400 且零出站；恰好等于上限的 instruction ⇒ 200（正对照）")
  void invalidInputIsRejectedBeforeAnyEgress() throws Exception {
    BuiltinFieldPermissionFixture.Actor actor = fixture.createUser("ai_input_");
    long customerId = createCustomerWithSentinels(actor.token());

    // 三个非法请求先都发出去，再判零出站：**零出站那一句必须排在状态码之前**。
    // 定向破坏 D5（把 instruction 上限的校验挪到出站之后）实测给出的是 `expected: 400 but was: 500`——
    // 因为出站一旦发生，状态码先变，排在后面的 verifyNoInteractions 就永远不会开口。安全面该先说。
    BuiltinFieldPermissionFixture.Res noIds = draft(actor.token(), "{}");
    BuiltinFieldPermissionFixture.Res tooLong =
        draft(actor.token(), bodyWithInstruction(customerId, "x".repeat(4001)));
    BuiltinFieldPermissionFixture.Res badTone =
        draft(actor.token(), "{\"customerId\":" + customerId + ",\"tone\":\"SHOUTING\"}");

    verifyNoInteractions(messageService);

    assertThat(noIds.status()).as("两个 id 都不给：无法确定上下文").isEqualTo(400);
    assertThat(tooLong.status()).as("超长 1 个字符即拒（400 而不是 422：全仓没有通用的 422 校验码）").isEqualTo(400);
    assertThat(badTone.status()).as("非法枚举值：服务层判（不放 @Valid，为的是能给出可读的中文说明）").isEqualTo(400);

    // 正对照：恰好 4000 字（等于上限）必须通过 —— 否则上面那条会以「只要带 instruction 就拒」的形式假绿。
    stubReply("草稿正文");
    assertThat(draft(actor.token(), bodyWithInstruction(customerId, "x".repeat(4000))).status())
        .isEqualTo(200);
  }

  // ===== I6：只读 + 审计落点 =====

  @Test
  @DisplayName("I6 生成是只读的：只有审计增长，且审计只落元数据、锚在客户上")
  void generationWritesNothingButOneMetadataOnlyAuditRow() throws Exception {
    BuiltinFieldPermissionFixture.Actor actor = fixture.createUser("ai_write_");
    long customerId = createCustomerWithSentinels(actor.token());
    Map<String, Long> rowsBefore = businessRowCounts();
    long auditsBefore = generateAuditCount();

    stubReply("草稿正文");
    assertThat(draft(actor.token(), bodyFor(customerId)).status()).isEqualTo(200);

    assertThat(businessRowCounts()).as("生成不改业务数据：这五张表一行都不该动").isEqualTo(rowsBefore);
    assertThat(generateAuditCount()).isEqualTo(auditsBefore + 1);
    assertThat(latestGenerateAudit().get("detail").toString())
        .as("审计只放元数据（FR-016）：能力名 / token / 耗时，提示词与客户数据一律不进")
        .contains("capability=email-draft")
        .contains("inputTokens=10")
        .contains("outputTokens=20")
        .doesNotContain(NAME_SENTINEL)
        .doesNotContain(CONTACT_SENTINEL);
    assertThat(((Number) latestGenerateAudit().get("entity_id")).longValue())
        .as("锚点：本能力的载体是客户")
        .isEqualTo(customerId);
  }

  // ===== FR-015/FR-017：日预算闸的端到端读数 =====

  /**
   * 日预算耗尽 ⇒ 429 + {@code Retry-After}，且<b>闸在出站之前</b>。
   *
   * <p>不预置 Redis 键，而是让**第一次调用自己把额度烧光**：桩返回的 {@code usage} 就是闸认的量（FR-016：不估算）， 故把 input token 打成
   * {@code budget} 即可。好处是这条用例不复制键形（那件事的判据在 {@code AiTokenBudgetTest}）， 也不会因为"预置的键拼错"而以"压根没耗尽"的形式假绿。
   *
   * <p>顺带把键族**接线**验掉：单测里 {@code RateLimitStore} 是桩，"这个键真的落进了 Redis"在那里问不出来。
   */
  @Test
  @DisplayName("日预算耗尽 ⇒ 429 + Retry-After，第二次请求零出站；键 real 落进 Redis 且与 022 的键族不混")
  void exhaustedDailyBudgetIsAControlled429() throws Exception {
    long budget = aiStatus.dailyTokenBudget();
    assertThat(budget).as("本用例的前提是闸开着（<=0 = 关闸）；关着的部署上它会以「怎么点都 200」的形式假绿").isGreaterThan(0);

    BuiltinFieldPermissionFixture.Actor actor = fixture.createUser("ai_budget_");
    long customerId = createCustomerWithSentinels(actor.token());

    Message burnTheWholeBudget =
        AnthropicTestResponses.reply("草稿正文", AnthropicTestResponses.END_TURN, budget, 0);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(burnTheWholeBudget);
    assertThat(draft(actor.token(), bodyFor(customerId)).status()).isEqualTo(200);

    Set<String> budgetKeys =
        redis.snapshot().keySet().stream()
            .filter(key -> key.contains("budget:user:" + actor.id()))
            .collect(Collectors.toSet());
    assertThat(budgetKeys).as("记账必须真的落到 Redis 上：单测里 store 是桩，这条是唯一的接线判据").hasSize(1);
    assertThat(budgetKeys)
        .as("键形见 AiTokenBudget.key：`ai:gen:budget:user:{userId}:{yyyyMMdd}`（本行是逐字复制，键一变即红）")
        .allSatisfy(key -> assertThat(key).matches("ai:gen:budget:user:\\d+:\\d{8}"));
    assertThat(redis.snapshot().keySet())
        .as("022 的忽略集是同名前缀下的另一族，本项不得碰它（反之亦然）")
        .noneMatch(key -> key.startsWith("ai:ignore:"));

    BuiltinFieldPermissionFixture.Res denied = draft(actor.token(), bodyFor(customerId));

    assertThat(denied.status()).isEqualTo(429);
    assertThat(denied.body().path("error").path("code").asText()).isEqualTo("RATE_LIMITED");
    assertThat(denied.header("Retry-After"))
        .as("429 的机器可读部分：到明天零点的秒数（0 < x <= 86400），不是某个窗口长度")
        .isNotNull();
    long retryAfter = Long.parseLong(denied.header("Retry-After"));
    assertThat(retryAfter).isBetween(1L, 86_400L);
    verify(messageService, times(1)).create(any(MessageCreateParams.class));
    assertThat(generateAuditCount()).as("拒绝路径不写审计（这次调用没有产生任何用量）").isEqualTo(1);
  }

  // ===== 夹具与读数 =====

  private static String bodyFor(long customerId) {
    return "{\"customerId\":" + customerId + "}";
  }

  private static String bodyWithInstruction(long customerId, String instruction) {
    return "{\"customerId\":" + customerId + ",\"instruction\":\"" + instruction + "\"}";
  }

  private BuiltinFieldPermissionFixture.Res draft(String token, String body) throws Exception {
    return fixture.call(token, HttpMethod.POST, ENDPOINT, body);
  }

  /** 打一条上游回复的桩。⚠️ 先造值、再 {@code when}：SDK 的构造异常抛在实参位置上会污染桩的状态。 */
  private void stubReply(String text) {
    Message reply = AnthropicTestResponses.reply(text, AnthropicTestResponses.END_TURN, 10, 20);
    when(messageService.create(any(MessageCreateParams.class))).thenReturn(reply);
  }

  /** 最近一次真的送给模型的 user 消息——"客户的哪些数据离开了本系统"的唯一直接证据。 */
  private String promptSentToTheModel() {
    ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
    verify(messageService).create(captor.capture());
    return captor.getValue().messages().get(0).content().asString();
  }

  private long createCustomerWithSentinels(String token) throws Exception {
    BuiltinFieldPermissionFixture.Res res =
        fixture.call(
            token,
            HttpMethod.POST,
            "/api/v1/customers",
            "{\"name\":\""
                + NAME_SENTINEL
                + "\",\"company\":\"哨兵公司甲\""
                + ",\"contactPerson\":\""
                + CONTACT_SENTINEL
                + "\",\"status\":\""
                + STATUS_SENTINEL
                + "\",\"phone\":\"13900004444\",\"email\":\"ai-content@example.com\"}");
    assertThat(res.status()).as("建客户失败：%s %s", res.status(), res.body()).isEqualTo(201);
    return res.body().path("data").path("id").asLong();
  }

  /** 联系人表里的一条：客户档案上的联系人字段被遮住时，装配器要靠它给出称呼。 */
  private void createContact(String token, long customerId, String name) throws Exception {
    BuiltinFieldPermissionFixture.Res res =
        fixture.call(
            token,
            HttpMethod.POST,
            "/api/v1/contacts",
            "{\"customerId\":" + customerId + ",\"name\":\"" + name + "\"}");
    assertThat(res.status()).as("建联系人失败：%s %s", res.status(), res.body()).isEqualTo(201);
  }

  /** 直接读库里的那两列（列名小写：测试库 URL 带 {@code DATABASE_TO_LOWER=TRUE}）。 */
  private Map<String, Object> storedContactAndStatus(long customerId) {
    return jdbcTemplate.queryForMap(
        "SELECT contact_person, status FROM customer WHERE id = ?", customerId);
  }

  private Map<String, Long> businessRowCounts() {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String table : BUSINESS_TABLES) {
      Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
      counts.put(table, count == null ? 0L : count);
    }
    return counts;
  }

  /** 本动作的审计行数。⚠️ 动作名在此是**字面量**（{@code AiContentService.AUDIT_ACTION} 是包级可见的）；改名的化这条会以"数到 0 行"红。 */
  private long generateAuditCount() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action = 'GENERATE' AND entity_type = 'CUSTOMER'",
            Long.class);
    return count == null ? 0L : count;
  }

  private Map<String, Object> latestGenerateAudit() {
    return jdbcTemplate.queryForMap(
        "SELECT entity_id, detail FROM audit_log WHERE action = 'GENERATE'"
            + " AND entity_type = 'CUSTOMER' ORDER BY id DESC LIMIT 1");
  }
}
