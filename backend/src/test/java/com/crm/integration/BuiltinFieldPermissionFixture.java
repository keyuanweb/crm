package com.crm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 102 内置字段权限用例的公共夹具：一个**非 ADMIN** 的专用角色、它的用户与登录令牌，以及 HTTP 调用/建实体的最小封装。
 *
 * <p><b>为什么必须是非 ADMIN 角色</b>：ADMIN 在两条链上都直通——{@code PermissionAspect} 直接放行、 出参收口点 {@code
 * FieldMaskingResponseBodyAdvice} 走快路径原样返回。整批接线就算全错，只要用例用管理员令牌就**全绿** （本仓已有这个先例，见 {@code
 * support/MenuReadPermissionTestSupport} 的来历）。故本夹具建一个只持必要权限码的 角色 {@link #ROLE}，掩码链只有在它身上才真的会跑。
 *
 * <p><b>为什么不改内建 SALES 角色</b>：种子里的 SALES 有 {@code customer:create/update} 与 {@code
 * opportunity:create/update}，但**没有** {@code opportunity:read} 也没有 {@code customer:export} （见 {@code
 * schema-h2.sql} 的 role_permission 播种）。补授种子角色会让「SALES 有什么权限」这件事 在测试库里与
 * 生产/文档不一致；新建一个角色反而更干净，且权限面在测试里是**显式可读**的。
 *
 * <p>角色经 {@code POST /api/v1/roles} 由 ADMIN 建、用户经 {@code POST /api/v1/users} 建、再真登录取令牌 —— 与 {@code
 * SecurityHardeningIT} 同一套做法（那里的 {@code ensureRole}/{@code createUserWithRole}）。
 */
class BuiltinFieldPermissionFixture {

  /** 专用角色编码。字段权限配置就按这个编码配，故用例里的 {@code roleCode} 一律是它。 */
  static final String ROLE = "IT_FLS_ROLE";

  /** 该角色需要的权限码：建/改客户与商机、读商机、导出客户、建定时导出。 */
  private static final List<String> PERMISSIONS =
      List.of(
          "customer:create",
          "customer:update",
          "opportunity:create",
          "opportunity:update",
          "opportunity:read",
          "customer:export",
          "export:scheduled");

  private final MockMvc mockMvc;
  private final ObjectMapper mapper;
  private final String adminToken;

  BuiltinFieldPermissionFixture(MockMvc mockMvc, ObjectMapper mapper, String adminToken) {
    this.mockMvc = mockMvc;
    this.mapper = mapper;
    this.adminToken = adminToken;
  }

  String adminToken() {
    return adminToken;
  }

  /**
   * 建角色并授码。
   *
   * <p>忽略返回码是**有意的**（照 {@code SecurityHardeningIT.ensureRole}）：同一用例内可能重复确保，
   * 第二次必然因编码重复而失败，不影响后续。角色是否真的建成功，会由「持码角色不被拒」的正对照暴露。
   */
  void ensureRole() throws Exception {
    String perms = PERMISSIONS.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
    call(
        adminToken,
        HttpMethod.POST,
        "/api/v1/roles",
        "{\"code\":\""
            + ROLE
            + "\",\"name\":\"FLS 内置字段测试角色\",\"menus\":[\"customers\"],\"permissions\":["
            + perms
            + "]}");
  }

  /** 建一个该角色的用户并真登录，返回 (userId, token)。 */
  Actor createUser(String prefix) throws Exception {
    String username = prefix + System.nanoTime();
    String body =
        "{\"username\":\""
            + username
            + "\",\"displayName\":\"FLS 测试用户\",\"role\":\""
            + ROLE
            + "\",\"password\":\"pass1234\"}";
    long id =
        call(adminToken, HttpMethod.POST, "/api/v1/users", body)
            .body()
            .path("data")
            .path("id")
            .asLong();
    String token =
        call(
                null,
                HttpMethod.POST,
                "/api/v1/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"pass1234\"}")
            .body()
            .path("data")
            .path("accessToken")
            .asText();
    return new Actor(id, token);
  }

  // ===== 配置面（一律用 ADMIN：配权限本身就是管理员动作） =====

  /** 配一条内置字段权限；返回响应（调用方按需断言 200/422）。 */
  Res configureBuiltin(String entityType, String fieldKey, String permission) throws Exception {
    return call(
        adminToken,
        HttpMethod.POST,
        "/api/v1/field-permissions",
        "{\"roleCode\":\""
            + ROLE
            + "\",\"entityType\":\""
            + entityType
            + "\",\"fieldKey\":\""
            + fieldKey
            + "\",\"permission\":\""
            + permission
            + "\"}");
  }

  // ===== 建实体 =====

  /** 建客户（调用方身份决定 owner：本夹具的角色用户建 ⇒ 归它自己，公海客户要用 ADMIN 建）。 */
  long createCustomer(String token, String name, String phone, String email) throws Exception {
    Res res =
        call(
            token,
            HttpMethod.POST,
            "/api/v1/customers",
            "{\"name\":\""
                + name
                + "\",\"company\":\""
                + name
                + " 公司\",\"contactPerson\":\"联系人\""
                + ",\"phone\":\""
                + phone
                + "\",\"email\":\""
                + email
                + "\",\"address\":\"地址\""
                + ",\"remark\":\"备注\"}");
    if (res.status() / 100 != 2) {
      throw new AssertionError("建客户失败：" + res.status() + " " + res.body());
    }
    return res.body().path("data").path("id").asLong();
  }

  /** 建商机（金额上下限都必须给：下限 > 上限会被 422）。 */
  long createOpportunity(String token, long customerId, String name, long min, long max)
      throws Exception {
    Res res =
        call(
            token,
            HttpMethod.POST,
            "/api/v1/opportunities",
            "{\"customerId\":"
                + customerId
                + ",\"name\":\""
                + name
                + "\",\"expectedAmountMin\":"
                + min
                + ",\"expectedAmountMax\":"
                + max
                + ",\"remark\":\"商机备注\",\"status\":\"ACTIVE\"}");
    if (res.status() / 100 != 2) {
      throw new AssertionError("建商机失败：" + res.status() + " " + res.body());
    }
    return res.body().path("data").path("id").asLong();
  }

  // ===== HTTP =====

  /** 发一个请求：{@code token} 为 null 时不带 Authorization（登录用）。 */
  Res call(String token, HttpMethod method, String path, String body) throws Exception {
    MockHttpServletRequestBuilder req =
        MockMvcRequestBuilders.request(method, java.net.URI.create(path));
    if (token != null) {
      req = req.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      req = req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    MvcResult result = mockMvc.perform(req).andReturn();
    byte[] content = result.getResponse().getContentAsByteArray();
    JsonNode parsed = null;
    if (content.length > 0) {
      try {
        parsed = mapper.readTree(new String(content, StandardCharsets.UTF_8));
      } catch (Exception ignored) {
        parsed = null; // 非 JSON 响应（xlsx）由调用方用 bytes 取
      }
    }
    return new Res(result.getResponse().getStatus(), parsed, content);
  }

  /** 取 xlsx 之类二进制响应体。 */
  byte[] callBytes(String token, HttpMethod method, String path) throws Exception {
    return call(token, method, path, null).bytes();
  }

  /** 从分页/列表信封里按名字找一行；找不到即失败（找不到多半是数据范围过滤把行挡掉了，不能当成"没有泄漏"）。 */
  static JsonNode findByName(JsonNode root, String name) {
    JsonNode items = root.path("data").path("items");
    for (JsonNode item : items) {
      if (name.equals(item.path("name").asText())) {
        return item;
      }
    }
    throw new AssertionError("响应里没有名为 " + name + " 的行：" + root);
  }

  record Actor(long id, String token) {}

  record Res(int status, JsonNode body, byte[] bytes) {}
}
