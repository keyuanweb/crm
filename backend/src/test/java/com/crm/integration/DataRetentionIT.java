package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据保留策略两张镜像表的最小集成测试（083-engineering-consolidation，T074）。
 *
 * <p><b>为什么需要这个类</b>：{@code data_retention_policy} / {@code data_retention_execution}
 * 是"为支撑集成测试而镜像"的表（V74）， 但在此之前没有任何 IT 读过它们——镜像漏列、唯一索引没镜像、H2 方言不吃某段 SQL，都不会有用例变红。而这段代码的失败模式尤其隐蔽：
 * {@code executeArchival} 会在**归档动作本身失败**时照样写一条 {@code status = SUCCESS}、{@code processedCount = N}
 * 的执行记录（见下）， 所以"没有用例红过"与"归档真的做了事"是两回事。
 *
 * <p><b>本类打的是哪一层</b>：策略 CRUD 与归档全部走真实 HTTP 端点（Controller → Service → Mapper → H2）；只有夹具数据（客户两行）用
 * JDBC 直插，以便精确控制 {@code created_at}（到期与否全靠它判定）。
 *
 * <p><b>未验证边界</b>：
 *
 * <ul>
 *   <li>生产 {@code V74} 给 {@code data_retention_execution.policy_id} 建了 {@code fk_dre_policy ... ON
 *       DELETE CASCADE}， <b>镜像没有建这个外键</b>。因此"删策略会级联删执行记录"这条语义在镜像上复现不出来——本类删策略时只断言策略行消失，不碰级联。
 *   <li>归档动作本身是否真的改动了数据，见 {@link
 *       #executeArchivalReportsSuccessButDoesNotArchive()}：它当前**不改动**，本类把这一点钉住。
 * </ul>
 */
class DataRetentionIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("保留策略 CRUD：增删改查都落在镜像表上（T074）")
  void policyCrudIsBackedByTheMirroredTable() throws Exception {
    String token = loginAndGetToken();

    long id = createPolicy(token, "CUSTOMER", 30, "ARCHIVE");
    assertThat(textOf("SELECT entity_type FROM data_retention_policy WHERE id = ?", id))
        .as("entity_type 应原样落库")
        .isEqualTo("CUSTOMER");
    assertThat(textOf("SELECT status FROM data_retention_policy WHERE id = ?", id))
        .as("Service 写的默认状态是 ACTIVE（非请求字段）")
        .isEqualTo("ACTIVE");

    JsonNode all = readJson(token, "/api/v1/data-retention/policies");
    assertThat(all.isArray()).as("策略列表应返回数组，实际：" + all).isTrue();
    assertThat(idsIn(all)).as("列表应含刚建的策略").contains(id);

    JsonNode one = readJson(token, "/api/v1/data-retention/policies/" + id);
    assertThat(one.path("retentionDays").asInt()).isEqualTo(30);
    assertThat(one.path("actionType").asText()).isEqualTo("ARCHIVE");

    mockMvc
        .perform(
            put("/api/v1/data-retention/policies/" + id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(policyBody("CUSTOMER", 7, "DELETE")))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT retention_days FROM data_retention_policy WHERE id = ?", Integer.class, id))
        .as("更新应落到表里（不是只改了返回的 DTO）")
        .isEqualTo(7);

    // 这张表没有 deleted 列、实体也不继承 BaseEntity → 删除是真删（与其它模块的软删语义不同，故单独钉住）
    mockMvc
        .perform(
            delete("/api/v1/data-retention/policies/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    assertThat(count("SELECT COUNT(*) FROM data_retention_policy WHERE id = ?", id))
        .as("删除策略应是物理删除（表无 deleted 列）")
        .isZero();
  }

  @Test
  @DisplayName("T074 发现 3 · 缺陷留痕：归档报 SUCCESS 且 processedCount=1，但一行都没被归档")
  void executeArchivalReportsSuccessButDoesNotArchive() throws Exception {
    String token = loginAndGetToken();
    long policyId = createPolicy(token, "CUSTOMER", 1, "ARCHIVE");

    // 夹具：一条 10 天前的客户（到期）、一条刚建的客户（未到期）。用 JDBC 直插是为了精确控制 created_at
    long expiredId = insertCustomer("T074 到期客户");
    jdbc.update(
        "UPDATE customer SET created_at = ? WHERE id = ?",
        Timestamp.valueOf(LocalDateTime.now().minusDays(10)),
        expiredId);
    long freshId = insertCustomer("T074 未到期客户");

    assertThat(
            count(
                "SELECT COUNT(*) FROM customer WHERE deleted = 0 AND created_at < ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1))))
        .as("前提：到期客户应恰好只有夹具里那一条（多于 1 说明有别的种子数据也到期了，本用例的计数断言需重算）")
        .isEqualTo(1);

    mockMvc
        .perform(post("/api/v1/data-retention/execute").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 执行记录确实写出来了，写的数字也对——**失败不在这条路径上**
    JsonNode executions =
        readJson(token, "/api/v1/data-retention/policies/" + policyId + "/executions");
    assertThat(executions.size()).as("一次执行应留一条记录，实际：" + executions).isEqualTo(1);
    assertThat(executions.get(0).path("status").asText()).as("执行记录报的是成功").isEqualTo("SUCCESS");
    assertThat(executions.get(0).path("processedCount").asInt())
        .as("processedCount 就是它**以为**处理掉的条数")
        .isEqualTo(1);

    // 机制：UPDATE 确实跑了（乐观锁把 version 从 0 推到 1），只是 SET 子句里没有 deleted。
    assertThat(intOf("SELECT version FROM customer WHERE id = ?", expiredId))
        .as("归档的 UPDATE 并非没执行——version 被乐观锁 +1 了，这正是缺陷的机制所在")
        .isEqualTo(1);

    // 但真相是：归档要做的 deleted = 1 根本没写进去。
    // `c.setDeleted(1); customerMapper.updateById(c)` 不生效——Customer 继承 BaseEntity（@TableLogic
    // deleted），
    // 而 MyBatis-Plus 的 updateById 会把逻辑删除字段排除出 SET 子句。被归档的九张表（客户/线索/联系人/跟进/商机/
    // 合同/工单/任务/工作流日志）走的是同一段生成的 SQL，生产 MySQL 行为一致。
    assertThat(deletedOf(expiredId))
        .as(
            "**缺陷留痕（T074 发现 3）**：本断言的期望值 0 是**当前事实，不是应当的行为**——"
                + "「归档」跑完并报 SUCCESS/processedCount=1，却没有一行数据被归档，而调用方（含定时作业）无从分辨。\n"
                + "它转红之时即缺陷被修好之日：届时请把期望改回 1、去掉用例标题里的「缺陷留痕」，"
                + "并改写 specs/083-engineering-consolidation/tasks.md 的 T074 记录")
        .isZero();
    assertThat(deletedOf(freshId)).as("未到期的客户无论如何都不该被动").isZero();
  }

  @Test
  @DisplayName("镜像里的 idx_drp_entity 唯一索引确有约束力（T074）")
  void duplicateEntityTypeIsRejected() {
    insertPolicyRow("CUSTOMER");
    assertThatThrownBy(() -> insertPolicyRow("CUSTOMER"))
        .as("同一 entity_type 只能有一条策略（镜像未建该唯一索引的话，重复策略会静默共存，归档就会跑两遍）")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ===== 辅助 =====

  private long createPolicy(String token, String entityType, int retentionDays, String actionType)
      throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/data-retention/policies")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(policyBody(entityType, retentionDays, actionType)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // 本模块的响应体是**裸 DTO**，不走全站 {data: …} 信封（FR-G17 已登记的形态差异）
    return objectMapper.readTree(resp).path("id").asLong();
  }

  private static String policyBody(String entityType, int retentionDays, String actionType) {
    return """
        {"entityType": "%s", "retentionDays": %d, "actionType": "%s"}
        """
        .formatted(entityType, retentionDays, actionType);
  }

  /** 直插一行客户（只填 NOT NULL 的 name/company，其余取默认值），返回其 id。 */
  private long insertCustomer(String name) {
    jdbc.update("INSERT INTO customer (name, company) VALUES (?, ?)", name, name + " 公司");
    return jdbc.queryForObject("SELECT id FROM customer WHERE name = ?", Long.class, name);
  }

  private int deletedOf(long customerId) {
    Integer d =
        jdbc.queryForObject("SELECT deleted FROM customer WHERE id = ?", Integer.class, customerId);
    return d == null ? 0 : d;
  }

  private void insertPolicyRow(String entityType) {
    jdbc.update(
        "INSERT INTO data_retention_policy (entity_type, retention_days, action_type, status)"
            + " VALUES (?, ?, ?, ?)",
        entityType,
        30,
        "ARCHIVE",
        "ACTIVE");
  }

  private String textOf(String sql, Object arg) {
    return jdbc.queryForObject(sql, String.class, arg);
  }

  private int intOf(String sql, Object arg) {
    Integer n = jdbc.queryForObject(sql, Integer.class, arg);
    return n == null ? 0 : n;
  }

  private JsonNode readJson(String token, String path) throws Exception {
    String resp =
        mockMvc
            .perform(get(path).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp);
  }

  private int count(String sql, Object... args) {
    Integer n = jdbc.queryForObject(sql, Integer.class, args);
    return n == null ? 0 : n;
  }

  private static List<Long> idsIn(JsonNode records) {
    return records.findValues("id").stream().map(JsonNode::asLong).toList();
  }
}
