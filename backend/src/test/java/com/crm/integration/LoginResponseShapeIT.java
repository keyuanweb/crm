package com.crm.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import com.crm.support.InMemoryRedisTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-M14 的**HTTP 层**守卫（082-two-factor-auth）：未启用 2FA 的账号，登录/刷新的响应与 082 之前逐字节相同。
 *
 * <p><b>为什么不与 {@code AuthResponseSerializationTest} 重复</b>：那个类自己造 {@code ObjectMapper}， 只能证明 DTO
 * 形状对；它<b>看不见</b> {@code application.yml} 里 {@code spring.jackson.default-property-inclusion:
 * non_null} 这一条。本类跑真上下文、用 Spring <b>真正装配</b>的 {@code ObjectMapper}（即把 yml 那条配置真的绑上），断言 HTTP
 * 响应体上<b>可观测</b>的形状。 有人把那条配置改成 {@code always} 时，<b>只有本类会红</b>——两个类缺一不可。
 *
 * <p><b>断言为什么直接核「键集」与「原文里没有 mfa」</b>：FR-M14 的原话是"响应逐字节一致"，
 * 而"逐字节"在这里的可判定近似就是：<b>顶层键的集合与顺序都不变</b>，且<b>响应原文里不出现任何本次新增的标识</b>。 不核整个字面量的原因是 {@code user} 子树随
 * admin 角色的菜单/权限字典变化而变化（那不是本类的主题）， 但键结构是稳定的。
 */
class LoginResponseShapeIT extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;

  /**
   * 功能性 Redis 替身。**必须**：父类装的是裸 mock，{@code opsForValue().set} 是空操作， 于是 {@code /auth/refresh}
   * 的成功分支（要校验"Redis 里登记过的 refresh token"）在父类的默认值下 永远走不通——这正是本用例需要它的原因。
   */
  private final InMemoryRedisTestSupport redis = new InMemoryRedisTestSupport();

  /** JUnit 5 保证父类 {@code @BeforeEach}（{@code stubRedis()}）先跑，故这里的复写生效。 */
  @BeforeEach
  void installFunctionalRedis() {
    redis.clear();
    redis.install(redisTemplate);
  }

  /** 082 之前登录响应的 {@code data} 键，**按声明顺序**。 */
  private static final List<String> PRE_082_DATA_KEYS =
      List.of("accessToken", "refreshToken", "user");

  @Test
  @DisplayName("非 2FA 账号登录：data 键集与顺序与 082 之前完全一致，原文无任何 mfa 痕迹")
  void normalLoginResponseShapeIsUnchanged() throws Exception {
    // 前提：确认走的是**非 2FA** 那条路径。若哪天种子账号被启用 2FA，本用例应当在这里失败，
    // 而不是"因为响应恰好形状相同"而继续绿——那会让它守着一个已经不再成立的场景。
    User admin =
        userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, "admin"));
    assertNotNull(admin, "种子账号 admin 应当存在（AbstractIntegrationTest 会重放 DataInitializer）");
    assertFalse(
        Boolean.TRUE.equals(admin.getTwoFactorEnabled()), "admin 未启用 2FA 时本用例才有意义；已启用则它守着的是二次验证分支");

    String body = loginRawBody("admin", "admin123");

    JsonNode root = objectMapper.readTree(body);
    JsonNode data = root.get("data");

    // 用 assertAll 而非顺序断言：任何一处形状变化都**全部**报出来。
    // 顺序断言的实测代价见 falsification-evidence.md §B 破坏 B —— 当时信封那条先失败并中止，
    // data 那条**根本没跑到**，于是"data 对 always 是否敏感"只能靠推断。assertAll 让那类推断变成观测。
    assertAll(
        "登录响应形状（未启用 2FA 的账号）",
        () ->
            assertEquals(
                Set.of("success", "data"),
                fieldNames(root),
                "成功响应的顶层键应恰为 success/data —— 这里用**集合**而非顺序，"
                    + "因为 ApiResponse 是全站信封，其字段顺序不属 FR-M14 的主题（data 的顺序是）"),
        () ->
            assertEquals(
                PRE_082_DATA_KEYS,
                fieldNamesInOrder(data),
                "登录响应的 data 键集/顺序变了 —— 这就是 FR-M14 被破坏的形态。"
                    + "常见原因：新字段被赋了默认值或被写成 Boolean.FALSE，或 yml 的 non_null 被改成了 always"),
        () ->
            assertFalse(
                body.contains("mfa"), "非 2FA 登录响应里不得出现任何 mfa 标识；命中处：" + excerpt(body, "mfa")),
        () ->
            assertFalse(
                body.contains("expiresIn"),
                "expiresIn 只在 mfaRequired 分支出现；命中处：" + excerpt(body, "expiresIn")),
        () -> assertTrue(data.get("accessToken").asText().length() > 0, "accessToken 不应为空"));
  }

  @Test
  @DisplayName("refresh 与 login 共用 AuthResponse，形状同样未变")
  void refreshResponseShapeIsUnchanged() throws Exception {
    JsonNode loginData = objectMapper.readTree(loginRawBody("admin", "admin123")).get("data");
    String refreshToken = loginData.get("refreshToken").asText();

    // 前置断言：登录确实把 refresh token 登记进了 Redis。
    // 若这一步不成立，下面的 401 会因为"没登记"而不是"形状变了"——本用例就红得莫名其妙。
    assertTrue(
        redis.snapshot().keySet().stream().anyMatch(k -> k.startsWith("auth:refresh:")),
        "登录应当把 refresh token 登记进 Redis，实际键：" + redis.snapshot().keySet());

    MvcResult refreshed =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andReturn();

    String body = refreshed.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertEquals(
        PRE_082_DATA_KEYS,
        fieldNamesInOrder(objectMapper.readTree(body).get("data")),
        "refresh 也走 AuthResponse，它的形状同样必须在 082 前后一致");
    assertFalse(body.contains("mfa"), "refresh 响应里不得出现任何 mfa 标识，实际原文：" + body);
  }

  /** 登录并返回**原文**（不是反序列化后的对象——本次要核的就是原文形状）。 */
  private String loginRawBody(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    // 显式按 UTF-8 解码：MockHttpServletResponse 的 characterEncoding 默认是 ISO-8859-1，
    // 不带参数会把 UTF-8 的字节解成乱码（本用例要扫"原文里有没有 mfa"，必须扫真字节）。
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  /**
   * 命中处的一小段原文。
   *
   * <p>不把整个响应体塞进失败信息：真实登录响应含 admin 的 55 个菜单与 140+ 个权限码， 全文约 4KB，会把失败报告淹掉（实测过一次，见 {@code
   * falsification-evidence.md} §B 破坏 B）。
   */
  private static String excerpt(String body, String needle) {
    int at = body.indexOf(needle);
    if (at < 0) {
      return "（未命中）";
    }
    int from = Math.max(0, at - 40);
    return "…" + body.substring(from, Math.min(body.length(), at + needle.length() + 40)) + "…";
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new LinkedHashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static List<String> fieldNamesInOrder(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
