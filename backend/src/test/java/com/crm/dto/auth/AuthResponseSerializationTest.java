package com.crm.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * FR-M14 的**序列化形状**守卫（082-two-factor-auth）。
 *
 * <p>被守护的承诺：<b>未启用 2FA 的账号，登录响应与 082 之前逐字节相同</b>。本批给 {@link AuthResponse}
 * 加了三个字段，承诺靠的机制是"这三个字段在正常路径上恒为 {@code null}"＋"本仓开了 {@code
 * spring.jackson.default-property-inclusion: non_null}" ⇒ {@code null} 字段不出现。
 *
 * <p><b>本类覆盖什么、不覆盖什么（务必看清，否则会误判护栏范围）</b>：
 *
 * <ul>
 *   <li><b>覆盖</b>：DTO 自身的形状 —— 给新字段加 Lombok 默认值（{@code mfaRequired = false}）、 新增第 4
 *       个非空字段、字段声明顺序变化、把 {@code null} 换成 {@code Boolean.FALSE}。这些<b>全部</b>会让 下面的字面量断言转红。
 *   <li><b>不覆盖</b>：{@code application.yml} 里那条 {@code non_null} 本身。本类的 {@link ObjectMapper}
 *       是<b>自建</b>的（只镜像了那一条配置），有人把 yml 改成 {@code always} 时本类<b>照样是绿的</b>。 那条路径由 {@code
 *       LoginResponseShapeIT}（跑真上下文、用 Spring 真正装配的 ObjectMapper）守住。 ⇒ 两条缺一不可，<b>删掉任何一条，FR-M14
 *       就有一半失去护栏</b>。
 * </ul>
 *
 * <p><b>若本类因 {@link UserInfo} 的改动而失败</b>：这不是误报。user 子树是登录响应的一部分，
 * 改动它<b>就是</b>改动了登录响应。正确的应对是<b>有意识地</b>更新下面的字面量并在提交信息里说明， 而不是把断言放宽成"只核 3 个顶层键"。
 */
class AuthResponseSerializationTest {

  /**
   * 镜像 {@code spring.jackson.default-property-inclusion: non_null}。
   *
   * <p>用 {@link Jackson2ObjectMapperBuilder}（而非裸 {@code new ObjectMapper()}）是为了拿到与本仓一致的 其余默认值：关闭
   * {@code FAIL_ON_UNKNOWN_PROPERTIES}、关闭 {@code WRITE_DATES_AS_TIMESTAMPS}、 注册 classpath 上的
   * well-known 模块。这里不接 Spring 上下文 —— 那会让本类也变成集成测试， 而"DTO 形状"这件事不需要上下文。
   */
  private static final ObjectMapper MAPPER =
      Jackson2ObjectMapperBuilder.json()
          .serializationInclusion(JsonInclude.Include.NON_NULL)
          .build();

  @Test
  @DisplayName("正常登录响应序列化后与 082 之前逐字节相同：恰好三个键，且不含任何 mfa 痕迹")
  void normalLoginSerializesByteIdentically() throws Exception {
    AuthResponse response =
        new AuthResponse("access-abc", "refresh-xyz", new UserInfo(1L, "admin", "Admin", "ADMIN"));

    String json = MAPPER.writeValueAsString(response);

    assertEquals(
        "{\"accessToken\":\"access-abc\",\"refreshToken\":\"refresh-xyz\","
            + "\"user\":{\"id\":1,\"username\":\"admin\",\"displayName\":\"Admin\",\"role\":\"ADMIN\","
            + "\"menus\":[],\"permissions\":[]}}",
        json,
        "登录响应形状变了 —— 这就是 FR-M14 被破坏的形态。"
            + "常见原因：新字段被赋了默认值/被写成 Boolean.FALSE，或 yml 的 non_null 被改成了 always"
            + "（后者本类看不出来，见 LoginResponseShapeIT）");

    assertFalse(json.contains("mfa"), "正常路径上不得出现任何 mfa 字段");
    assertFalse(json.contains("expiresIn"), "expiresIn 只在 mfaRequired 分支出现");
    // 逐键再钉一遍，使失败信息直接指认是哪个键多余/缺失
    List<String> keys = topLevelKeys(json);
    assertEquals(3, keys.size(), "顶层必须恰好 3 个键，实际：" + keys);
    assertTrue(keys.containsAll(List.of("accessToken", "refreshToken", "user")), "实际：" + keys);
  }

  @Test
  @DisplayName("三个新字段在正常路径上确实是 null，而不是被悄悄赋了假值")
  void newFieldsAreNullOnNormalPath() {
    AuthResponse response =
        new AuthResponse("access-abc", "refresh-xyz", new UserInfo(1L, "admin", "Admin", "ADMIN"));

    assertNull(response.getMfaRequired(), "mfaRequired 必须是 null —— 写成 Boolean.FALSE 会多出一个键");
    assertNull(response.getMfaToken());
    assertNull(response.getExpiresIn());
  }

  @Test
  @DisplayName("mfaChallenge 分支恰好三个键：只回票据，不含任何令牌")
  void mfaChallengeCarriesNoTokens() throws Exception {
    AuthResponse challenge = AuthResponse.mfaChallenge("opaque-ticket-value", 300);

    assertEquals(
        "{\"mfaRequired\":true,\"mfaToken\":\"opaque-ticket-value\",\"expiresIn\":300}",
        MAPPER.writeValueAsString(challenge),
        "二次验证分支只应回票据；多出 accessToken/refreshToken 就是把半张令牌发出去了");

    // 结构性断言：这条路径**拿不到**令牌字段，无论 JSON 怎么变
    assertNull(challenge.getAccessToken());
    assertNull(challenge.getRefreshToken());
    assertNull(challenge.getUser(), "密码阶段不返回用户信息 —— 此时会话尚未成立");
  }

  /** 顶层键，**按声明顺序**。 */
  private static List<String> topLevelKeys(String json) throws Exception {
    List<String> names = new ArrayList<>();
    MAPPER.readTree(json).fieldNames().forEachRemaining(names::add);
    return names;
  }
}
