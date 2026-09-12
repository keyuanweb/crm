package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 开放平台集成测试（055 T015）：Key 鉴权/吊销/Webhook 订阅。
 *
 * <p><b>为什么本类要显式声明出站白名单</b>：083（FR-G13）把出站地址改为<b>默认全拒</b>——{@code crm.outbound.allowed-hosts}
 * 留空时，回环地址与公网地址<b>同样</b>不被放行（data-model.md §4）。本类的 webhook 订阅用例要给一个"可达的本地假端点"（{@code
 * http://localhost:9999/hook}），在默认配置下该地址会被拒， 于是用例观察到的是 422 而不是它真正要验证的订阅 CRUD 流程。
 *
 * <p>此处列出白名单是<b>声明本用例的环境前提</b>，不是放宽校验：出站被拒的那条路径由 {@code SecurityHardeningIT}（回环／私网／链路本地／云元数据逐类断言）与
 * {@code WebhookRedirectIT}（重定向跳转）覆盖， 本类不重复验证它。同样形制见 {@code WebhookRedirectIT} 的 {@code 127.0.0.1}
 * 白名单。
 */
@TestPropertySource(properties = "crm.outbound.allowed-hosts=localhost")
class OpenPlatformIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("API Key 流程：创建→鉴权访问→吊销 401")
  void apiKeyFlow() throws Exception {
    String token = loginAndGetToken();

    // 创建 Key
    String keyResp =
        mockMvc
            .perform(
                post("/api/v1/platform/api-keys")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"数据同步\", \"scopes\": [\"customer:read\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.key").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String apiKey = objectMapper.readTree(keyResp).path("data").path("key").asText();
    long keyId = objectMapper.readTree(keyResp).path("data").path("id").asLong();

    // 无 Key → 401
    mockMvc.perform(get("/api/v1/open/customers")).andExpect(status().isUnauthorized());

    // 有效 Key → 200（customer:read 有权限）
    mockMvc
        .perform(get("/api/v1/open/customers").header("X-API-Key", apiKey))
        .andExpect(status().isOk());

    // 吊销
    mockMvc
        .perform(
            post("/api/v1/platform/api-keys/{id}/revoke", keyId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 吊销后 401
    mockMvc
        .perform(get("/api/v1/open/customers").header("X-API-Key", apiKey))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("API Key 范围不足 → 403")
  void apiKeyScopeDenied() throws Exception {
    String token = loginAndGetToken();
    String keyResp =
        mockMvc
            .perform(
                post("/api/v1/platform/api-keys")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"只读\", \"scopes\": [\"customer:read\"]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String apiKey = objectMapper.readTree(keyResp).path("data").path("key").asText();

    // 无 lead:write 权限 → 403
    mockMvc
        .perform(
            post("/api/v1/open/leads")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权线索\", \"company\": \"X\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Webhook 订阅流程：创建→列表→toggle")
  void webhookFlow() throws Exception {
    String token = loginAndGetToken();

    String resp =
        mockMvc
            .perform(
                post("/api/v1/platform/webhooks")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"eventType\": \"LEAD_CREATED\", \"callbackUrl\": \"http://localhost:9999/hook\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.secret").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode data = objectMapper.readTree(resp).path("data");
    long webhookId = data.path("id").asLong();
    String secret = data.path("secret").asText();
    assertThatCustom(secret.length() > 10);

    mockMvc
        .perform(get("/api/v1/platform/webhooks").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].eventType").value("LEAD_CREATED"));

    mockMvc
        .perform(
            post("/api/v1/platform/webhooks/{id}/toggle", webhookId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));
  }

  // ===== FR-G11 / T029：密钥主体不得读到授权范围外的数据 =====
  //
  // 本类已存在（055 T015），故 T029 的"新增 OpenPlatformIT"以"在既有类中新增用例"落地：
  // 另建同名类会与既有类冲突，且既有三例（Key 流程 / scope 拒绝 / Webhook）仍需保留。

  /**
   * 密钥主体读到他人名下线索 —— 必须被拒（FR-G11）。
   *
   * <p><b>改造前必然失败</b>：{@code ApiKeyAuthFilter} 注入 {@code CrmPrincipal(0L, "open-api", "ADMIN")}，而
   * {@code LeadService.visibleOwnerFilter()} 以「主体角色是否为 ADMIN」决定是否加行级过滤—— 于是任意有效密钥都拿到全量线索，与密钥自身被授予的
   * scope 无关。本用例断言"密钥读不到他人名下的线索"， 在改造前该线索必然出现在响应里。
   *
   * <p>实现后此断言<b>必须继续保持</b>：它是本次语义变更的固定点，不得为了让实现通过而放宽。
   */
  @Test
  @DisplayName("受限密钥读取授权范围外数据被拒（FR-G11）")
  void apiKeyCannotReadLeadsOwnedByAnotherUser() throws Exception {
    Fixture f = seedKeyWithAnotherOwnersData("[\"lead:read\"]");

    JsonNode body = openGet(f.apiKey(), "/api/v1/open/leads");
    assertNameAbsent(body.path("data").path("items"), f.leadName(), "/open/leads");
  }

  /**
   * 同一缺陷在客户接口上的表现 —— 必须被拒（FR-G11）。
   *
   * <p><b>本用例当前是"因错误的原因而通过"</b>：注入主体的用户标识为 {@code 0L}，而 {@code
   * CustomerService.applyDataScopeFilter} 走的是 {@code DataPermissionService.resolveVisibleOwnerIds}
   * ——它按<b>数据库里的用户</b>判角色，{@code 0L} 查不到用户，于是被当作"可见集 = {0}"，
   * 结果是空列表。也就是说客户接口今天的"安全"来自一个<b>不存在的用户标识</b>，不是来自授权判定。
   *
   * <p>因此本用例的价值在实现之后：一旦把主体换成密钥所属主体的真实标识，该路径的"无限制"判定 （按库中角色 ADMIN 强制 ALL）就会生效并泄漏全量。本断言即用于钉住这一条判定路径，
   * 使"去 ADMIN 化"不得只改主体而留下按库判定的旁路。
   */
  @Test
  @DisplayName("受限密钥读取他人名下客户被拒（FR-G11，行级判定旁路）")
  void apiKeyCannotReadCustomersOwnedByAnotherUser() throws Exception {
    Fixture f = seedKeyWithAnotherOwnersData("[\"customer:read\"]");

    JsonNode body = openGet(f.apiKey(), "/api/v1/open/customers");
    assertNameAbsent(body.path("data").path("items"), f.customerName(), "/open/customers");
  }

  /**
   * 以密钥访问开放端点并解析 JSON。
   *
   * <p><b>必须走字节流</b>：响应头是 {@code application/json}（不带 charset）， {@code
   * MockHttpServletResponse.getContentAsString()} 因而按 ISO-8859-1 解码，中文名变成乱码，
   * 使"响应里是否含某条中文名数据"的断言<b>恒为真</b>——一个不可能失败的断言，正是本规格要消除的假绿形态 （初版本用例即因此假绿通过，改走字节流后才现出真实行为）。Jackson
   * 读字节流会按 UTF-8 自动判定编码。
   */
  private JsonNode openGet(String apiKey, String path) throws Exception {
    byte[] body =
        mockMvc
            .perform(get(path).header("X-API-Key", apiKey))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return objectMapper.readTree(body);
  }

  /** 密钥 + 一名与该密钥无关的销售用户，及其名下的线索与客户。 */
  private record Fixture(String apiKey, String leadName, String customerName) {}

  private Fixture seedKeyWithAnotherOwnersData(String scopesJson) throws Exception {
    String adminToken = loginAndGetToken();

    // 另一名销售用户（数据范围默认为 SELF，见 user.data_scope 默认值）
    String username = "it_scope_" + System.nanoTime();
    String createdUser =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\": \""
                            + username
                            + "\", \"displayName\": \"范围测试销售\", \"role\": \"SALES\","
                            + " \"password\": \"pass1234\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long salesId = objectMapper.readTree(createdUser).path("data").path("id").asLong();
    String salesToken = loginAndGetToken(username, "pass1234");

    // 该销售名下的线索与客户（064：非 ADMIN 创建时 owner 默认为本人）
    String leadName = "范围外线索-" + salesId;
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + leadName + "\", \"company\": \"越权测试公司\"}"))
        // 200 而非 201：LeadController.create 未声明 @ResponseStatus（与 CustomerController 不同）
        .andExpect(status().isOk());
    String customerName = "范围外客户-" + salesId;
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + customerName + "\", \"company\": \"越权测试公司\"}"))
        .andExpect(status().isCreated());

    // 开放平台密钥仅 ADMIN 可创建 —— 即密钥所属主体必然是其创建者
    String apiKey =
        mockMvc
            .perform(
                post("/api/v1/platform/api-keys")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"范围测试\", \"scopes\": " + scopesJson + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Fixture(
        objectMapper.readTree(apiKey).path("data").path("key").asText(), leadName, customerName);
  }

  /** 断言响应条目里没有指定名称的实体；命中即抛错并给出接口路径，便于定位是哪条路径泄漏。 */
  private void assertNameAbsent(JsonNode items, String name, String endpoint) {
    for (JsonNode item : items) {
      if (name.equals(item.path("name").asText())) {
        throw new AssertionError("密钥读取到授权范围外的数据：" + endpoint + " 返回了 " + name);
      }
    }
  }

  private void assertThatCustom(boolean condition) {
    if (!condition) {
      throw new AssertionError("condition failed");
    }
  }
}
