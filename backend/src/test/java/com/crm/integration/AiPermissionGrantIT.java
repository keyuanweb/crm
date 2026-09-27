package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.support.PermissionDictionaryTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code ai:generate} 的<b>零授予</b>是一条<b>记录在案的决定</b>，不是遗漏（104-ai-content-generation，FR-020）。
 *
 * <p><b>为什么要把一个"没有"写成用例</b>：本码按判据③ 裁为"一个角色都不授予"——{@code ai:generate} 覆盖的是
 * 一个此前不存在的能力，它挂在<b>既有页面</b>上（不是新菜单），因此既没有"改造前那道粗粒度门"可供继承（判据①）， 也没有菜单承诺可依据（判据②）。判据③
 * 的结论是"只接码、不补授"。同判例：{@code mail_account:manage} · {@code workflow:read} · {@code
 * integration:manage} · {@code open_platform:manage}。理由的逐条论证在 {@code RoleConstants} 里本码上方的注释。
 *
 * <p><b>但"零授予"与"没人知道"只差一件事：有没有一条可执行的痕迹。</b>没有它，本码在字典里（管理员能在角色页 勾选）、权限注解也在（{@code
 * RequirePermissionCatalogTest} 绿、{@code UnwiredPermissionCodeTest} 也绿 ——它已经被本端点引用了），于是"除 ADMIN
 * 外全部角色 403"这件事只会以一个现状的形式存在。谁哪天决定把 {@code ai:generate} 授给 SALES_MANAGER，本用例即转红，逼他把决定与记录一起改掉。
 *
 * <p><b>为什么另起一个类，而不是把码加进 {@code PermissionMatrixIT.ADMIN_ONLY_BY_DEFAULT}</b>：那个集合是 <b>FR-G14
 * 那一批</b>的决定（六个码，各有各的"改造前有门/有菜单"的论据），把它撑成七个，就等于把 104 的判据 挂在一条不属于它的 FR 名下——日后读那段 javadoc 的人会以为本码也有
 * FR-G14 的那套理由。判例照抄， <b>FR 归属不合并</b>。
 *
 * <p><b>这里只有数据面的一半</b>：{@code ADMIN} 在 {@code PermissionAspect} 里恒放行 （{@code
 * PermissionAspect.java:43-44}），故零授予的实际后果是"ADMIN 照旧、其余角色一律 403"。 行为面的那一半（预置角色真打 {@code POST
 * /api/v1/ai/email-draft} → 403）归 C4 的集成用例，<b>此处刻意不写</b>： C3
 * 是本项的"显式红窗"（新代码暂无对应用例），把一条行为断言塞进来会让那个窗口的形状变得含混。
 *
 * <p>✅ <b>2026-09-27（C4 批）该欠账已结清</b>：上面那句"归 C4 的集成用例、<b>此处刻意不写</b>"由本类的 {@link
 * #aPresetRoleWithoutTheCodeIsRefusedByTheAspect()} 兑现。原文<b>保留在上面不改写</b>——它是 C3 当时的相位决定
 * （那时新代码暂无对应用例，红窗是刻意的），此处只登记它已经兑现，不做追认式改写。
 */
class AiPermissionGrantIT extends AbstractIntegrationTest {

  /** 本项唯一的权限码。 */
  private static final String AI_GENERATE = "ai:generate";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("104 FR-020：ai:generate 预置矩阵对任何角色零授予（判据③ 的可执行痕迹）")
  void aiGenerateIsGrantedToNoPresetRole() {
    // 正对照：先证明这条查询真查得出授予——联表或列名写坏时，下面那个"空"会一起假绿
    // （照 PermissionMatrixIT 的做法）。
    assertThat(rolesHolding("customer:claim"))
        .as("正对照落空：查不出任何角色持有 customer:claim，说明查询本身写坏了")
        .isNotEmpty();

    // 前提：码必须在字典里。若它不在字典里，"零授予"就是句废话——字典里没有的码本来就授不出去，
    // 而这条用例会以一个恒真的形态绿着。
    assertThat(PermissionDictionaryTestSupport.codes())
        .as(
            "ai:generate 不在 PERMISSION_DEFS 里：那样「零授予」恒真，本用例不再看着任何东西"
                + "（角色页也勾不出它 ⇒ 零授予会从「决定」退化成「做不到」）")
        .contains(AI_GENERATE);

    assertThat(rolesHolding(AI_GENERATE))
        .as(
            "预置角色持有 %s。若这是有意的，请同时改掉本用例、RoleConstants 上方的注释与 spec.md FR-020 的"
                + "「默认后果」记录：零授予是一份**记录在案的决定**，不是一份没人知道的现状",
            AI_GENERATE)
        .isEmpty();
  }

  /**
   * 行为面：<b>预置角色真打那个端点</b>。
   *
   * <p>上半条是"零授予"的后果，下半条是它的<b>正对照</b>——没有正对照的话，"拿到 403"可能只是因为那条令牌整个坏掉了
   * （角色名拼错、用户没建上、菜单/权限面空着），而不是因为这个码没授出去。
   *
   * <p>⚠️ <b>403 的 {@code error.code} 是 {@code PERMISSION_DENIED}，不是 {@code
   * FORBIDDEN}</b>：本仓有<b>两个</b> 403 出口——权限切面（{@code PermissionAspect} → {@code
   * PERMISSION_DENIED}）与数据范围判定（服务层 → {@code FORBIDDEN}）。断言写成 {@code FORBIDDEN}
   * 会把这道门的身份搞错，而两者在响应体上确实可分辨。
   *
   * <p>两个请求体都打一遍（合法的与必然非法的）：权限切面在<b>方法进入之前</b>，所以请求体不该改变结论。这条把"门在外层" 写成可执行的事实，而不是靠读 {@code
   * PermissionAspect} 的注解位置推断。
   */
  @Test
  @DisplayName("104 FR-020 行为面：预置角色 SALES 真打生成端点 ⇒ 403 PERMISSION_DENIED（与请求体无关）；同令牌建客户 ⇒ 201")
  void aPresetRoleWithoutTheCodeIsRefusedByTheAspect() throws Exception {
    String salesToken = createSalesUser("aigen");
    String adminToken = loginAndGetToken();

    // 合法请求体：先由 ADMIN 建一个客户，好让"被拒"这件事不是"请求体本身就不成立"。
    // 用响应里的 id 而不是写死 1：写死会让本用例依赖主键编号，而那件事与它要问的东西无关。
    String createdBody = createCustomerAsAdmin(adminToken);
    long customerId = objectMapper.readTree(createdBody).path("data").path("id").asLong();
    assertThat(customerId).as("前提落空：客户没建出来，下面那条 403 就分不清是权限还是请求体的问题").isPositive();

    for (String body : new String[] {"{\"customerId\":" + customerId + "}", "{}"}) {
      mockMvc
          .perform(
              post("/api/v1/ai/email-draft")
                  .header("Authorization", bearer(salesToken))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    // 正对照一：同一条令牌换成一个它持有的码 ⇒ 放行。证明上面那条 403 讲的是"这个码没授出去"，
    // 而不是"这条令牌什么都做不了"。
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"SALES 自建客户\",\"company\":\"SALES 公司\",\"phone\":\"13900001111\""
                        + ",\"email\":\"sales@example.com\"}"))
        .andExpect(status().isCreated());

    // 正对照二：同一个请求体换成 ADMIN ⇒ 不被切面拒（本上下文是出厂档，配置门回 409，那是另一条判据，
    // 见 AiContentUnconfiguredIT）。它证明端点存在、请求体可被受理，403 只能来自"这个码没授出去"。
    mockMvc
        .perform(
            post("/api/v1/ai/email-draft")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":1}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AI_NOT_CONFIGURED"));
  }

  /** 由 ADMIN 建一个客户，返回响应体（调用方从里面取 id）。 */
  private String createCustomerAsAdmin(String adminToken) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"零授予客户\",\"company\":\"零授予公司\",\"phone\":\"13900002222\""
                        + ",\"email\":\"grant@example.com\"}"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** 建一个预置角色 SALES 的用户并真登录（照 {@code CustomerAtRiskIT.createSalesUser} 的做法）。 */
  private String createSalesUser(String suffix) throws Exception {
    String adminToken = loginAndGetToken();
    String username = "aigen" + suffix;
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"username\": \"%s\", \"password\": \"Passw0rd!\", \"displayName\": \"零授予%s\""
                            + ", \"role\": \"SALES\"}",
                        username, suffix)))
        .andExpect(status().isCreated());
    return loginAndGetToken(username, "Passw0rd!");
  }

  /** 哪些角色持有该码——用角色 code 而非 id，失败信息才读得懂。 */
  private List<String> rolesHolding(String permissionCode) {
    return jdbcTemplate.queryForList(
        "SELECT r.code FROM role_permission rp JOIN role r ON r.id = rp.role_id"
            + " WHERE rp.permission_code = ? ORDER BY r.code",
        String.class,
        permissionCode);
  }
}
