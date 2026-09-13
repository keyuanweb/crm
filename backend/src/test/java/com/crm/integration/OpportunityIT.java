package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 商机→销售机会→关闭 集成测试（T031，US3）。 */
class OpportunityIT extends AbstractIntegrationTest {

  @Autowired private CustomerMapper customerMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private PasswordEncoder passwordEncoder;

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"张三\", \"company\": \"XX 科技\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createOpportunity(String token, long customerId) throws Exception {
    String body =
        String.format(
            "{\"customerId\": %d, \"name\": \"年度合作\", \"expectedAmountMin\": 100000, \"expectedAmountMax\": 500000}",
            customerId);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("商机全流程：创建商机→创建销售机会→阶段流转→关闭")
  void opportunityPipelineLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);

    // 商机详情包含下属销售机会列表（空）
    mockMvc
        .perform(get("/api/v1/opportunities/{id}", oppId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customerName").value("张三"))
        .andExpect(jsonPath("$.data.salesOpportunities.length()").value(0));

    // 创建销售机会（INITIAL_CONTACT）
    String soBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 300000, \"stage\": \"INITIAL_CONTACT\", \"expectedCloseDate\": \"2026-09-30\"}",
            oppId);
    String soResp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(soBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long soId = objectMapper.readTree(soResp).path("data").path("id").asLong();

    // 阶段流转 → NEGOTIATING
    String updateBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 300000, \"stage\": \"NEGOTIATING\", \"version\": 0}",
            oppId);
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/sales-opportunities/{id}", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stage").value("NEGOTIATING"))
        .andExpect(jsonPath("$.data.version").value(1));

    // 关闭（WON）
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closeResult\": \"WON\", \"version\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stage").value("CLOSED_WON"))
        .andExpect(jsonPath("$.data.closeResult").value("WON"))
        .andExpect(jsonPath("$.data.closedAt").isNotEmpty());

    // 已关闭不可再编辑
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/sales-opportunities/{id}", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("ALREADY_CLOSED"));
  }

  @Test
  @DisplayName("SUPPORT 角色访问商机接口返回 403（权限矩阵）")
  void supportRoleForbidden() throws Exception {
    User support = new User();
    support.setUsername("support1");
    support.setPasswordHash(passwordEncoder.encode("pass1234"));
    support.setDisplayName("客服一");
    support.setRole("SUPPORT");
    support.setEnabled(true);
    userMapper.insert(support);

    String token = loginAndGetToken("support1", "pass1234");
    mockMvc
        .perform(get("/api/v1/opportunities").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("关闭缺少结果返回 422 CLOSE_RESULT_REQUIRED")
  void closeWithoutResultReturns422() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    String soBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 100000, \"stage\": \"INITIAL_CONTACT\"}", oppId);
    String soResp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(soBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long soId = objectMapper.readTree(soResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 0}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CLOSE_RESULT_REQUIRED"));
  }

  private long createSalesOpportunity(String token, long oppId) throws Exception {
    String soResp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"opportunityId\": %d, \"amount\": 100000, \"stage\": \"INITIAL_CONTACT\"}",
                            oppId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(soResp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("FR-V03：关闭端点两条失败路径语义**一致** —— 缺结果与非空非法结果都是 422 CLOSE_RESULT_REQUIRED")
  void bothCloseFailurePathsAgree() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    long soId = createSalesOpportunity(token, oppId);

    // ① 结果**缺失**（触发 CloseRequest 的 @NotBlank → 经由全局异常处理器映射）
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 0}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CLOSE_RESULT_REQUIRED"));

    // ② 结果**非空但非法**（不经 Bean Validation，由 SalesOpportunityService 判定）
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closeResult\": \"MAYBE\", \"version\": 0}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CLOSE_RESULT_REQUIRED"));

    // 本用例的要点在"① 与 ② 相同"：改造前 ① 走 Bean Validation 得 **400**、② 走域层得 **422**，
    // 同一语义的两种输入给出两种状态码。FR-V03 要求的正是消除这个自相矛盾。
  }

  @Test
  @DisplayName("FR-V04：已关闭的销售机会**再次关闭**返回 ALREADY_CLOSED（本次不得破坏既有域错误语义）")
  void closingAlreadyClosedReturnsAlreadyClosed() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    long soId = createSalesOpportunity(token, oppId);

    // 正常关闭（合法结果必须**能**成功 —— FR-V04 第 2 条：不得靠取消"结果必填"这条业务规则来实现 422）
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closeResult\": \"WON\", \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.closeResult").value("WON"));

    // 再次关闭 → ALREADY_CLOSED（注意走的是 **close** 路径；既有用例覆盖的是 update 路径）
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closeResult\": \"LOST\", \"version\": 1}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("ALREADY_CLOSED"));
  }

  @Test
  @DisplayName("边界（T014 探测项）：缺结果 + 版本过期 → 实测 **422**，而非 spec.md:88 倾向的 409")
  void missingResultTakesPrecedenceOverStaleVersion() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    long soId = createSalesOpportunity(token, oppId);

    // ── 对照组：先证明"版本过期**确实能被判定**为 409" ──────────────────────────
    // 用**合法**结果 + 一个必然不匹配的版本号（该记录刚建，版本远不到 99）。
    // 没有这一步，下面的探测项就分不清"优先级是 422"与"409 这条路径根本不可达"。
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closeResult\": \"WON\", \"version\": 99}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));

    // ── 探测项：同一版本号，但**不给结果** ────────────────────────────────────
    // 实测结论：**422 CLOSE_RESULT_REQUIRED**。
    // 机制（两层都如此，不是偶然）：
    //   ① 表现层：`SalesOpportunityController.close` 的 `@Valid @RequestBody` 在**进入方法体之前**
    //      就抛出 MethodArgumentNotValidException → 域层根本没被调用，version 从未被读到；
    //   ② 域层（即便绕过 ① 也还是这个顺序）：`SalesOpportunityService.close` 先在 :139 判结果，
    //      后在 :149 才用 version 做乐观锁更新并抛 VERSION_CONFLICT。
    // 故 spec.md:88 的"倾向：版本冲突优先"**从未被实现过**，且本规格未改动它（属 3 个缺陷之外）。
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities/{id}/close", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 99}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CLOSE_RESULT_REQUIRED"))
        // 明确排除"碰巧是 409"以外的误读：错误码不得是 VERSION_CONFLICT
        .andExpect(jsonPath("$.error.code").value(org.hamcrest.Matchers.not("VERSION_CONFLICT")));
  }

  @Test
  @DisplayName("逻辑删除商机后其销售机会不再可见")
  void deleteOpportunityCascades() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    long oppId = createOpportunity(token, customerId);
    String soBody =
        String.format(
            "{\"opportunityId\": %d, \"amount\": 100000, \"stage\": \"INITIAL_CONTACT\"}", oppId);
    mockMvc
        .perform(
            post("/api/v1/sales-opportunities")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(soBody))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/opportunities/{id}", oppId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/sales-opportunities")
                .header("Authorization", bearer(token))
                .param("opportunityId", String.valueOf(oppId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }
}
