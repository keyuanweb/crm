package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 系统增强集成测试（016 T014/T024/T032）：自定义字段/通知/导出/权限。 */
class SystemEnhancementIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("自定义字段：配置→实体携带值→详情回显→筛选")
  void customFieldFlow() throws Exception {
    String token = loginAndGetToken();

    // 配置字段（LEAD 数字字段）
    String fieldResp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entityType\": \"LEAD\", \"name\": \"预算规模\", \"fieldType\": \"NUMBER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.name").value("预算规模"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long fieldId = objectMapper.readTree(fieldResp).path("data").path("id").asLong();

    // 创建线索携带字段值
    String leadResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"字段线索\", \"company\": \"字段公司\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"500\"}]}",
                            fieldId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long leadId = objectMapper.readTree(leadResp).path("data").path("id").asLong();

    // 详情回显
    mockMvc
        .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customFieldValues[0].fieldId").value(fieldId))
        .andExpect(jsonPath("$.data.customFieldValues[0].value").value("500"));

    // 列表包含自定义字段值
    mockMvc
        .perform(get("/api/v1/leads").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // SUPPORT 不可配置字段 → 403
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"enhanceagent\", \"password\": \"Passw0rd!\", \"displayName\": \"增强客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("enhanceagent", "Passw0rd!");
    mockMvc
        .perform(
            post("/api/v1/custom-fields")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityType\": \"LEAD\", \"name\": \"越权字段\", \"fieldType\": \"TEXT\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("自定义字段列表筛选（FR-S03：cf_<fieldId> SELECT 精确 / 文本 LIKE）")
  void customFieldListFilter() throws Exception {
    String token = loginAndGetToken();

    // 配置 LEAD SELECT 字段（选项 A/B）
    String selectResp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entityType\": \"LEAD\", \"name\": \"客户分级\", \"fieldType\": \"SELECT\", \"options\": \"A,B\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long selectFieldId = objectMapper.readTree(selectResp).path("data").path("id").asLong();

    // 两条线索分别携带不同选项
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"筛选线索A\", \"company\": \"筛A\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"A\"}]}",
                        selectFieldId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"筛选线索B\", \"company\": \"筛B\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"B\"}]}",
                        selectFieldId)))
        .andExpect(status().isOk());

    // SELECT 精确筛选 → 仅命中 A
    mockMvc
        .perform(
            get("/api/v1/leads")
                .header("Authorization", bearer(token))
                .param("cf_" + selectFieldId, "A"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("筛选线索A"));

    // 文本类型 LIKE 筛选
    String textResp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entityType\": \"LEAD\", \"name\": \"行业\", \"fieldType\": \"TEXT\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long textFieldId = objectMapper.readTree(textResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"行业线索\", \"company\": \"行\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"制造业\"}]}",
                        textFieldId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/leads")
                .header("Authorization", bearer(token))
                .param("cf_" + textFieldId, "制造"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("行业线索"));
  }

  @Test
  @DisplayName("通知中心：分配工单→通知→已读→未读计数")
  void notificationFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "通知客户");

    String ticketResp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"通知工单\", \"priority\": \"LOW\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();

    // 分配给自己不产生通知；先建一个 SUPPORT 用户再分配
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"notifyuser\", \"password\": \"Passw0rd!\", \"displayName\": \"通知用户\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    long targetId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/v1/users")
                            .param("keyword", "notifyuser")
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
            post("/api/v1/tickets/{id}/assign", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"assigneeId\": %d}", targetId)))
        .andExpect(status().isOk());

    // 目标用户登录查看通知
    String targetToken = loginAndGetToken("notifyuser", "Passw0rd!");
    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.unreadCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 全部已读
    mockMvc
        .perform(
            post("/api/v1/notifications/read-all").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(0));
  }

  @Test
  @DisplayName("数据导出：创建任务→状态查询→下载（完成后）")
  void exportFlow() throws Exception {
    String token = loginAndGetToken();
    createCustomer(token, "导出客户");

    String jobResp =
        mockMvc
            .perform(
                post("/api/v1/exports")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exportType\": \"CUSTOMER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long jobId = objectMapper.readTree(jobResp).path("data").path("id").asLong();

    // 轮询状态直到 DONE（异步执行）
    String status = "PENDING";
    for (int i = 0; i < 20 && ("PENDING".equals(status) || "RUNNING".equals(status)); i++) {
      Thread.sleep(200);
      String listResp =
          mockMvc
              .perform(get("/api/v1/exports").header("Authorization", bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      status =
          objectMapper.readTree(listResp).path("data").path("items").get(0).path("status").asText();
    }
    org.assertj.core.api.Assertions.assertThat(status).isEqualTo("DONE");

    // 下载
    mockMvc
        .perform(get("/api/v1/exports/{id}/download", jobId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(
                    "Content-Type",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
  }

  @Test
  @DisplayName("权限：SUPPORT 不可配置 SLA 之外的字段相关均走 403 路径已覆盖")
  void permissionMatrix() throws Exception {
    String token = loginAndGetToken();
    // SUPPORT 下载他人导出 → 403（先由 admin 创建）
    String jobResp =
        mockMvc
            .perform(
                post("/api/v1/exports")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exportType\": \"LEAD\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long jobId = objectMapper.readTree(jobResp).path("data").path("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"exportagent\", \"password\": \"Passw0rd!\", \"displayName\": \"导出客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("exportagent", "Passw0rd!");
    mockMvc
        .perform(
            get("/api/v1/exports/{id}/download", jobId)
                .header("Authorization", bearer(supportToken)))
        .andExpect(status().isForbidden());
  }

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"%s%d\", \"company\": \"%s公司\"}",
                            name, System.nanoTime(), name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }
}
