package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 任务集成测试（010 T009/T015/T019/T024）：CRUD/提醒/日历/跟进自动建任务/数据隔离。 */
class TaskIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("任务生命周期：创建→列表（含提醒标识）→完成→重开→删除")
  void taskLifecycle() throws Exception {
    String token = loginAndGetToken();
    String overdueDue =
        LocalDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    // 创建逾期任务
    String resp =
        mockMvc
            .perform(
                post("/api/v1/tasks")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\": \"跟进旧客户\", \"dueAt\": \""
                            + overdueDue
                            + "\", \"priority\": \"HIGH\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.reminderStatus").value("OVERDUE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long taskId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 列表（按唯一标题过滤，避免同 JVM 跨测试数据累计干扰）
    mockMvc
        .perform(
            get("/api/v1/tasks?status=TODO&keyword=跟进旧客户").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].overdueDays").isNumber());

    // 完成
    mockMvc
        .perform(post("/api/v1/tasks/{id}/toggle", taskId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DONE"))
        .andExpect(jsonPath("$.data.reminderStatus").value("DONE"));

    // 重开
    mockMvc
        .perform(post("/api/v1/tasks/{id}/toggle", taskId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("TODO"));

    // 编辑（先取当前 version，toggle 已完成/重开递增了 version）
    String currentResp =
        mockMvc
            .perform(get("/api/v1/tasks?keyword=跟进旧客户").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    int currentVersion =
        objectMapper
            .readTree(currentResp)
            .path("data")
            .path("items")
            .get(0)
            .path("version")
            .asInt();
    mockMvc
        .perform(
            put("/api/v1/tasks/{id}", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\": \"跟进旧客户-改\", \"priority\": \"LOW\", \"version\": "
                        + currentVersion
                        + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("跟进旧客户-改"));

    // 删除
    mockMvc
        .perform(delete("/api/v1/tasks/{id}", taskId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("提醒汇总与日历数据")
  void reminderSummaryAndCalendar() throws Exception {
    String token = loginAndGetToken();
    String today = LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"今日任务\", \"dueAt\": \"" + today + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.reminderStatus").value("TODAY"));

    mockMvc
        .perform(get("/api/v1/tasks/reminder-summary").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.todayCount").value(1));

    // 日历（本月应含今日任务）
    String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    mockMvc
        .perform(
            get("/api/v1/tasks/calendar")
                .header("Authorization", bearer(token))
                .param("month", month))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.month").value(month))
        .andExpect(jsonPath("$.data.days").isArray());
  }

  @Test
  @DisplayName("数据隔离：任务仅本人可见")
  void dataIsolation() throws Exception {
    // admin 建任务
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"admin 私有任务\"}"))
        .andExpect(status().isCreated());

    // 创建 sales 用户并登录
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"taskuser\", \"password\": \"Passw0rd!\", \"displayName\": \"任务用户\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("taskuser", "Passw0rd!");

    // sales 列表看不到 admin 任务
    mockMvc
        .perform(get("/api/v1/tasks").header("Authorization", bearer(salesToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));

    // sales 编辑 admin 任务 → 403
    String listResp =
        mockMvc
            .perform(get("/api/v1/tasks").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long adminTaskId =
        objectMapper.readTree(listResp).path("data").path("items").get(0).path("id").asLong();
    mockMvc
        .perform(
            put("/api/v1/tasks/{id}", adminTaskId)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"越权\", \"version\": 0}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("跟进自动建任务：勾选 createTask 后创建关联任务")
  void followUpCreatesTask() throws Exception {
    String token = loginAndGetToken();
    // 建客户
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"任务客户\", \"company\": \"任务公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();

    // 添加跟进（勾选创建任务）
    String next = LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    mockMvc
        .perform(
            post("/api/v1/follow-ups")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"method\": \"PHONE\", \"content\": \"沟通\", \"nextFollowUpAt\": \"%s\", \"createTask\": true}",
                        customerId, next)))
        .andExpect(status().isCreated());

    // 任务列表出现"跟进：任务客户"
    String listResp =
        mockMvc
            .perform(
                get("/api/v1/tasks?linkedType=CUSTOMER").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    JsonNode items = objectMapper.readTree(listResp).path("data").path("items");
    assertThat(items.size()).isGreaterThanOrEqualTo(1);
    assertThat(items.get(0).path("title").asText()).contains("任务客户");
    assertThat(items.get(0).path("linkedId").asLong()).isEqualTo(customerId);
  }
}
