package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 工作流集成测试（013 T013/T025）：规则 CRUD/触发执行/日志/权限。 */
class WorkflowIT extends AbstractIntegrationTest {

  private long createRule(String token, String body) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/workflows/rules")
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
  @DisplayName("规则生命周期：创建→列表→编辑→停用→删除")
  void ruleLifecycle() throws Exception {
    String token = loginAndGetToken();

    long ruleId =
        createRule(
            token,
            "{\"name\": \"线索自动分配\", \"eventType\": \"LEAD_CREATED\", \"actionType\": \"ASSIGN\", \"action\": {\"targetUserId\": 1}}");

    // 列表
    mockMvc
        .perform(get("/api/v1/workflows/rules").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 编辑
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/workflows/rules/{id}", ruleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"线索自动分配-改\", \"eventType\": \"LEAD_CREATED\", \"actionType\": \"ASSIGN\", \"action\": {\"targetUserId\": 1}, \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("线索自动分配-改"));

    // 停用
    mockMvc
        .perform(
            post("/api/v1/workflows/rules/{id}/toggle", ruleId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));

    // 删除
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/workflows/rules/{id}", ruleId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("线索创建触发自动分配：ownerId 自动设置 + 日志")
  void leadCreatedTriggersAssign() throws Exception {
    String token = loginAndGetToken();
    // 目标用户
    String userResp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\": \"wfowner013\", \"password\": \"Passw0rd!\", \"displayName\": \"工作流归属\", \"role\": \"SALES\"}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long targetUserId = objectMapper.readTree(userResp).path("data").path("id").asLong();

    createRule(
        token,
        "{\"name\": \"线索自动分配\", \"eventType\": \"LEAD_CREATED\", \"actionType\": \"ASSIGN\", \"action\": {\"targetUserId\": "
            + targetUserId
            + "}}");

    // 创建线索 → 自动分配
    String leadResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"自动分配线索\", \"company\": \"自动化公司\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ownerId").value(targetUserId))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long leadId = objectMapper.readTree(leadResp).path("data").path("id").asLong();

    // 执行日志
    mockMvc
        .perform(get("/api/v1/workflows/logs").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.data.items[0].entityId").value(leadId));
  }

  @Test
  @DisplayName("商机阶段变更创建任务 + 回款登记触发")
  void stageChangeAndPaymentTrigger() throws Exception {
    String token = loginAndGetToken();
    createRule(
        token,
        "{\"name\": \"谈判建任务\", \"eventType\": \"OPPORTUNITY_STAGE_CHANGED\","
            + " \"condition\": {\"field\": \"stage\", \"value\": \"NEGOTIATING\"},"
            + " \"actionType\": \"CREATE_TASK\", \"action\": {\"titleTemplate\": \"跟进{name}\", \"dueDays\": 3}}");

    // 建客户+商机+销售机会
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"工作流客户\", \"company\": \"工作流公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();
    String oppResp =
        mockMvc
            .perform(
                post("/api/v1/opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"name\": \"工作流商机\", \"expectedAmountMin\": 10000, \"expectedAmountMax\": 20000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long oppId = objectMapper.readTree(oppResp).path("data").path("id").asLong();

    // 创建销售机会（初始阶段 INITIAL_CONTACT）
    String soResp =
        mockMvc
            .perform(
                post("/api/v1/sales-opportunities")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"opportunityId\": %d, \"stage\": \"INITIAL_CONTACT\", \"amount\": 15000}",
                            oppId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long soId = objectMapper.readTree(soResp).path("data").path("id").asLong();

    // 阶段变更 → NEGOTIATING → 触发建任务
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/sales-opportunities/{id}", soId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"opportunityId\": %d, \"stage\": \"NEGOTIATING\", \"amount\": 15000, \"version\": 0}",
                        oppId)))
        .andExpect(status().isOk());

    // 我的任务出现"跟进工作流商机"
    String tasksResp =
        mockMvc
            .perform(get("/api/v1/tasks?keyword=跟进").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(tasksResp).path("data").path("total").asLong())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("非管理员规则管理返回 403")
  void nonAdminForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"wfsales\", \"password\": \"Passw0rd!\", \"displayName\": \"工作流销售\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("wfsales", "Passw0rd!");

    mockMvc
        .perform(
            post("/api/v1/workflows/rules")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"x\", \"eventType\": \"LEAD_CREATED\", \"actionType\": \"NOTIFY\", \"action\": {\"message\": \"x\"}}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/workflows/logs").header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());
  }
}
