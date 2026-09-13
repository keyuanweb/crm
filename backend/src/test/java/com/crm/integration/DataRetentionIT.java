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
 * {@code executeArchival} 曾经在归档动作**静默不生效**时照样写一条 {@code status = SUCCESS}、{@code processedCount =
 * N} 的执行记录——"没有用例红过"与"归档真的做了事"因此是两回事。该缺陷（T074 发现 3）已由 083 T078 修复，本类即其守卫。
 *
 * <p><b>本类打的是哪一层</b>：策略 CRUD 与归档全部走真实 HTTP 端点（Controller → Service → Mapper → H2）；只有夹具数据（客户两行）用
 * JDBC 直插，以便精确控制 {@code created_at}（到期与否全靠它判定）。
 *
 * <p><b>未验证边界</b>：
 *
 * <ul>
 *   <li>生产 {@code V74} 给 {@code data_retention_execution.policy_id} 建了 {@code fk_dre_policy ... ON
 *       DELETE CASCADE}， <b>镜像没有建这个外键</b>。因此"删策略会级联删执行记录"这条语义在镜像上复现不出来——本类删策略时只断言策略行消失，不碰级联。
 *   <li>归档只做逻辑删除（{@code deleted = 1}），策略上的 {@code actionType} 目前**不参与分支**：{@code processPolicy} 只按
 *       {@code entityType} 分派，写成 {@code DELETE} 或 {@code ANONYMIZE} 与 {@code ARCHIVE} 行为完全相同。两条语义是
 *       080 的下一步（plan.md 3.6），不在 T078 范围内，本类也不为它背书。
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
  @DisplayName("归档真的改动了数据，且 processedCount 报的是真改动数而非命中数（T074 发现 3，由 T078 修复）")
  void executeArchivalArchivesExpiredRowsAndReportsTheRealCount() throws Exception {
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

    // ① 归档**真的改了数据**。这里此前是缺陷留痕（钉的是 deleted == 0 且 version == 1）：
    //    `c.setDeleted(1); customerMapper.updateById(c)` 不生效——Customer 继承 BaseEntity（@TableLogic
    // deleted），
    //    而 MyBatis-Plus 的 updateById 会把逻辑删除字段排除出 SET 子句，于是 UPDATE 只动了 version/updated_at，
    //    行还在原处，执行记录却写 SUCCESS。被归档的九张表（客户/线索/联系人/跟进/商机/合同/工单/任务/工作流日志）
    //    走的是同一段生成的 SQL，生产 MySQL 行为一致。改用 deleteById（走 @TableLogic 的逻辑删除入口）后，
    //    两处期望值同时翻面：deleted 0→1、version 1→0。
    assertThat(deletedOf(expiredId)).as("到期的客户应被真的归档（deleted = 1）").isEqualTo(1);
    assertThat(deletedOf(freshId)).as("未到期的客户无论如何都不该被动").isZero();
    assertThat(intOf("SELECT version FROM customer WHERE id = ?", expiredId))
        .as("逻辑删除不参与乐观锁，故 version 不动——缺陷期的特征恰是「UPDATE 跑了、version 被 +1、行却没被删掉」")
        .isZero();

    // ② 执行记录报的条数是**真改动数**：命中 1 条、改动 1 条，两者相等。
    JsonNode executions =
        readJson(token, "/api/v1/data-retention/policies/" + policyId + "/executions");
    assertThat(executions.size()).as("一次执行应留一条记录，实际：" + executions).isEqualTo(1);
    assertThat(executions.get(0).path("status").asText()).as("归档成功，执行记录报成功").isEqualTo("SUCCESS");
    assertThat(executions.get(0).path("processedCount").asInt())
        .as("processedCount 应等于真正被归档的条数")
        .isEqualTo(1);

    // ③ 再跑一次：已归档的不再命中，processedCount 应**如实报 0**。
    //    这正是 T078 附带要求的那一点——「无事可做」与「跑了但没生效」在修复后才可区分：缺陷期两次执行都会写
    //    SUCCESS/1，而实际什么都没发生，两条记录彼此、以及与被归档的数据之间都对不上账。
    mockMvc
        .perform(post("/api/v1/data-retention/execute").header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    JsonNode afterSecondRun =
        readJson(token, "/api/v1/data-retention/policies/" + policyId + "/executions");
    assertThat(afterSecondRun.size()).as("第二次执行应再留一条记录").isEqualTo(2);
    // 按 executedAt 取值而不是按下标取值：两次执行可能落在同一时间精度内，顺序不该成为断言的一部分
    assertThat(afterSecondRun.findValues("processedCount").stream().map(JsonNode::asInt).toList())
        .as("两次执行应分别报「真的改了 1 条」与「无可归档 = 0」")
        .containsExactlyInAnyOrder(1, 0);
    assertThat(deletedOf(expiredId)).as("第二次执行不应动已被归档的行").isEqualTo(1);
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
