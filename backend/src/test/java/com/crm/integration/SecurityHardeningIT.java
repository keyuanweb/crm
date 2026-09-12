package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 安全加固（FR-G13、FR-G14、FR-G16，T031）。
 *
 * <p><b>本类断言的都是修复后应有的行为，改造前必然失败</b>：出站地址当前只判 {@code
 * startsWith("http://")}（集成通道）或完全不判（Webhook），三个控制器的变更端点无任何权限注解，定时导出的读取端点接受任意 {@code userId}。实测改造前
 * <b>42 项中 27 项红</b>：FR-G13 的负向 17／18（唯一例外见 {@link #integrationChannelUrlMustBeRejected} 对非 HTTP
 * 协议的说明）、FR-G16 的 3／3、FR-G14 的 7／7。<b>其余 15 项改造前即为绿</b>——它们是刻意设置的正对照与护栏 （白名单放行 2、持码角色不被拒
 * 7、无权限码端点仍可用 5、非 HTTP 协议 1），不随本次修复转绿，其作用是在实现之后继续挡退化。
 *
 * <p><b>为什么把三类断言收在同一个类里</b>：它们同属 US3 的同一批"既有鉴权缺陷"，共享同一套夹具（管理员 / 受限角色 / 越权用户），
 * 拆成三个类会重复造夹具且失去"这批缺陷被一次性收口"的可读性。代价是本类的红不是单点红，因此每个端点、每种敌意地址都<b>单独成为一个用例</b>， 免得一个断言失败掩盖其余。
 *
 * <p><b>为什么要显式配置出站白名单</b>：{@code crm.outbound.allowed-hosts} 的默认值是空，即<b>默认全拒</b>
 * ——连公网地址也不放行。若不在本类放开白名单，则"拒绝回环／私网／链路本地／云元数据"这些断言将与"校验器拒绝一切"这一退化实现无法区分：
 * 一个恒抛异常的校验器能让全部负向断言通过。故此处显式给出白名单，并配上<b>正对照</b>（白名单内地址必须被放行）， 使"校验器在做真实判定"成为可观测事实。
 */
@TestPropertySource(
    properties = {
      // 主机名形式与地址形式各一，覆盖白名单的两种取值形态（data-model.md §4）
      "crm.outbound.allowed-hosts=allowed.example.com,10.9.9.9"
    })
class SecurityHardeningIT extends AbstractIntegrationTest {

  /**
   * 必须被拒的出站地址，逐条对应 FR-G13 列举的四类目标 + 非 HTTP 协议。
   *
   * <p>{@code public.example.com} 一条单列出来：它既非回环也非私网，用于验证<b>白名单语义是"默认全拒"</b>
   * ——配置了白名单之后，未列入的<b>公网</b>地址同样必须被拒，否则白名单就成了"只挡私网"的黑名单。
   */
  static Stream<String> hostileOutboundUrls() {
    return Stream.of(
        "http://127.0.0.1:8081/hook", // 回环（地址形式）
        "http://localhost/hook", // 回环（主机名形式）
        "http://10.1.2.3/hook", // 私有网段 10/8
        "http://172.16.0.9/hook", // 私有网段 172.16/12
        "http://192.168.1.10/hook", // 私有网段 192.168/16
        "http://169.254.1.1/hook", // 链路本地
        "http://169.254.169.254/latest/meta-data/", // 云元数据端点
        "http://public.example.com/hook", // 公网但未列入白名单 → 默认全拒
        "file:///etc/passwd"); // 非 HTTP 协议
  }

  // ===== FR-G13：出站地址校验 =====

  @ParameterizedTest(name = "Webhook 回调地址被拒：{0}")
  @MethodSource("hostileOutboundUrls")
  @DisplayName("Webhook 回调地址指向回环／私网／链路本地／云元数据时必须被拒（FR-G13）")
  void webhookCallbackUrlMustBeRejected(String url) throws Exception {
    // 改造前：WebhookService 对地址完全无校验，直接取用 → 201，本断言红。
    assertClientRejected(createWebhook(url), "创建 Webhook 回调地址 " + url);
  }

  @ParameterizedTest(name = "集成通道地址被拒：{0}")
  @MethodSource("hostileOutboundUrls")
  @DisplayName("集成通道地址指向回环／私网／链路本地／云元数据时必须被拒（FR-G13）")
  void integrationChannelUrlMustBeRejected(String url) throws Exception {
    // 改造前：IntegrationChannelService.validate 只判 startsWith("http://") → 201，本断言红。
    // 例外：file:///etc/passwd —— 该路径已有的 scheme 判定本就会拒非 http(s)，故此项改造前即绿；
    // 而 Webhook 路径连 scheme 都不判，故同一输入在那边是红的。这一处不对称是既存事实，不是遗漏。
    assertClientRejected(createChannel(url), "创建集成通道地址 " + url);
  }

  /**
   * 正对照：白名单内的主机名必须被放行。
   *
   * <p>没有这条，"全部负向断言通过"可能只是校验器拒绝一切的假象（见类注释）。
   */
  @Test
  @DisplayName("白名单内的主机名被放行（FR-G13 正对照：校验器在做真实判定）")
  void webhookCallbackUrlInWhitelistAccepted() throws Exception {
    assertCreated(createWebhook("http://allowed.example.com/hook"), "白名单主机名应被放行");
  }

  /**
   * 正对照：白名单内的<b>私网地址</b>必须被放行。
   *
   * <p>FR-G13 要求白名单是"合法内网集成的显式出口"——若白名单只能放行公网地址，该出口就是无效的，
   * 而"拒绝私网"与"必须支持合法私网集成"两个要求会互相矛盾。故这一条同时验证了白名单的<b>优先级</b>： 显式声明应当压过网段判定。
   */
  @Test
  @DisplayName("白名单内的私网地址被放行（FR-G13：白名单是合法内网集成的显式出口）")
  void integrationChannelUrlInWhitelistAccepted() throws Exception {
    assertCreated(createChannel("http://10.9.9.9/hook"), "白名单私网地址应被放行");
  }

  // ===== FR-G16：定时导出读取端点不得越权 =====

  @Test
  @DisplayName("他人不能按 userId 读到我的定时导出订阅（FR-G16）")
  void scheduledExportListMustNotLeakOtherUsersSubscription() throws Exception {
    ensureScheduledExportRole();
    Actor owner = createUserWithRole("sched_owner_", SCHEDULED_EXPORT_ROLE);
    long exportId = createScheduledExport(owner);

    Actor other = createUserWithRole("sched_other_", SCHEDULED_EXPORT_ROLE);
    // 传的必须是 owner 的标识——传调用方自己的标识只会拿到自己的（空）列表，那样断言与缺陷无关：
    // 本用例要考的是"能否指定他人标识去读"，故请求参数本身就得是那个他人的标识。
    JsonNode items = jsonOf(callRaw(other.token(), HttpMethod.GET, listExports(owner.id()), null));

    for (JsonNode item : items) {
      if (item.path("id").asLong() == exportId) {
        throw new AssertionError(
            "越权读取：用户 " + other.id() + " 用 userId 参数读到了用户 " + owner.id() + " 的定时导出 " + exportId);
      }
    }
  }

  @Test
  @DisplayName("他人不能按主键读到我的定时导出详情（FR-G16）")
  void scheduledExportDetailMustNotBeReadableByOthers() throws Exception {
    ensureScheduledExportRole();
    Actor owner = createUserWithRole("sched_owner2_", SCHEDULED_EXPORT_ROLE);
    long exportId = createScheduledExport(owner);

    Actor other = createUserWithRole("sched_other2_", SCHEDULED_EXPORT_ROLE);
    // 改造前：详情端点无归属校验 → 200，本断言红。
    int status = call(other.token(), HttpMethod.GET, "/api/v1/scheduled-exports/" + exportId, null);
    if (status / 100 == 2) {
      throw new AssertionError(
          "越权读取：用户 " + other.id() + " 读到了他人定时导出详情 " + exportId + "（状态 " + status + "）");
    }
  }

  @Test
  @DisplayName("他人不能读到我的定时导出执行记录（FR-G16）")
  void scheduledExportExecutionsMustNotBeReadableByOthers() throws Exception {
    ensureScheduledExportRole();
    Actor owner = createUserWithRole("sched_owner3_", SCHEDULED_EXPORT_ROLE);
    long exportId = createScheduledExport(owner);

    Actor other = createUserWithRole("sched_other3_", SCHEDULED_EXPORT_ROLE);
    // 改造前：执行记录端点同样无归属校验 → 200（空列表也算"读到了"，因为归属根本没被判定），本断言红。
    int status =
        call(
            other.token(),
            HttpMethod.GET,
            "/api/v1/scheduled-exports/" + exportId + "/executions",
            null);
    if (status / 100 == 2) {
      throw new AssertionError(
          "越权读取：用户 " + other.id() + " 读到了他人定时导出执行记录 " + exportId + "（状态 " + status + "）");
    }
  }

  // ===== FR-G14：三个控制器的权限门禁 =====

  /**
   * 受权限码保护的端点清单。{@code {policyId}}／{@code {self}} 由用例在运行时替换。
   *
   * <p>每个端点单列一条：若合成一个方法，"第一个失败"会掩盖其余端点同样失守的事实。
   */
  static Stream<Arguments> guardedEndpoints() {
    String policyBody =
        "{\"entityType\": \"CUSTOMER\", \"retentionDays\": 30, \"actionType\": \"ARCHIVE\"}";
    String scheduledBody =
        "{\"entityType\": \"CUSTOMER\", \"exportFormat\": \"CSV\", \"cronExpression\": \"0 0 1 * *\"}";
    return Stream.of(
        Arguments.of(
            "合规导出（export:compliance）",
            HttpMethod.POST,
            "/api/v1/data-retention/compliance-export?entityType=CUSTOMER&userId=1",
            null),
        Arguments.of(
            "创建数据保留策略（retention:create）",
            HttpMethod.POST,
            "/api/v1/data-retention/policies",
            policyBody),
        Arguments.of(
            "编辑数据保留策略（retention:update）",
            HttpMethod.PUT,
            "/api/v1/data-retention/policies/{policyId}",
            policyBody),
        Arguments.of(
            "删除数据保留策略（retention:delete）",
            HttpMethod.DELETE,
            "/api/v1/data-retention/policies/{policyId}",
            null),
        Arguments.of(
            "执行归档（retention:execute）", HttpMethod.POST, "/api/v1/data-retention/execute", null),
        Arguments.of(
            "创建定时导出（export:scheduled）",
            HttpMethod.POST,
            "/api/v1/scheduled-exports",
            scheduledBody),
        Arguments.of(
            "列出定时导出（export:scheduled）",
            HttpMethod.GET,
            "/api/v1/scheduled-exports?userId={self}",
            null));
  }

  @ParameterizedTest(name = "无权限角色被拒：{0}")
  @MethodSource("guardedEndpoints")
  @DisplayName("缺少权限码的角色调用受保护端点必须 403（FR-G14）")
  void roleWithoutPermissionMustBeDenied(
      String label, HttpMethod method, String pathTemplate, String body) throws Exception {
    // 改造前：三个控制器均无权限注解 → 端点直接执行，本断言红。
    Actor actor = createActorWithRole("gate_deny_", "IT_GATE_DENIED", List.of("export:create"));

    int status = call(actor.token(), method, resolve(pathTemplate, actor), body);
    if (status != 403) {
      throw new AssertionError(
          "权限门禁未生效："
              + label
              + " 对无权限角色返回 "
              + status
              + "，期望 403（"
              + method
              + " "
              + pathTemplate
              + "）");
    }
  }

  @ParameterizedTest(name = "有权限角色不被拒：{0}")
  @MethodSource("guardedEndpoints")
  @DisplayName("持有所需权限码的角色不被权限门禁拒绝（FR-G14 正对照：钉住权限码字符串本身）")
  void roleWithPermissionMustNotBeDenied(
      String label, HttpMethod method, String pathTemplate, String body) throws Exception {
    // 正对照的意义：反向断言（403）在"注解写错了权限码"时同样会通过，只有让持码角色走一遍，
    // 才能钉住注解里写的确实是那个码。故此处只断言"不是 403"，允许端点因其他原因失败。
    Actor actor = createActorWithRole("gate_allow_", "IT_GATE_ALLOWED", ALL_GUARDED_PERMISSIONS);

    int status = call(actor.token(), method, resolve(pathTemplate, actor), body);
    if (status == 403) {
      throw new AssertionError(
          "权限门禁误拒：" + label + " 对持码角色返回 403，说明注解所声明的权限码与实际不符（" + method + " " + pathTemplate + "）");
    }
  }

  /** FR-G16 用例的当事人所用角色：两个当事人都须能建任务，否则夹具本身会被 T040 加上的门禁挡住。 */
  private static final String SCHEDULED_EXPORT_ROLE = "IT_SCHED_EXPORT";

  private static final List<String> ALL_GUARDED_PERMISSIONS =
      List.of(
          "export:compliance",
          "retention:create",
          "retention:update",
          "retention:delete",
          "retention:execute",
          "export:scheduled");

  /**
   * 刻意<b>不</b>声明权限码、必须对任何已认证用户可用的端点。
   *
   * <p><b>为什么这两组要写成用例，而不只是写成代码注释</b>：FR-G14 对数据保留策略的三个读端点、FR-G15 对整个搜索控制器 都做出了"不加注解"的显式决策，理由是既有 97
   * 条权限码中不存在对应项、擅自添加会使功能对所有用户失效。 这两个决策的价值全在"以后不要有人好心补上注解"——而注释拦不住后手，用例可以。
   */
  static Stream<Arguments> endpointsWithoutPermissionCode() {
    return Stream.of(
        // FR-G14 / T039：三个读端点在既有权限码中无对应项，只依赖全局认证
        Arguments.of("数据保留策略列表（读端点）", HttpMethod.GET, "/api/v1/data-retention/policies", null),
        Arguments.of(
            "数据保留策略详情（读端点）", HttpMethod.GET, "/api/v1/data-retention/policies/{policyId}", null),
        Arguments.of(
            "数据保留策略执行记录（读端点）",
            HttpMethod.GET,
            "/api/v1/data-retention/policies/{policyId}/executions",
            null),
        // FR-G15 / T041：搜索的数据范围在服务层按当前用户过滤，不存在 search:* 权限码
        Arguments.of("全局搜索（FR-G15 显式决策）", HttpMethod.GET, "/api/v1/search?keyword=test", null),
        Arguments.of(
            "全局搜索结果页（FR-G15 显式决策）", HttpMethod.GET, "/api/v1/search/full?keyword=test", null));
  }

  @ParameterizedTest(name = "无权限码端点仍可用：{0}")
  @MethodSource("endpointsWithoutPermissionCode")
  @DisplayName("刻意不声明权限码的端点不得对已认证用户返回 403（FR-G14 读端点决策、FR-G15）")
  void endpointWithoutPermissionCodeMustStayAccessible(
      String label, HttpMethod method, String pathTemplate, String body) throws Exception {
    // 该角色只持 export:create，不含任何保留／搜索相关权限码——若这些端点被补上注解，本用例即转红。
    Actor actor = createActorWithRole("no_code_", "IT_NO_CODE", List.of("export:create"));

    int status = call(actor.token(), method, resolve(pathTemplate, actor), body);
    if (status == 403) {
      throw new AssertionError(
          "端点了被误加权限门禁："
              + label
              + " 对无相关权限码的用户返回 403（"
              + method
              + " "
              + pathTemplate
              + "）。按 FR-G14／FR-G15 的显式决策，此处不应声明权限码。");
    }
  }

  // ===== 辅助 =====

  private record Actor(long id, String token) {}

  /** 把路径模板里的 {@code {policyId}}／{@code {self}} 换成当前用例的真实标识。 */
  private String resolve(String pathTemplate, Actor actor) throws Exception {
    String path = pathTemplate.replace("{self}", String.valueOf(actor.id()));
    if (path.contains("{policyId}")) {
      path = path.replace("{policyId}", String.valueOf(createPolicyAsAdmin()));
    }
    return path;
  }

  private String listExports(long userId) {
    return "/api/v1/scheduled-exports?userId=" + userId;
  }

  /** 发起一次带凭据的请求，返回状态码。{@code body} 为 {@code null} 时不带请求体。 */
  private int call(String token, HttpMethod method, String path, String body) throws Exception {
    MockHttpServletRequestBuilder req =
        request(method, path).header("Authorization", bearer(token));
    if (body != null) {
      req = req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(req).andReturn().getResponse().getStatus();
  }

  /** 同 {@link #call}，但返回响应字节（JSON 解析须走字节流，见 OpenPlatformIT 的说明与 FR 中的假绿陷阱）。 */
  private byte[] callRaw(String token, HttpMethod method, String path, String body)
      throws Exception {
    MockHttpServletRequestBuilder req =
        request(method, path).header("Authorization", bearer(token));
    if (body != null) {
      req = req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(req).andReturn().getResponse().getContentAsByteArray();
  }

  private JsonNode jsonOf(byte[] body) throws Exception {
    return objectMapper.readTree(body);
  }

  private int createWebhook(String callbackUrl) throws Exception {
    String body = "{\"eventType\": \"LEAD_CREATED\", \"callbackUrl\": \"" + callbackUrl + "\"}";
    return call(loginAndGetToken(), HttpMethod.POST, "/api/v1/platform/webhooks", body);
  }

  private int createChannel(String webhookUrl) throws Exception {
    String body =
        "{\"channelType\": \"CUSTOM\", \"name\": \"SSRF 测试通道-"
            + System.nanoTime()
            + "\", \"webhookUrl\": \""
            + webhookUrl
            + "\"}";
    return call(loginAndGetToken(), HttpMethod.POST, "/api/v1/integration-channels", body);
  }

  /**
   * 断言被拒，且拒绝必须是<b>客户端错误（4xx）</b>。
   *
   * <p>只断言"非 2xx"会把服务端异常（5xx）也算作拒绝——那意味着地址校验抛了未映射的异常，调用方拿不到可理解的拒绝理由。 本项目对"请求不合法"的统一形制是 {@code
   * BusinessException} + 全局异常处理器（映射为 4xx），故此处按 4xx 断言。
   */
  private void assertClientRejected(int status, String what) {
    if (status / 100 != 4) {
      throw new AssertionError(what + " 未被拒绝：状态 " + status + "，期望 4xx（校验器须以请求不合法的方式拒绝，不得以 5xx 抛出）");
    }
  }

  private void assertCreated(int status, String what) {
    if (status != 201) {
      throw new AssertionError(what + "：状态 " + status + "，期望 201（改造前应为 201，此为回归护栏）");
    }
  }

  private long createPolicyAsAdmin() throws Exception {
    String body =
        "{\"entityType\": \"CUSTOMER\", \"retentionDays\": 30, \"actionType\": \"ARCHIVE\"}";
    byte[] resp =
        callRaw(loginAndGetToken(), HttpMethod.POST, "/api/v1/data-retention/policies", body);
    return objectMapper.readTree(resp).path("id").asLong();
  }

  private long createScheduledExport(Actor owner) throws Exception {
    String body =
        "{\"entityType\": \"CUSTOMER\", \"exportFormat\": \"CSV\", \"cronExpression\": \"0 0 1 * *\"}";
    byte[] resp = callRaw(owner.token(), HttpMethod.POST, "/api/v1/scheduled-exports", body);
    return objectMapper.readTree(resp).path("id").asLong();
  }

  /**
   * 建角色 + 建用户 + 登录。
   *
   * <p>{@code permissions} 为 {@code null} 时使用内建角色（其权限矩阵来自建库脚本，不走角色创建接口）。
   */
  private Actor createActorWithRole(
      String usernamePrefix, String roleCode, List<String> permissions) throws Exception {
    if (permissions != null) {
      ensureRole(roleCode, permissions);
    }
    return createUserWithRole(usernamePrefix, roleCode);
  }

  /** 建一个持 {@code export:scheduled} 的角色（FR-G16 的两个当事人都需要建任务的权限）。 */
  private void ensureScheduledExportRole() throws Exception {
    ensureRole(SCHEDULED_EXPORT_ROLE, List.of("export:scheduled"));
  }

  /**
   * 建角色并授予指定权限码。
   *
   * <p><b>忽略返回状态是有意的</b>：同一用例内可能需要多次确保同一角色存在，第二次必然因编码重复而失败；
   * 该失败不影响后续（用户仍引用同一角色编码）。此处不引入"先查再建"的复杂度，因为角色是否真的建成功， 会由紧随其后的"持码角色不被拒"正对照用例暴露出来。
   */
  private void ensureRole(String roleCode, List<String> permissions) throws Exception {
    StringBuilder perms = new StringBuilder();
    for (String p : permissions) {
      perms.append(perms.length() == 0 ? "" : ",").append('"').append(p).append('"');
    }
    String roleBody =
        "{\"code\": \""
            + roleCode
            + "\", \"name\": \"安全测试角色 "
            + roleCode
            + "\", \"menus\": [\"customers\"], \"permissions\": ["
            + perms
            + "]}";
    call(loginAndGetToken(), HttpMethod.POST, "/api/v1/roles", roleBody);
  }

  private Actor createUserWithRole(String usernamePrefix, String roleCode) throws Exception {
    String username = usernamePrefix + System.nanoTime();
    String userBody =
        "{\"username\": \""
            + username
            + "\", \"displayName\": \"安全测试用户\", \"role\": \""
            + roleCode
            + "\", \"password\": \"pass1234\"}";
    byte[] resp = callRaw(loginAndGetToken(), HttpMethod.POST, "/api/v1/users", userBody);
    long id = objectMapper.readTree(resp).path("data").path("id").asLong();
    return new Actor(id, loginAndGetToken(username, "pass1234"));
  }
}
