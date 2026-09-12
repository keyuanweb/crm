package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.AbstractIntegrationTest;
import com.crm.entity.SalesQuotaBreakdown;
import com.crm.entity.SalesQuotaVersion;
import com.crm.repository.quota.SalesQuotaBreakdownRepository;
import com.crm.repository.quota.SalesQuotaVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 销售配额四张镜像表的最小集成测试（083-engineering-consolidation，T074）。
 *
 * <p><b>为什么需要这个类</b>：{@code sales_quota} / {@code sales_quota_version} / {@code
 * sales_quota_breakdown} / {@code sales_quota_achievement} 四张表在 {@code schema-h2.sql}
 * 里存在，是"为支撑集成测试而镜像"的（V71/V73）； 但在此之前**没有任何用例读过它们**——镜像与迁移若有实质偏差（漏一列、唯一约束没镜像、H2 方言不吃某段
 * SQL），一个用例都不会变红，而 083 的 FR-G04／FR-G05 要的恰恰是"这些模块可被集成测试覆盖"，不是"脚本被翻译了一遍"。
 *
 * <p><b>本类打的是哪一层</b>：主要走真实 HTTP 端点（Controller → Service → Mapper → H2），不 mock 任何一层。特别值得指出的是本类首次执行了
 * {@code SalesQuotaRepository} 的三段**聚合 SQL**（达成率、团队排名、年度汇总）与批量达成率子查询——它们此前从未在 H2 上跑过，其中 {@code
 * YEAR(closed_at)}、{@code sq.year}、{@code FROM user}、以及 {@code SUM(bigint) / 1000000}
 * 的整数除法都是方言敏感的写法。
 *
 * <p><b>本类同时是一份缺陷留痕</b>：第一次运行就暴露了 078-sales-quota 的真实缺陷（三张子表缺 {@code BaseEntity}
 * 的四列，导致版本与分解写路径在任何数据库上都报错）， 见 {@link
 * #quotaChildTablesLackBaseEntityColumnsSoWritesFail()}。该用例**钉的是当前事实**，缺陷修好之日它会转红——那时请连同本 javadoc 与
 * {@code specs/083-engineering-consolidation/tasks.md} 的 T074 记录一起改写。
 *
 * <p><b>刻意不覆盖</b>：不测权限（{@code PermissionEnforcementIT} 已按码覆盖）、不测前端、不测参数校验分支。
 */
class SalesQuotaIT extends AbstractIntegrationTest {

  /** 用一个远离其他用例的年份，避免与种子数据或别的 IT 落在同一批聚合里。 */
  private static final int YEAR = 2099;

  /**
   * {@code BaseEntity} 声明的四列。50 个继承 {@code BaseEntity} 的实体里，47 张表带全这四列；例外只有三张 quota 子表（见缺陷留痕用例）。
   */
  private static final Set<String> BASE_ENTITY_COLUMNS =
      Set.of("deleted", "version", "created_at", "updated_at");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private SalesQuotaVersionRepository versionRepository;
  @Autowired private SalesQuotaBreakdownRepository breakdownRepository;

  @Test
  @DisplayName("配额父表：建/查/列表（含批量达成率子查询）都打到镜像表（T074）")
  void quotaParentTableRoundTrip() throws Exception {
    String token = loginAndGetToken();

    long quotaId = createQuota(token, "5.00");

    // 走 JDBC 复核：API 回显不足以证明表被写对（回显走的是内存里的实体）
    assertThat(statusOfQuota(quotaId)).as("新建配额应落库为 DRAFT（Service 的默认值，非请求字段）").isEqualTo("DRAFT");
    assertThat(amountOfQuota(quotaId))
        .as("金额应原样落进 sales_quota.amount（DECIMAL(15,2)）")
        .isEqualByComparingTo("5.00");

    JsonNode detail = readJson(token, "/api/v1/sales-quota/" + quotaId);
    assertThat(detail.path("amount").decimalValue()).isEqualByComparingTo("5.00");

    // 列表页会调 getAchievementBatch（<foreach> 生成 IN (...)）：这段 SQL 此前从未在 H2 上执行过
    JsonNode page = readJson(token, "/api/v1/sales-quota?year=" + YEAR + "&size=50");
    assertThat(page.path("total").asInt())
        .as("本年份应有刚建的这一条，实际：" + page.path("total"))
        .isGreaterThanOrEqualTo(1);
    assertThat(idsIn(page.path("records"))).as("列表应含刚建的配额").contains(quotaId);
  }

  @Test
  @DisplayName("达成率 + 年度汇总的聚合 SQL 在镜像上跑得出数字（T074）")
  void achievementAndSummaryAggregateAgainstTheMirroredTables() throws Exception {
    String token = loginAndGetToken();
    long quotaId = createQuota(token, "5.50");

    // 直接写一条在配额期间内成交的商机。amount 是 BIGINT，而聚合 SQL 除以 1 000 000——
    // 故 5 500 000 → 5.5。之所以取这个值而不是整数倍：整数倍下"整数除法截断"与"精确除法"
    // 给出同样的结果，看不出方言差异；非整数倍能把这个差异暴露在断言里。
    insertClosedWonOpportunity(5_500_000L);

    JsonNode achievement = readJson(token, "/api/v1/sales-quota/" + quotaId + "/achievement");
    assertThat(achievement.path("quotaAmount").decimalValue())
        .as("达成率接口应带出配额金额（聚合 SQL 的 quota_amount）")
        .isEqualByComparingTo("5.50");
    assertThat(achievement.path("actualAmount").decimalValue())
        .as(
            "实际金额 = SUM(amount) / 1000000。**这个数字是两库除法语义的实测点**：实测 H2 2.2.224 与 MySQL 同为 5.5"
                + "（SUM() 不把它当整数相除）。之所以取 5 500 000 而不是 5 000 000，正是为了让「截断」与「精确」在这条断言上"
                + "分道扬镳；哪天 H2 改了 SUM() 的返回类型，这里会第一个变红，而不是静默把 5.5 变成 5")
        .isEqualByComparingTo("5.50");
    assertThat(achievement.path("achievementRate").decimalValue())
        .as("达成率 = 5.5 / 5.5 × 100")
        .isEqualByComparingTo("100.00");
    assertThat(achievement.path("status").asText()).as("≥80% 应判为 ON_TRACK").isEqualTo("ON_TRACK");

    // 年度汇总走的是另一段 SQL：它用 YEAR(closed_at) 过滤，且对 parent_id IS NULL 的顶层配额求和
    JsonNode summary = readJson(token, "/api/v1/sales-quota/summary?year=" + YEAR);
    assertThat(summary.path("totalQuota").decimalValue())
        .as("年度总配额应只算顶层（parent_id IS NULL），实际：" + summary)
        .isEqualByComparingTo("5.50");
    assertThat(summary.path("totalActual").decimalValue())
        .as("与达成率接口同源的除法，两者必须给出同一个数")
        .isEqualByComparingTo("5.50");
    assertThat(summary.path("achievementRate").decimalValue()).isEqualByComparingTo("100.00");
  }

  @Test
  @DisplayName("团队排名 SQL：department 联表与 user 子查询能在镜像上执行（T074）")
  void teamRankingSqlRunsOnTheMirror() throws Exception {
    String token = loginAndGetToken();
    long quotaId = createQuotaWithTeam(token, "3.00", 1L);
    insertClosedWonOpportunity(5_500_000L);

    // 正对照：先把前提摆出来——user 1 不在 team 1 之前，子查询不该命中任何成交
    assertThat(rankingRowForTeam(token, 1L).path("actual_amount").decimalValue())
        .as("尚未把用户挂到 team 1 时，IN (SELECT id FROM user ...) 应查不到成交")
        .isEqualByComparingTo("0");

    jdbc.update("UPDATE user SET department_id = 1 WHERE id = 1");

    JsonNode row = rankingRowForTeam(token, 1L);
    assertThat(row.path("quota_amount").decimalValue()).isEqualByComparingTo("3.00");
    assertThat(row.path("actual_amount").decimalValue())
        .as("把用户挂进 team 1 后，team_id=1 的名下应聚合出 5.5——这一步才真正证明子查询**按团队关联**了，" + "而不只是语法能通过")
        .isEqualByComparingTo("5.50");
    assertThat(row.path("achievement_rate").decimalValue())
        .as("达成率 = 5.5 / 3.0 × 100")
        .isEqualByComparingTo("183.33");

    assertThat(count("SELECT COUNT(*) FROM sales_quota WHERE id = ? AND team_id = 1", quotaId))
        .as("team_id 应原样落库")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("镜像里的两条唯一约束确有约束力（T074：约束语义也算镜像保真度）")
  void mirroredUniqueConstraintsAreEnforced() {
    jdbc.update(
        "INSERT INTO sales_quota (`year`, team_id, user_id, amount, status, period_start,"
            + " period_end) VALUES (?, ?, ?, ?, ?, ?, ?)",
        YEAR,
        1L,
        1L,
        1.00,
        "DRAFT",
        java.sql.Date.valueOf(YEAR + "-01-01"),
        java.sql.Date.valueOf(YEAR + "-12-31"));

    // uk_year_team_user：同一 (年, 团队, 人) 不得重复
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO sales_quota (`year`, team_id, user_id, amount, status, period_start,"
                        + " period_end) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    YEAR,
                    1L,
                    1L,
                    2.00,
                    "DRAFT",
                    java.sql.Date.valueOf(YEAR + "-01-01"),
                    java.sql.Date.valueOf(YEAR + "-12-31")))
        .as("(year, team_id, user_id) 唯一约束若没被镜像，重复配额会静默写进去")
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbc.update(
        "INSERT INTO sales_quota_breakdown (parent_quota_id, child_quota_id, amount)"
            + " VALUES (?, ?, ?)",
        1L,
        2L,
        1.00);

    // uk_parent_child：同一对父子关系不得重复
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO sales_quota_breakdown (parent_quota_id, child_quota_id, amount)"
                        + " VALUES (?, ?, ?)",
                    1L,
                    2L,
                    3.00))
        .as("(parent_quota_id, child_quota_id) 唯一约束若没被镜像，同一对父子会重复累计金额")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("T074 发现 2 · 缺陷留痕：三张 quota 子表缺 BaseEntity 列，078 的版本/分解写路径必然报错")
  void quotaChildTablesLackBaseEntityColumnsSoWritesFail() throws Exception {
    // ---- 一、事实：三张子表各缺哪些列（镜像与生产 V71 逐列一致，SchemaParityIT 已核）----
    // 实体继承 BaseEntity → MyBatis-Plus 认为这几列存在：@TableLogic 的 deleted 会进每条 SELECT 的 WHERE，
    // @TableField(fill) 的 updated_at 与初始化为 0 的 version 会进每条 INSERT 的列清单。
    assertThat(missingBaseEntityColumns("sales_quota_version"))
        .as("版本表缺列 → Service 的 selectCount 与 insert 都会带上它们")
        .containsExactlyInAnyOrderElementsOf(Set.of("deleted", "version", "updated_at"));
    assertThat(missingBaseEntityColumns("sales_quota_breakdown"))
        .containsExactlyInAnyOrderElementsOf(Set.of("deleted", "version"));
    assertThat(missingBaseEntityColumns("sales_quota_achievement"))
        .containsExactlyInAnyOrderElementsOf(Set.of("deleted", "version", "updated_at"));

    // 对照：父表带全四列，所以本类的其它用例能绿——不是"MP 不好用"，是这三张表没跟上约定
    assertThat(missingBaseEntityColumns("sales_quota")).isEmpty();

    // ---- 二、后果（读）：逻辑删除条件让 selectCount 直接语法/列名错误 ----
    Throwable readFailure =
        catchThrowable(
            () ->
                versionRepository.selectCount(
                    new LambdaQueryWrapper<SalesQuotaVersion>()
                        .eq(SalesQuotaVersion::getQuotaId, 0L)));
    assertThat(rootMessage(readFailure))
        .as("MP 给 selectCount 追加了 `WHERE deleted = 0`，而版本表没有 deleted 列")
        .contains("deleted");

    // ---- 三、后果（写）：insert 带上 updated_at / version ----
    SalesQuotaVersion version = new SalesQuotaVersion();
    version.setQuotaId(0L);
    version.setOldAmount(new BigDecimal("1.00"));
    version.setNewAmount(new BigDecimal("2.00"));
    version.setChangedBy(1L);
    version.setChangedAt(LocalDateTime.now());
    version.setVersionNumber(1);
    String insertMessage = rootMessage(catchThrowable(() -> versionRepository.insert(version)));
    assertThat(insertMessage)
        .as("版本/分解的写路径在**任何**库上都跑不通（生产 MySQL 报的是同一个 Unknown column）")
        .containsAnyOf("updated_at", "version", "deleted");

    SalesQuotaBreakdown breakdown = new SalesQuotaBreakdown();
    breakdown.setParentQuotaId(0L);
    breakdown.setChildQuotaId(0L);
    breakdown.setAmount(new BigDecimal("1.00"));
    assertThat(rootMessage(catchThrowable(() -> breakdownRepository.insert(breakdown))))
        .as("分解表同样缺列")
        .containsAnyOf("updated_at", "version", "deleted");

    // ---- 四、用户可见后果：两个真实端点 5xx ----
    String token = loginAndGetToken();
    long quotaId = createQuota(token, "4.00");

    mockMvc
        .perform(
            put("/api/v1/sales-quota/" + quotaId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(quotaBody("8.00", null)))
        .andExpect(status().is5xxServerError());

    mockMvc
        .perform(
            post("/api/v1/sales-quota/" + quotaId + "/breakdown")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [{"quarter": 1, "userId": 2, "amount": 4.00}]
                    """))
        .andExpect(status().is5xxServerError());

    // 注意：分解请求的金额与父配额相等，故不会先被"分解总额必须等于配额"的校验拦下——这里红的是写库。
    // 好消息是两条路径都是 @Transactional，失败会整体回滚，不会留下半条子配额。
    assertThat(count("SELECT COUNT(*) FROM sales_quota WHERE parent_id = ?", quotaId))
        .as("失败的分解不得留下子配额（事务回滚）")
        .isZero();
  }

  // ===== 辅助 =====

  /** 建一个顶层配额（year=2099，挂 userId=1，期间为整年），返回其 id。 */
  private long createQuota(String token, String amount) throws Exception {
    return createQuota(token, amount, null);
  }

  /** 同上，但可指定 teamId（排名的 WHERE 要求 team_id 非空）。 */
  private long createQuotaWithTeam(String token, String amount, Long teamId) throws Exception {
    return createQuota(token, amount, teamId);
  }

  private long createQuota(String token, String amount, Long teamId) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/sales-quota")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(quotaBody(amount, teamId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // 注意：本模块的响应体是**裸 DTO**，不走全站 {data: …} 信封（FR-G17 已登记的形态差异）
    return objectMapper.readTree(resp).path("id").asLong();
  }

  private static String quotaBody(String amount, Long teamId) {
    return """
        {"year": %d, "amount": %s, "userId": 1, "teamId": %s, "changeReason": "季度调整",
         "periodStart": "%d-01-01", "periodEnd": "%d-12-31"}
        """
        .formatted(YEAR, amount, teamId == null ? "null" : teamId, YEAR, YEAR);
  }

  /** 写一条在 2099 期间内成交、归属 userId=1 的商机（聚合 SQL 只看 stage/closed_at/created_by/amount）。 */
  private void insertClosedWonOpportunity(long amount) {
    jdbc.update(
        "INSERT INTO sales_opportunity (opportunity_id, amount, stage, closed_at, created_by,"
            + " created_at, deleted, version) VALUES (?, ?, ?, ?, ?, ?, 0, 0)",
        987_654_321L,
        amount,
        "CLOSED_WON",
        Timestamp.valueOf(LocalDateTime.of(YEAR, 6, 1, 10, 0)),
        1L,
        Timestamp.valueOf(LocalDateTime.now()));
  }

  /**
   * 取团队排名里 team_id = teamId 的那一行。
   *
   * <p><b>键名是实测出来的坑（T074 发现 4）</b>：别名 {@code teamId} 在 H2 上被折成 {@code teamid}——{@code
   * DATABASE_TO_LOWER=TRUE} 会把未加引号的标识符折成小写，结果集的列名也算在内；MySQL 则原样保留 {@code teamId}。也就是说这段 SQL
   * 的**出参键名在镜像上验不了**： 前端能读到 {@code teamId} 是生产端行为，本类只能钉住"SQL 跑得通、数字对"。故此处按大小写不敏感查找。
   */
  private JsonNode rankingRowForTeam(String token, long teamId) throws Exception {
    JsonNode ranking = readJson(token, "/api/v1/sales-quota/ranking?year=" + YEAR);
    assertThat(ranking.isArray()).as("排名接口应返回数组，实际：" + ranking).isTrue();
    for (JsonNode row : ranking) {
      var names = row.fieldNames();
      while (names.hasNext()) {
        String name = names.next();
        if (name.equalsIgnoreCase("teamId") && row.path(name).asLong() == teamId) {
          return row;
        }
      }
    }
    throw new AssertionError(
        "排名里没有 team_id=" + teamId + " 的行（department 联表或 user 子查询写错时会先失败），实际：" + ranking);
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

  private String statusOfQuota(long id) {
    return jdbc.queryForObject("SELECT status FROM sales_quota WHERE id = ?", String.class, id);
  }

  private BigDecimal amountOfQuota(long id) {
    return jdbc.queryForObject("SELECT amount FROM sales_quota WHERE id = ?", BigDecimal.class, id);
  }

  private int count(String sql, Object... args) {
    Integer n = jdbc.queryForObject(sql, Integer.class, args);
    return n == null ? 0 : n;
  }

  /** 表里实际存在的列（读元数据，不查 information_schema，避免依赖库的大小写约定）。 */
  private Set<String> columnsOf(String table) {
    return jdbc.query(
        "SELECT * FROM " + table + " WHERE 1 = 0",
        rs -> {
          ResultSetMetaData md = rs.getMetaData();
          Set<String> cols = new TreeSet<>();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            cols.add(md.getColumnLabel(i).toLowerCase());
          }
          return cols;
        });
  }

  /** BaseEntity 声明、而该表没有的列。 */
  private Set<String> missingBaseEntityColumns(String table) {
    Set<String> missing = new TreeSet<>(BASE_ENTITY_COLUMNS);
    missing.removeAll(columnsOf(table));
    return missing;
  }

  private static String rootMessage(Throwable t) {
    if (t == null) {
      return "<未抛出异常>";
    }
    Throwable cur = t;
    while (cur.getCause() != null) {
      cur = cur.getCause();
    }
    return cur.getMessage() == null ? cur.toString() : cur.getMessage();
  }

  private static List<Long> idsIn(JsonNode records) {
    return records.findValues("id").stream().map(JsonNode::asLong).toList();
  }
}
