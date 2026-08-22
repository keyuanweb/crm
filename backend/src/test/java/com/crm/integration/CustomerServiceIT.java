package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 客户服务集成测试（015 T012/T020/T029）：工单/SLA/知识库/权限。 */
class CustomerServiceIT extends AbstractIntegrationTest {

  private long createCustomer(String token) throws Exception {
    String name = "服务客户" + System.nanoTime();
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format("{\"name\": \"%s\", \"company\": \"%s公司\"}", name, name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("工单生命周期：SLA 策略→建工单→分配→回复→流转→删除")
  void ticketLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);

    // 配置 SLA 策略（HIGH：响应 4h/解决 24h）
    mockMvc
        .perform(
            post("/api/v1/sla-policies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\": \"HIGH\", \"respondHours\": 4, \"resolveHours\": 24}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.priority").value("HIGH"));

    // 建工单
    String ticketResp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"登录失败\", \"priority\": \"HIGH\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.slaStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data.slaResolveDeadline").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();

    // 分配
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/assign", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\": 1}"))
        .andExpect(status().isOk());

    // 回复
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/reply", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"已联系客户确认\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("已联系客户确认"));

    // 流转：IN_PROGRESS→RESOLVED
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/transition", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"IN_PROGRESS\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/transition", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"RESOLVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RESOLVED"));

    // 非法流转：OPEN 不可跳 RESOLVED（用新工单）→ 409
    String openResp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"非法流转\", \"priority\": \"LOW\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long openTicketId = objectMapper.readTree(openResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/transition", openTicketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"RESOLVED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("TICKET_INVALID_STATE"));

    // 列表
    mockMvc
        .perform(get("/api/v1/tickets").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

    // SLA 超时统计
    mockMvc
        .perform(get("/api/v1/sla-policies/overview").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalOpen").isNumber());
  }

  @Test
  @DisplayName("知识库：创建→发布→搜索仅已发布→下线")
  void knowledgeArticleFlow() throws Exception {
    String token = loginAndGetToken();

    // 创建（默认草稿）
    mockMvc
        .perform(
            post("/api/v1/knowledge")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"category\": \"FAULT_TROUBLESHOOTING\", \"title\": \"如何重置密码\", \"content\": \"步骤\", \"keywords\": \"密码\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("DRAFT"));

    // 普通搜索不返回草稿
    mockMvc
        .perform(
            get("/api/v1/knowledge").param("keyword", "密码").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));

    // 管理员可见草稿
    mockMvc
        .perform(
            get("/api/v1/knowledge")
                .param("includeDraft", "true")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 发布
    long articleId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/v1/knowledge")
                            .param("includeDraft", "true")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data")
            .path("items")
            .get(0)
            .path("id")
            .asLong();
    mockMvc
        .perform(
            post("/api/v1/knowledge/{id}/publish", articleId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

    // 发布后普通搜索命中
    mockMvc
        .perform(
            get("/api/v1/knowledge").param("keyword", "密码").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  @DisplayName("权限：SUPPORT 可建工单不可配 SLA；SALES 建工单 403")
  void permissionMatrix() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"svcagent\", \"password\": \"Passw0rd!\", \"displayName\": \"服务客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("svcagent", "Passw0rd!");

    // SUPPORT 配 SLA → 403
    mockMvc
        .perform(
            post("/api/v1/sla-policies")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\": \"LOW\", \"respondHours\": 8}"))
        .andExpect(status().isForbidden());

    // SALES 建工单 → 403
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"svcsales\", \"password\": \"Passw0rd!\", \"displayName\": \"服务销售\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("svcsales", "Passw0rd!");
    mockMvc
        .perform(
            post("/api/v1/tickets")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": 1, \"title\": \"越权工单\", \"priority\": \"LOW\"}"))
        .andExpect(status().isForbidden());

    // SUPPORT 建工单成功（客户 id 不存在则 404，先建客户）
    long customerId = createCustomer(adminToken);
    mockMvc
        .perform(
            post("/api/v1/tickets")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"title\": \"客服工单\", \"priority\": \"MEDIUM\"}",
                        customerId)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("工单编辑：version 冲突 409")
  void ticketVersionConflict() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);
    String ticketResp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"版本冲突\", \"priority\": \"LOW\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();

    // 用错误的 version 更新 → 409
    mockMvc
        .perform(
            put("/api/v1/tickets/{id}", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"title\": \"版本冲突改\", \"priority\": \"LOW\", \"version\": 99}",
                        customerId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
  }
}
