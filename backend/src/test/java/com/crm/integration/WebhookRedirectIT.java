package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

/**
 * 出站重定向的逐跳校验（FR-G13 的「覆盖重定向跳转」那一半，T037）。
 *
 * <p><b>为什么单开一个类</b>：本类必须把 {@code 127.0.0.1} 列入出站白名单——测试要真的访问本机 HTTP 服务， 而默认策略是全拒；而 {@link
 * SecurityHardeningIT} 恰恰断言 {@code 127.0.0.1} <b>必须被拒</b>。两者对白名单的 要求相反，共用一个类级
 * {@code @TestPropertySource} 不可能同时成立，故拆开。
 *
 * <p><b>为什么这类测试必须存在</b>：创建订阅时校验的是**落库的那个地址**，重定向的落点由对端在响应里临时给出、 创建时并不存在。只要客户端自动跟随 3xx，一条 {@code 302
 * Location: http://169.254.169.254/…} 就能让服务端 去请求一个从未被校验的地址——创建时的校验因此形同虚设。故 FR-G13
 * 的"覆盖重定向"由两半共同实现，两半分别由本类的 两个用例证明：{@link #clientMustNotAutoFollowRedirects} 证明客户端不再自动跟随（配置那一半），
 * {@link #legitimateRedirectIsFollowedHopByHop} 证明跟随由我们自己的逐跳校验驱动（合成那一半）。 任何一半被改回原样，对应的用例即转红。
 *
 * <p><b>T075 追加（投递时对当前地址的再校验）</b>：创建时的校验管的是"创建当时的那个地址"，而两条入口都不经过它—— 修复前已落库的行、以及集成通道直推（{@code
 * WebhookService.publishToUrl}，不经订阅表）。现在 {@code WebhookDeliverer} 在投递前对<b>当前</b>地址再校验一次，两个入口各有一个用例：
 * {@link #legacySubscriptionRowWithDeniedTargetIsRejectedAtDeliveryTime}（订阅表里的历史行）与 {@link
 * #publishToUrlTargetIsRejectedAtDeliveryTime}（内部通道直推）。
 *
 * <p><b>反向实验（已执行）说明了这两条断言各自的层次</b>：把投递前的校验整段移除后，两个用例都转红——但红在 10 秒轮询超时上（修复前投递记录要等三次退避重试跑完、约 36
 * 秒才落库），而不是红在拒绝理由上。可见 "记录里出现校验器的措辞"与"没有进入重试"这两条断言<b>并不用于区分有没有修</b>，而是用于钉住修法的形状：
 * 把校验挪进重试循环（"先试一次，失败再判"）这种实现同样记 FAILED、同样带校验器的措辞， 只有 {@code retryCount == 0} 与"没有任何 HTTP
 * 状态"能把它与正解区分开。
 *
 * <p><b>刻意的覆盖边界</b>：「重定向落点是被拒地址」这一组合没有端到端用例。原因是投递器对失败会退避重试三次 （1s／5s／30s，共约 36
 * 秒），且投递记录在重试全部结束后才落库——为一个断言付 36 秒的套件时长并不划算。该组合的 两个事实分别被更近的位置覆盖：落点判定由 {@code
 * OutboundUrlValidatorTest.redirectsAreValidatedPerHop} 覆盖， 而"每一跳都走该判定"由本类第二个用例（同样的循环、同样的 {@code
 * resolveRedirect} 调用，只是判定通过）覆盖。（T075 的拒绝是<b>首跳前</b>判死的，不进重试循环，故没有这一时长问题。）
 */
@TestPropertySource(properties = {"crm.outbound.allowed-hosts=127.0.0.1"})
class WebhookRedirectIT extends AbstractIntegrationTest {

  @Autowired private RestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private WebhookService webhookService;

  private HttpServer server;
  private int port;
  private final AtomicInteger hookHits = new AtomicInteger();
  private final AtomicInteger finalHits = new AtomicInteger();

  /**
   * 起一个真实的回环 HTTP 服务：{@code /hook} 一律回 302，落点由各用例指定。
   *
   * <p>用真实的 {@link HttpServer} 而非 mock：本类要验证的正是"HTTP 客户端是否自己又发了一次请求"， 这只能在真实的 socket 往返上观察——mock
   * 掉的客户端无法回答这个问题。
   */
  @BeforeEach
  void startLoopbackServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();
    server.start();
  }

  @AfterEach
  void stopLoopbackServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  /** 注册一个 302 端点与一个 200 端点；计数器在每个用例开始时归零。 */
  private void redirectTo(String location) {
    hookHits.set(0);
    finalHits.set(0);
    server.createContext(
        "/hook",
        exchange -> {
          hookHits.incrementAndGet();
          exchange.getResponseHeaders().add("Location", location);
          exchange.sendResponseHeaders(302, -1); // -1 = 无响应体
          exchange.close();
        });
    server.createContext(
        "/final",
        exchange -> {
          finalHits.incrementAndGet();
          respond(exchange, 200, "ok");
        });
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  // ===== 配置那一半：客户端不自动跟随 =====

  /**
   * 客户端必须把 3xx 原样交回调用方，而不是自己去请求落点。
   *
   * <p><b>为什么用 GET 而不是 POST</b>——这是一个曾把本用例变成假绿的坑，写下来免得后人踩回去： Spring 的 {@code
   * SimpleClientHttpRequestFactory.prepareConnection} 自身对<b>非 GET</b> 方法设了 {@code
   * setInstanceFollowRedirects(false)}，对 <b>GET</b> 才设 true。故用 POST 写这个用例时，无论 {@code
   * RestTemplateConfig} 有没有关掉自动跟随，断言都会通过（实测印证：把那一行改回跟随，POST 版用例照样绿）。 而 JDK 的 {@code
   * HttpURLConnection} 本身对 POST 的 302 是<b>会</b>跟随的（实测：第二跳被改写成 GET 并返回 200）， 两者叠加意味着 POST
   * 路径的安全来自框架的实现细节、并非本类的配置。GET 才真正检验本类自己写下的配置—— 实测同样印证：把 {@code setInstanceFollowRedirects(false)}
   * 去掉，本用例即转红。
   */
  @Test
  @DisplayName("RestTemplate 不自动跟随 3xx：落点地址绝不会被默认客户端自行请求（FR-G13）")
  void clientMustNotAutoFollowRedirects() {
    // 落点指向一个不在白名单内的地址。跟随一旦发生，这里去连 169.254.169.254 会抛 SocketException，
    // 调用方拿到的就不是 302 —— 本断言因此是「有没有跟随」的可观测判据。
    redirectTo("http://169.254.169.254/latest/meta-data/");

    ResponseEntity<String> resp =
        restTemplate.getForEntity(URI.create("http://127.0.0.1:" + port + "/hook"), String.class);

    assertThat(resp.getStatusCode().value())
        .as("出站客户端应把 3xx 原样返回给调用方，由调用方逐跳校验后再决定是否继续")
        .isEqualTo(302);
  }

  // ===== 合成那一半：合法重定向仍被逐跳跟随 =====

  @Test
  @DisplayName("合法重定向被逐跳跟随并投递成功（FR-G13：不能因关闭自动跟随而打断合法集成）")
  void legitimateRedirectIsFollowedHopByHop() throws Exception {
    redirectTo("http://127.0.0.1:" + port + "/final");
    String token = loginAndGetToken();

    String callbackUrl = "http://127.0.0.1:" + port + "/hook";
    // 订阅创建本身即是一次 FR-G13 正对照：白名单内的回环地址必须被放行（默认策略是全拒）
    byte[] created =
        mockMvc
            .perform(
                post("/api/v1/platform/webhooks")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"eventType\": \"LEAD_CREATED\", \"callbackUrl\": \""
                            + callbackUrl
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    long subscriptionId = objectMapper.readTree(created).path("data").path("id").asLong();

    // 触发一次真实事件（创建线索 → LEAD_CREATED），投递在异步线程上进行
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "重定向线索%s", "company": "重定向公司", "source": "WEBSITE", "score": 70}
                    """
                        .formatted(System.nanoTime())))
        .andExpect(status().isOk());

    JsonNodeHolder delivery = awaitDelivery(token, subscriptionId);
    assertThat(delivery.status).as("回调落点返回 200，本次投递应记为成功").isEqualTo("SUCCESS");
    assertThat(delivery.httpStatus).as("成功码应来自第二跳（重定向落点）的 200").isEqualTo(200);
    assertThat(finalHits.get()).as("重定向落点应恰好收到一次请求——证明跟随由我们自己发起，且没有多跳重复").isEqualTo(1);
  }

  // ===== T075：投递时对当前地址的再校验 =====

  /** 云元数据端点：既不在白名单内，又命中链路本地网段——SSRF 最典型的目标。 */
  private static final String METADATA_URL = "http://169.254.169.254/latest/meta-data/";

  /** 校验器拒绝内网目标时的措辞。断言落在它上面，才与"真去连了、连接失败"区分得开。 */
  private static final String DENIED_REASON = "不允许出站";

  @Test
  @DisplayName("修复前落库的订阅行：投递前被拒，请求根本发不出去（FR-G13 / T075）")
  void legacySubscriptionRowWithDeniedTargetIsRejectedAtDeliveryTime() throws Exception {
    // 直接写库，绕过 WebhookService.create 的落库前校验——这正是"改造前已落库的行"的形态
    long legacyId = 987654321L;
    jdbc.update(
        "INSERT INTO webhook_subscription (id, event_type, callback_url, secret, enabled, created_by)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        legacyId,
        "LEAD_CREATED",
        METADATA_URL,
        "legacy-secret",
        1,
        1L);

    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "历史行线索%s", "company": "历史行公司", "source": "WEBSITE", "score": 70}
                    """
                        .formatted(System.nanoTime())))
        .andExpect(status().isOk());

    assertRejectedBeforeAnyRequest(awaitDelivery(token, legacyId));
  }

  @Test
  @DisplayName("集成通道直推（不经订阅表）：投递前同样被拒（FR-G13 / T075）")
  void publishToUrlTargetIsRejectedAtDeliveryTime() throws Exception {
    long directId = 987654322L;
    String token = loginAndGetToken();

    // publishToUrl 由 IntegrationChannelService 调用，临时订阅的 callbackUrl 直接取参数、不经 create
    webhookService.publishToUrl(
        directId, "LEAD_CREATED", "LEAD", 1L, java.util.Map.of(), METADATA_URL, "probe-channel");

    assertRejectedBeforeAnyRequest(awaitDelivery(token, directId));
  }

  /**
   * 断言投递记录里的三条证据：拒绝理由是**校验器**给出的、没有进入重试、没有任何 HTTP 状态。
   *
   * <p>后两条是必需的：修复前也记 FAILED（真去连了内网地址，连不上），只有"理由"与"重试次数" 能把两种情况分开——被拒的地址若进了重试循环，就要白等 36 秒才落记录。
   */
  private static void assertRejectedBeforeAnyRequest(JsonNodeHolder delivery) {
    assertThat(delivery.status).as("被拒的地址不能记为成功").isEqualTo("FAILED");
    assertThat(delivery.error).as("拒绝理由必须来自出站校验器，而不是连接失败的 socket 异常").contains(DENIED_REASON);
    assertThat(delivery.retryCount).as("地址能否出站不随重试改变，故应一次判死、不进重试循环").isZero();
    assertThat(delivery.httpStatus).as("被拒的地址不应产生任何 HTTP 状态码").isNull();
  }

  // ===== 辅助 =====

  private record JsonNodeHolder(String status, Integer httpStatus, String error, int retryCount) {}

  /**
   * 取一个"可空"的字段。**必须同时判 MissingNode**：`application.yml` 配了 {@code
   * spring.jackson.default-property-inclusion: non_null}，于是 {@code null} 在响应里是整个键<b>缺席</b>， {@code
   * path()} 返回 MissingNode——而 {@code MissingNode.isNull()} 是 {@code false}。 只判 {@code isNull()}
   * 会把"没有值"读成"有值"（本条断言第一次跑就栽在这上面：把缺席读成了非空）。
   */
  private static Integer nullableInt(JsonNode item, String field) {
    var node = item.path(field);
    return node.isNull() || node.isMissingNode() ? null : node.asInt();
  }

  private static String nullableText(JsonNode item, String field) {
    var node = item.path(field);
    return node.isNull() || node.isMissingNode() ? null : node.asText();
  }

  /** 投递的**在途**状态（085 FR-V05 起，派发即落库，故"记录已出现"不再等于"已有结果"）。 */
  private static final String IN_FLIGHT = "PENDING";

  /**
   * 轮询投递记录直到它被**判定为终态**（异步投递，不能只 sleep 一次就断言）。
   *
   * <p><b>判据为什么从"记录已出现"改成"状态已是终态"</b>：085（FR-V05）之前投递记录要等重试循环结束才落库，于是"记录存在"
   * 恰好等价于"已有结果"，判存在是对的。改造后记录在**派发那一刻**就以 PENDING 落库——这正是缺陷③的修复（在飞窗口可见、进程
   * 中断也不丢）——于是"存在"不再蕴含"已判定"：继续判存在会当场读到 PENDING，把一次**成功**的投递读成失败。本方法随生产
   * 语义同步收窄，不是放宽断言：它仍然要求等到一个明确的终态，只是不再把"在途"误当"终态"。
   *
   * <p>超时后抛出的错误里带上记录数、最后一次状态与两跳的命中次数，便于区分"根本没投递""仍在重试（退避最长 36 秒）""落到别的地址去了"这三种情形。
   */
  private JsonNodeHolder awaitDelivery(String token, long subscriptionId) throws Exception {
    String lastStatus = "<无记录>";
    int lastCount = 0;
    for (int i = 0; i < 40; i++) {
      byte[] raw =
          mockMvc
              .perform(
                  get("/api/v1/platform/webhooks/{id}/deliveries", subscriptionId)
                      .header("Authorization", bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      var items = objectMapper.readTree(raw).path("data").path("items");
      if (items.size() > 0) {
        var item = items.path(0);
        lastCount = items.size();
        lastStatus = item.path("status").asText();
        if (!IN_FLIGHT.equals(lastStatus)) {
          return new JsonNodeHolder(
              lastStatus,
              nullableInt(item, "httpStatus"),
              nullableText(item, "error"),
              item.path("retryCount").asInt());
        }
      }
      Thread.sleep(250);
    }
    throw new AssertionError(
        "等待投递终态超时（10 秒）——最后一次状态="
            + lastStatus
            + "，记录数="
            + lastCount
            + "，回环服务收到 /hook 请求 "
            + hookHits.get()
            + " 次，/final "
            + finalHits.get()
            + " 次");
  }
}
