package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** SLA 自动升级集成测试（1.3-sla-escalation）：分级升级/幂等/扫描范围。 */
class SlaEscalationIT extends AbstractIntegrationTest {

  /** 与 crm.sla.warning-min-hours 默认值一致：deadline 剩余 ≤2h 即进入预警窗口。 */
  private static final String SLA_WARNING = "SLA_WARNING";

  private static final String SLA_OVERDUE = "SLA_OVERDUE";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("SLA 升级：WARNING→处理人；OVERDUE→处理人+ADMIN；同级不重复；L3 只再通知 ADMIN")
  void escalationLevelsAndIdempotency() throws Exception {
    String adminToken = loginAndGetToken();
    long customerId = createCustomer(adminToken);
    long agentId = createUser(adminToken, "slaagent", "SUPPORT");
    String agentToken = loginAndGetToken("slaagent", "Passw0rd!");

    // HIGH 策略：响应/解决均 1 小时 → 建单即进入预警窗口（1h ≤ 2h）
    mockMvc
        .perform(
            post("/api/v1/sla-policies")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\": \"HIGH\", \"respondHours\": 1, \"resolveHours\": 1}"))
        .andExpect(status().isCreated());

    long ticketId = createTicket(adminToken, customerId, "HIGH");
    assertThat(ticketDetail(ticketId, adminToken).path("slaStatus").asText()).isEqualTo("WARNING");
    assign(adminToken, ticketId, agentId);

    // ---- L1：WARNING 只通知处理人，ADMIN 不受打扰 ----
    escalateNow(adminToken, 1);
    JsonNode afterL1 = ticketDetail(ticketId, adminToken);
    assertThat(afterL1.path("escalateLevel").asInt()).isEqualTo(1);
    assertThat(afterL1.path("slaStatus").asText()).isEqualTo("WARNING");
    assertThat(notificationCount(agentToken, SLA_WARNING)).isEqualTo(1);
    assertThat(notificationCount(adminToken, SLA_WARNING)).isZero();
    assertSystemAudit(ticketId, 1);

    // ---- 幂等：仍在同级，再扫一轮不重复通知 ----
    escalateNow(adminToken, 0);
    assertThat(ticketDetail(ticketId, adminToken).path("escalateLevel").asInt()).isEqualTo(1);
    assertThat(notificationCount(agentToken, SLA_WARNING)).isEqualTo(1);
    assertThat(notificationCount(adminToken, SLA_WARNING)).isZero();
    assertSystemAudit(ticketId, 1);

    // ---- L2：deadline 推到过去 → OVERDUE，通知处理人 + ADMIN ----
    // （改策略时限不会回溯改写已建工单的 deadline，故这里直接改这张工单的两个 deadline）
    setDeadlinesInPast(ticketId, 1);
    escalateNow(adminToken, 1);
    JsonNode afterL2 = ticketDetail(ticketId, adminToken);
    assertThat(afterL2.path("escalateLevel").asInt()).isEqualTo(2);
    assertThat(afterL2.path("slaStatus").asText()).isEqualTo("OVERDUE");
    assertThat(notificationCount(agentToken, SLA_OVERDUE)).isEqualTo(1);
    assertThat(notificationCount(adminToken, SLA_OVERDUE)).isEqualTo(1);

    // ---- L3：距上次升级满一个间隔（策略 resolve 时限 1h）→ 再升级，只再通知 ADMIN ----
    escalateAgainAfterInterval(ticketId, 3);
    escalateNow(adminToken, 1);
    assertThat(ticketDetail(ticketId, adminToken).path("escalateLevel").asInt()).isEqualTo(3);
    assertThat(notificationCount(adminToken, SLA_OVERDUE)).isEqualTo(2);
    // 处理人已在 L2 被通知过，L3 不再重复打扰
    assertThat(notificationCount(agentToken, SLA_OVERDUE)).isEqualTo(1);

    // ---- 已解决工单不在扫描范围内（升级是催办语义，RESOLVED 的 SLA 时钟已停）----
    long resolvedId = createTicket(adminToken, customerId, "HIGH");
    transition(adminToken, resolvedId, "IN_PROGRESS");
    transition(adminToken, resolvedId, "RESOLVED");
    setDeadlinesInPast(resolvedId, 10); // 若被扫到，必然升到 L2
    escalateAgainAfterInterval(resolvedId, 10);

    escalateNow(adminToken, 0);
    assertThat(ticketDetail(resolvedId, adminToken).path("escalateLevel").asInt()).isZero();
  }

  @Test
  @DisplayName("SLA 升级：无匹配策略的工单不升级（SLA 承诺不存在就不催办）")
  void noPolicyMeansNoEscalation() throws Exception {
    String adminToken = loginAndGetToken();
    long customerId = createCustomer(adminToken);
    // 只配 LOW 策略，工单用 MEDIUM：建单时拿不到策略 → 无 deadline → 不在候选集
    mockMvc
        .perform(
            post("/api/v1/sla-policies")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\": \"LOW\", \"respondHours\": 1, \"resolveHours\": 1}"))
        .andExpect(status().isCreated());
    long ticketId = createTicket(adminToken, customerId, "MEDIUM");
    assertThat(ticketDetail(ticketId, adminToken).path("slaResolveDeadline").isMissingNode())
        .isTrue();

    escalateNow(adminToken, 0);
    assertThat(ticketDetail(ticketId, adminToken).path("escalateLevel").asInt()).isZero();
  }

  // ===== 辅助 =====

  private long createCustomer(String token) throws Exception {
    String name = "升级客户" + System.nanoTime();
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

  private long createUser(String token, String username, String role) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"username\": \"%s\", \"password\": \"Passw0rd!\", \"displayName\": \"服务客服\", \"role\": \"%s\"}",
                            username, role)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createTicket(String token, long customerId, String priority) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"登录失败\", \"priority\": \"%s\"}",
                            customerId, priority)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private void assign(String token, long ticketId, long assigneeId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/assign", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"assigneeId\": %d}", assigneeId)))
        .andExpect(status().isOk());
  }

  private void transition(String token, long ticketId, String targetStatus) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/transition", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"targetStatus\": \"%s\"}", targetStatus)))
        .andExpect(status().isOk());
  }

  private JsonNode ticketDetail(long ticketId, String token) throws Exception {
    String resp =
        mockMvc
            .perform(get("/api/v1/tickets/{id}", ticketId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data");
  }

  /** 手动触发升级扫描（与定时作业同一实现），断言本轮升级数。 */
  private void escalateNow(String token, int expectedEscalated) throws Exception {
    mockMvc
        .perform(post("/api/v1/sla-policies/escalate-now").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.escalated").value(expectedEscalated));
  }

  private long notificationCount(String token, String type) throws Exception {
    String resp =
        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .param("type", type)
                    .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("total").asLong();
  }

  /** 把工单的响应/解决 deadline 都推到 N 小时前，制造超时。 */
  private void setDeadlinesInPast(long ticketId, int hoursAgo) {
    Timestamp past = Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo));
    jdbcTemplate.update(
        "UPDATE ticket SET sla_respond_deadline = ?, sla_resolve_deadline = ? WHERE id = ?",
        past,
        past,
        ticketId);
  }

  /**
   * 断言升级审计是「有主体」的行：actor_id = 0（哨兵，非 NULL）、actor_name = system、detail 带 {@code [system]} 前缀。
   *
   * <p>升级作业跑在调度线程上（无 SecurityContext），若走 {@code AuditService#record} 会写出 actor_id = NULL
   * 的孤儿行——既无法归因，也无法与「用户被删除后 actor_id 悬空」区分，故这里直接查表锁死该契约。
   */
  private void assertSystemAudit(long ticketId, int expectedRows) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT actor_id, actor_name, detail FROM audit_log WHERE action = 'SLA_ESCALATE' AND entity_id = ?",
            ticketId);
    assertThat(rows).hasSize(expectedRows);
    for (Map<String, Object> row : rows) {
      assertThat(row.get("actor_id")).as("审计行不得无主体（actor_id 为 NULL）").isNotNull();
      assertThat(((Number) row.get("actor_id")).longValue()).isZero();
      assertThat(row.get("actor_name")).isEqualTo("system");
      assertThat((String) row.get("detail")).startsWith("[system] ");
    }
  }

  /** 把上次升级时刻推到 N 小时前，制造「又满一个升级间隔」。 */
  private void escalateAgainAfterInterval(long ticketId, int hoursAgo) {
    jdbcTemplate.update(
        "UPDATE ticket SET last_escalated_at = ? WHERE id = ?",
        Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo)),
        ticketId);
  }
}
