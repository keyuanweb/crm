package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.crm.AbstractIntegrationTest;
import com.crm.config.AiClientFactory;
import com.crm.support.InMemoryRedisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * I1：<b>出厂档</b>（{@code crm.ai.enabled=false}）下生成端点的行为。
 *
 * <p><b>为什么是独立一类，而不是 {@code AiContentIT} 里的一条</b>：本仓 {@code application.yml} 的出厂值是 {@code
 * enabled=false}（零出站、零成本），而 I2–I6 恰恰需要把它打开才跑得到出网点。{@code AiStatus} 是 <b>单例</b>，同一个 Spring
 * 上下文里只能有一种取值：把两档塞进一个类，就成了"谁跑在后面谁说了算"。 ⇒ 分成两个类 = 两个上下文 = 两档配置各自都被真的跑到。（T033 的原文写的是"一个 {@code
 * AiContentIT}，I1–I6"，此处<b>刻意偏离</b>， 理由在此。）
 *
 * <p><b>本类不覆盖 {@code @TestPropertySource}</b>：它就吃出厂档。这是本类唯一的价值——断言的是"默认部署"下客户会看到什么，
 * 而不是"某个被测试改过的配置下"会看到什么。
 *
 * <p><b>为什么用管理员令牌</b>：本类问的是"配置门"，不是"权限门"。预置角色都<b>没有</b> {@code ai:generate} （那条判据与它的行为面在 {@code
 * AiPermissionGrantIT} 里），用它们打这个端点会拿到 403， 于是 409 这条断言永远跑不到——而那正是 "整批接线错误也能全仓绿"的老路（管理员在切面上直通）。
 * 故本类只借管理员的"通行"来单独观察配置门：两件事分开，各自有各自的类。
 */
class AiContentUnconfiguredIT extends AbstractIntegrationTest {

  private static final String ENDPOINT = "/api/v1/ai/email-draft";

  @Autowired private JdbcTemplate jdbcTemplate;

  /** 出站的唯一注入点：本类要断"一个字节都没出去"，故它是被观察的对象，不是被使用的对象。 */
  @MockBean private AiClientFactory clientFactory;

  private InMemoryRedisTestSupport redis;

  private BuiltinFieldPermissionFixture fixture;

  @BeforeEach
  void setUp() throws Exception {
    // 借这个夹具的 HTTP 管道（含本 C4 批新增的响应头捕获），**不**调 ensureRole：本类不需要那个角色。
    fixture = new BuiltinFieldPermissionFixture(mockMvc, objectMapper, loginAndGetToken());

    // 功能型替身而不是父类的裸 mock：本类要断"没写任何记账键"，而在裸 mock 上"没写"与"写了但没人看得见"
    // 是同一件事。装着替身，"零键"才是真读数。
    redis = new InMemoryRedisTestSupport();
    redis.install(redisTemplate);
  }

  @Test
  @DisplayName("I1 未配置 ⇒ 409 AI_NOT_CONFIGURED：先于入参校验、零出站、零审计、零记账")
  void unconfiguredRefusesBeforeAnythingElse() throws Exception {
    String admin = fixture.adminToken();
    long customerId = fixture.createCustomer(admin, "未配置档客户", "13900003333", "off@example.com");

    // 三种请求体，从"必然非法"到"完全合法"：若三者的响应都是 409，那就是同一道门在说话。
    for (String body :
        new String[] {
          "{}", // 连 id 都没有 ⇒ 若先跑校验，这里会是 400
          "{\"customerId\":" + customerId + ",\"tone\":\"SHOUTING\"}", // 非法枚举 ⇒ 同上
          "{\"customerId\":" + customerId + "}" // 合法请求 ⇒ 它也没有理由被别的什么拒掉
        }) {
      BuiltinFieldPermissionFixture.Res res = fixture.call(admin, HttpMethod.POST, ENDPOINT, body);

      assertThat(res.status()).as("请求体 %s 应当被配置门拦下", body).isEqualTo(409);
      assertThat(res.body().path("error").path("code").asText()).isEqualTo("AI_NOT_CONFIGURED");
    }

    // 这三条是"未配置 ⇒ 零出站、零记录"的**结构性**证据（FR-003 的假绿通道 1：只断响应码的话，
    // 一个"先发出去、失败了再回 409"的实现照样绿——那已经在替客户花钱了）。
    verifyNoInteractions(clientFactory);
    assertThat(generateAuditCount()).as("未配置时不该有任何生成审计").isZero();
    assertThat(redis.snapshot().keySet())
        .as("连记账都不该发生：预算键的存在被当作「今天真的花过钱」的证据，一次没花的调用留下键会让这条推断失真")
        .noneMatch(key -> key.startsWith("ai:gen:"));
  }

  private long generateAuditCount() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action = 'GENERATE' AND entity_type = 'CUSTOMER'",
            Long.class);
    return count == null ? 0L : count;
  }
}
