package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.AbstractIntegrationTest;
import com.crm.entity.SalesQuotaAchievement;
import com.crm.entity.SalesQuotaVersion;
import com.crm.repository.quota.SalesQuotaAchievementRepository;
import com.crm.repository.quota.SalesQuotaVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
 * <p><b>本类的来历：一份缺陷留痕被改写成了正常断言</b>。T074 首次运行本类时暴露了 078-sales-quota 的真实缺陷——三张子表的实体都 {@code extends
 * BaseEntity}，而 V71 建表时没给它们 {@code deleted/version/updated_at} 三列，于是版本与分解的写路径在**任何**数据库上 都报 {@code
 * Unknown column}（用户可见形态：{@code PUT /{id}} 与 {@code POST /{id}/breakdown} 双双
 * 5xx）。当时留了一条钉住缺陷的用例（断言"缺这三列""两个端点 5xx"），并把改写指示写在它的断言消息里。
 *
 * <p>T077 按该指示做了两件事：V88 补齐三张子表的列（镜像同步进 {@code schema-h2.sql} 并加进 {@code
 * SchemaParityIT.MIRRORED_MIGRATIONS}），本条用例随之转红——转红本身就是"修复生效"的证据——随后改写为 {@link
 * #quotaChildTablesCarryBaseEntityColumnsSoWritesSucceed()}：断言列齐备、版本号递增、两条分解回读得到行。 缺陷经过与转红时的原始输出记在
 * {@code specs/083-engineering-consolidation/tasks.md} 的 T077 记录里。
 *
 * <p><b>刻意不覆盖</b>：不测权限（{@code PermissionEnforcementIT} 已按码覆盖）、不测前端、不测参数校验分支。 {@code
 * sales_quota_achievement} 只探到"可写"——生产代码至今没有任何写入它的路径（{@code SalesQuotaServiceImpl} 注入了 Repository
 * 却从不调用，达成率是 {@code SalesQuotaRepository} 现算的），这是 078 的既有事实，不是本次修复的范围。
 */
class SalesQuotaIT extends AbstractIntegrationTest {

  /** 用一个远离其他用例的年份，避免与种子数据或别的 IT 落在同一批聚合里。 */
  private static final int YEAR = 2099;

  /**
   * {@code BaseEntity} 声明的四列。50 个继承 {@code BaseEntity} 的实体全部对应的表带全这四列——三张 quota 子表曾缺列（T077 已补齐，
   * {@link #quotaChildTablesCarryBaseEntityColumnsSoWritesSucceed()} 钉住）。
   */
  private static final Set<String> BASE_ENTITY_COLUMNS =
      Set.of("deleted", "version", "created_at", "updated_at");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private SalesQuotaVersionRepository versionRepository;
  @Autowired private SalesQuotaAchievementRepository achievementRepository;

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
  @DisplayName("T077 修复后：三张 quota 子表带全 BaseEntity 列，版本与分解写路径端到端可写可读")
  void quotaChildTablesCarryBaseEntityColumnsSoWritesSucceed() throws Exception {
    // ---- 一、结构：三张子表不再缺列（修复前它们的缺列清单由本用例的前身钉着，见 tasks.md T074）----
    assertThat(missingBaseEntityColumns("sales_quota_version")).isEmpty();
    assertThat(missingBaseEntityColumns("sales_quota_breakdown")).isEmpty();
    assertThat(missingBaseEntityColumns("sales_quota_achievement")).isEmpty();
    // 对照：父表从一开始就带全四列——修复前的失败不是"MP 不好用"，是这三张表没跟上约定
    assertThat(missingBaseEntityColumns("sales_quota")).isEmpty();

    // ---- 二、读：逻辑删除条件不再让 selectCount 报列名错误（修复前的第一个失败点）----
    assertThat(
            versionRepository.selectCount(
                new LambdaQueryWrapper<SalesQuotaVersion>().eq(SalesQuotaVersion::getQuotaId, 0L)))
        .as("deleted 列存在后，MP 追加的 `WHERE deleted = 0` 才成立")
        .isZero();

    String token = loginAndGetToken();
    long quotaId = createQuota(token, "4.00");

    // ---- 三、写（版本表）：PUT 触发 insert，随后回读 ----
    updateQuotaAmount(token, quotaId, "8.00");
    assertThat(versionNumbersOf(quotaId)).as("一次改额留下一条版本记录，版本号从 1 起").containsExactly(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM sales_quota_version WHERE quota_id = ? AND deleted = 0",
                quotaId))
        .as("deleted 由 @TableLogic 参与读取；NOT NULL DEFAULT 0 让新行落为未删除")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at FROM sales_quota_version WHERE quota_id = ? AND version_number = 1",
                Timestamp.class,
                quotaId))
        .as("updated_at 由 MetaObjectHandler 的 INSERT 填充写入（V88 之前该列不存在，INSERT 直接失败）")
        .isNotNull();

    JsonNode versions = readJson(token, "/api/v1/sales-quota/" + quotaId + "/versions");
    assertThat(versions.isArray()).isTrue();
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.get(0).path("versionNumber").asInt()).isEqualTo(1);
    assertThat(versions.get(0).path("oldAmount").decimalValue()).isEqualByComparingTo("4.00");
    assertThat(versions.get(0).path("newAmount").decimalValue()).isEqualByComparingTo("8.00");
    assertThat(versions.get(0).path("changeReason").asText()).isEqualTo("季度调整");

    // 第二次改额：版本号 = 该配额已有的版本数 + 1（Service 里那行 selectCount 现在真的执行得下去）
    updateQuotaAmount(token, quotaId, "12.00");
    assertThat(versionNumbersOf(quotaId))
        .as("版本号应递增而不是覆盖——这正是 Service 用 selectCount 算版本号的那行代码")
        .containsExactlyInAnyOrder(1, 2);

    JsonNode versions2 = readJson(token, "/api/v1/sales-quota/" + quotaId + "/versions");
    assertThat(versions2.get(0).path("versionNumber").asInt()).as("接口按版本号降序返回").isEqualTo(2);
    assertThat(versions2.get(0).path("oldAmount").decimalValue()).isEqualByComparingTo("8.00");
    assertThat(versions2.get(0).path("newAmount").decimalValue()).isEqualByComparingTo("12.00");

    // ---- 四、写（分解表）：POST 触发 insert，随后回读 ----
    // 分解会建出子配额，而子配额的 user_id 在生产库上有外键（fk_sales_quota_user）——故这里建两个**真实用户**，
    // 不凭空编 id。踩过的坑：镜像不建外键（T074 已登记的不覆盖项），早先版本用 userId=2/3 在 H2 上通过，
    // 而在生产 MySQL 上会被 FK 拒成 409（GlobalExceptionHandler 把 DataIntegrityViolationException 也映射为 409）。
    long firstUser = createUser(token, "t077_quota_u1");
    long secondUser = createUser(token, "t077_quota_u2");
    mockMvc
        .perform(
            post("/api/v1/sales-quota/" + quotaId + "/breakdown")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [{"quarter": 1, "userId": %d, "amount": 6.00},
                     {"quarter": 2, "userId": %d, "amount": 6.00}]
                    """
                        .formatted(firstUser, secondUser)))
        .andExpect(status().isCreated());

    assertThat(
            count("SELECT COUNT(*) FROM sales_quota_breakdown WHERE parent_quota_id = ?", quotaId))
        .as("两条分解各留一行关系记录（修复前一行都不会有：insert 直接抛 Unknown column，事务整体回滚）")
        .isEqualTo(2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM sales_quota_breakdown WHERE parent_quota_id = ? AND deleted = 0",
                quotaId))
        .isEqualTo(2);
    assertThat(count("SELECT COUNT(*) FROM sales_quota WHERE parent_id = ?", quotaId))
        .as("分解同时建出两条子配额")
        .isEqualTo(2);

    JsonNode breakdown = readJson(token, "/api/v1/sales-quota/" + quotaId + "/breakdown");
    assertThat(breakdown.size()).isEqualTo(2);
    BigDecimal total = BigDecimal.ZERO;
    for (JsonNode row : breakdown) {
      assertThat(row.path("childQuota").path("quarter").asInt()).isIn(1, 2);
      assertThat(row.path("childQuota").path("amount").decimalValue())
          .as("回读的子配额金额应等于写入值")
          .isEqualByComparingTo("6.00");
      total = total.add(row.path("amount").decimalValue());
    }
    assertThat(total).as("分解总额等于父配额（Service 的校验，能过说明金额确实落库了）").isEqualByComparingTo("12.00");

    // ---- 五、达成统计表：修复只到"可写"这一层 ----
    // 生产代码至今没有任何写入 sales_quota_achievement 的路径（Service 注入了 Repository 却从不调用，
    // 达成率是 SalesQuotaRepository 现算的）。故这里只能探写，不能断言业务行为——该事实另行登记。
    SalesQuotaAchievement achievement = new SalesQuotaAchievement();
    achievement.setQuotaId(quotaId);
    achievement.setActualAmount(new BigDecimal("6.00"));
    achievement.setAchievementRate(new BigDecimal("50.00"));
    achievement.setCalculatedAt(LocalDateTime.now());
    achievement.setQuotaYear(YEAR);
    achievement.setQuotaQuarter(1);
    achievementRepository.insert(achievement);
    assertThat(achievement.getId()).as("插入应拿到自增主键").isNotNull();
    assertThat(
            count(
                "SELECT COUNT(*) FROM sales_quota_achievement WHERE quota_id = ? AND deleted = 0",
                quotaId))
        .isEqualTo(1);
  }

  // ===== 辅助 =====

  /** 改配额金额（走 PUT，触发版本记录写入），断言 200。 */
  private void updateQuotaAmount(String token, long id, String amount) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/sales-quota/" + id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(quotaBody(amount, null)))
        .andExpect(status().isOk());
  }

  /**
   * 建一个真实用户（走 {@code POST /api/v1/users}），返回其 id。
   *
   * <p>用于分解用例：子配额的 {@code user_id} 在生产库上有外键，凭空编一个 id 只能在本镜像上通过（镜像不建外键）。
   */
  private long createUser(String token, String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username": "%s", "password": "Passw0rd!", "displayName": "quota bd user", "role": "SALES"}
                    """
                        .formatted(username)))
        .andExpect(status().isCreated());
    Long id = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    assertThat(id).as("建完用户应能在 user 表里查到：" + username).isNotNull();
    return id;
  }

  /** 该配额在版本表里的版本号（直接读表，不经接口——接口口径见 GET /{id}/versions）。 */
  private List<Integer> versionNumbersOf(long quotaId) {
    return jdbc.queryForList(
        "SELECT version_number FROM sales_quota_version WHERE quota_id = ?",
        Integer.class,
        quotaId);
  }

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
            // 显式按 UTF-8 解码：响应的 Content-Type 不带 charset 时，无参的 getContentAsString() 按
            // ISO-8859-1 解，中文断言会拿去比一串 mojibake（"季度调整" → "å­£åº¦è°æ´"）。产品本身没问题，
            // 是读取侧的口径问题——本类另一处中文断言在 ContactIT 已有同样处理。
            .getContentAsString(StandardCharsets.UTF_8);
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

  private static List<Long> idsIn(JsonNode records) {
    return records.findValues("id").stream().map(JsonNode::asLong).toList();
  }
}
