package com.crm.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 迁移链 DDL 名冲突守卫（083-engineering-consolidation，T079）。
 *
 * <p><b>为什么需要它</b>：MySQL **没有** {@code ADD KEY IF NOT EXISTS}，因此在同一张表上第二次创建同名索引／约束**必然报错** {@code
 * Error Code 1061 Duplicate key name}。这类缺陷**只在全新数据库上暴露**——所有增量长出来的开发库都不会重放整条迁移链，
 * 于是它可以潜伏很久，直到某天"干净检出 + 一键启动"装不上为止。本规格记录的 D1 正是这一形态： {@code V7__lead.sql} 建了 {@code
 * idx_lead_deleted_name}，{@code V54__search_indexes.sql} 又建了一次同名索引， 而后者在有历史的库上执行成功（该库的 lead
 * 表当时没有这个索引），在空库上必然失败。
 *
 * <p><b>与既有守卫的分工</b>：{@link SchemaParityIT} 管"迁移 ↔ H2 镜像"的一致性，{@link
 * #migrationChainNeverRecreatesALiveDdlName()}
 * 这一条管"迁移链自身能不能在空库上跑通"的一个**可机械判定**的子集。两者互补：前者对本缺陷完全无感（它只比对版本号清单与表名）， 后者不关心镜像。
 *
 * <p><b>判定模型</b>：按版本号升序重放整条链，维护"当前活着的 (表, 名字)"集合。遇到创建时若该 (表, 名字) **仍然活着**， 即为违规；遇到 DROP 则从集合移除。这样"先
 * DROP 再重建"（合法的改索引手法，本仓库 {@code uk_sales_target_active_month} 即是） 不会被误判。
 *
 * <p><b>作用域按表，不按库</b>：MySQL 的索引名是**表级**的，不同表上的同名索引完全合法——故本守卫比对的是 {@code (表名, 名字)}
 * 二元组。若按全库名字比对，会对着一段完全正常的迁移报红。
 *
 * <p><b>本守卫不覆盖（如实划界，宁可漏报不可误报）</b>：
 *
 * <ul>
 *   <li><b>列级与类型层冲突</b>：如两处 {@code ADD COLUMN} 同名。已知未覆盖，本仓库当前无此形态。
 *   <li><b>定位不到表名的语句一律跳过</b>：只认 {@code CREATE TABLE} / {@code ALTER TABLE} 开头、或带 {@code ON <表>} 的
 *       {@code CREATE INDEX}。这是刻意的取向——宁可漏报（守卫变弱）也不误报（守卫变噪音），与 {@link SchemaParityIT} 的同类取舍一致。跳过量由
 *       本类 {@code scanned} 计数 的防呆断言兜底，跳过过多会先在那里暴露。
 *   <li><b>镜像文件 {@code schema-h2.sql} 自身</b>：不扫。H2 建库时若有同名冲突会直接让测试库起不来，属"大声失败"，无需守卫。
 *   <li><b>迁移链能否真正在空库上跑通</b>：本守卫只是它的静态近似。真正的端到端证据只有在空库上实跑一次迁移才能给出 （见 {@code
 *       specs/083-engineering-consolidation/quickstart.md} 验证 6）。
 * </ul>
 */
class MigrationDdlCollisionIT {

  @Test
  @DisplayName("迁移链不得在仍存活的 DDL 名上重复创建（MySQL 无 ADD KEY IF NOT EXISTS）")
  void migrationChainNeverRecreatesALiveDdlName() throws IOException {
    Map<String, String> migrations = migrationsOnClasspath();
    assertTrue(
        migrations.size() >= 80,
        "只读到 " + migrations.size() + " 个迁移文件（预期 ≥80），先查类路径解析——" + "读不到就等于永远绿，这类守卫必须能自证扫到了东西");

    // (表名, 名字) → 该对象当前"活"着的那个版本号
    Map<String, String> liveSince = new HashMap<>();
    // 违规明细：名字 → 创建过的版本序列
    Map<String, List<String>> violations = new TreeMap<>();
    int creations = 0;
    int drops = 0;
    int scanned = 0;

    for (Map.Entry<String, String> migration : migrations.entrySet()) {
      String version = "V" + migration.getKey();
      for (DdlEvent event : ddlEventsIn(migration.getValue())) {
        scanned++;
        String key = event.table() + "." + event.name();
        if (event.creation()) {
          creations++;
          String previous = liveSince.put(key, version);
          if (previous != null) {
            violations
                .computeIfAbsent(key, unused -> new ArrayList<>())
                .add(previous + " → " + version);
          }
        } else {
          drops++;
          liveSince.remove(key);
        }
      }
    }

    // 防呆三道：正则若整体失效（零匹配），下面的违规断言会因"零违规"而假绿
    assertTrue(creations >= 120, "只解析出 " + creations + " 处索引／约束创建（预期 ≥120），先查解析正则与剥离注释的逻辑");
    assertTrue(
        drops >= 1,
        "一处 DROP 都没解析出来。本仓库 V44 明确 DROP 了 uk_sales_target_active_month——"
            + "解析不到它说明 DROP 分支已失效，而失效的 DROP 分支会把"
            + "「先 DROP 再重建」误判成冲突（假红），或把真正的冲突漏掉（假绿）");
    assertTrue(scanned >= 120, "只有 " + scanned + " 条语句被定位到表名（预期 ≥120），跳过比例过高，本守卫已失去意义");

    assertTrue(
        violations.isEmpty(),
        "以下 (表, 索引／约束名) 在迁移链中被重复创建，且中间没有 DROP——MySQL 无 ADD KEY IF NOT EXISTS，"
            + "这类库在**全新数据库**上执行到第二条即报 SQL State 42000 / Error Code 1061 "
            + "Duplicate key name，整套编排起不来："
            + violations
            + "。修法：保留第一次创建，把后一次删掉（若后一次是「补索引」性质的迁移，在文件里注明该索引已由更早的迁移建立）。"
            + "注意改的是**已发布**的迁移文件，其 checksum 会变，所有已部署的库都需要跑一次 flyway repair。");
  }

  /** 一段 SQL 里的事件，按出现顺序排列。 */
  private record DdlEvent(String table, String name, boolean creation) {}

  /**
   * 把一段迁移 SQL 拆成"按出现顺序排列的 DDL 事件"。
   *
   * <p>先剥注释、再按 {@code ;} 切分语句：DDL 语句体内不含分号，故切分是安全的；定位不到表名的语句整条跳过（见类注释的划界说明）。
   */
  private static List<DdlEvent> ddlEventsIn(String sql) {
    List<DdlEvent> events = new ArrayList<>();
    String withoutComments = stripComments(sql);
    for (String statement : withoutComments.split(";")) {
      String table = tableOf(statement);
      if (table == null) {
        continue;
      }
      Matcher matcher = DDL_NAME.matcher(statement);
      while (matcher.find()) {
        String dropped = matcher.group(1);
        if (dropped != null) {
          events.add(new DdlEvent(table, dropped.toLowerCase(java.util.Locale.ROOT), false));
          continue;
        }
        for (int group = 2; group <= 4; group++) {
          String created = matcher.group(group);
          if (created != null) {
            events.add(new DdlEvent(table, created.toLowerCase(java.util.Locale.ROOT), true));
            break;
          }
        }
      }
    }
    return events;
  }

  /**
   * 语句所属的表名；定位不到返回 {@code null}（调用方跳过该语句）。
   *
   * <p>三种形态：{@code CREATE TABLE t} / {@code ALTER TABLE t} 取紧跟其后的名字；{@code CREATE INDEX i ON t} 取
   * {@code ON} 之后的名字。
   */
  private static String tableOf(String statement) {
    Matcher createOrAlter = CREATE_OR_ALTER_TABLE.matcher(statement);
    if (createOrAlter.find()) {
      return createOrAlter.group(1).toLowerCase(java.util.Locale.ROOT);
    }
    Matcher on = INDEX_ON_TABLE.matcher(statement);
    if (on.find()) {
      return on.group(1).toLowerCase(java.util.Locale.ROOT);
    }
    return null;
  }

  /**
   * 剥掉行注释与块注释。
   *
   * <p>不剥会把说明文字里引用的索引名当成语句（本仓库的迁移普遍整句引用 DDL）。剥注释的代价（字符串字面量里的 {@code --} 被截断） 只会让守卫变弱，不会让它变红。
   */
  private static String stripComments(String sql) {
    return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--[^\\n]*", " ");
  }

  /**
   * DDL 名的创建与删除。
   *
   * <p><b>分支顺序有意义</b>：DROP 必须排在前面。否则 {@code DROP INDEX x} 会被 {@code index\s+name} 分支当成一次创建， 于是"先
   * DROP 再重建"立刻变成假红。四个捕获组依次是：DROP 的名字、{@code KEY} 的名字、独立 {@code INDEX} 的名字、{@code CONSTRAINT} 的名字。
   */
  private static final Pattern DDL_NAME =
      Pattern.compile(
          "(?i)(?:drop\\s+(?:index|key|foreign\\s+key|constraint)\\s+[`\"]?([a-z0-9_]+)"
              + "|(?:unique\\s+)?key\\s+[`\"]?([a-z0-9_]+)"
              + "|(?:create\\s+(?:unique\\s+)?index|add\\s+(?:unique\\s+)?index)\\s+[`\"]?([a-z0-9_]+)"
              + "|constraint\\s+[`\"]?([a-z0-9_]+))");

  /** {@code CREATE TABLE [IF NOT EXISTS] t} 或 {@code ALTER TABLE t}。 */
  private static final Pattern CREATE_OR_ALTER_TABLE =
      Pattern.compile(
          "(?i)(?:create\\s+table(?:\\s+if\\s+not\\s+exists)?|alter\\s+table)\\s+[`\"]?([a-z0-9_]+)");

  /** {@code ... ON t}（用于 {@code CREATE INDEX i ON t (...)} 这类不带表名前缀的语句）。 */
  private static final Pattern INDEX_ON_TABLE = Pattern.compile("(?i)\\son\\s+[`\"]?([a-z0-9_]+)");

  /** 类路径上全部迁移文件：版本号 → SQL 文本，按**数值**版本升序（重放顺序必须是迁移的真实执行顺序）。 */
  private static Map<String, String> migrationsOnClasspath() throws IOException {
    Map<String, Integer> order = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
    Map<String, String> sqlByVersion = new HashMap<>();
    for (Resource resource :
        new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql")) {
      String filename = resource.getFilename();
      if (filename == null) {
        continue;
      }
      try (var in = resource.getInputStream()) {
        String version = versionOf(filename);
        order.put(version, Integer.parseInt(version));
        sqlByVersion.put(version, new String(in.readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new UncheckedIOException("读取迁移文件失败：" + filename, e);
      }
    }
    Map<String, String> byVersion = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
    for (String version : new TreeSet<>(order.keySet())) {
      byVersion.put(version, sqlByVersion.get(version));
    }
    return byVersion;
  }

  /** 从 {@code V70__desc.sql} 取出版本号 {@code 70}。 */
  private static String versionOf(String filename) {
    int separator = filename.indexOf("__");
    return filename.substring(1, separator < 0 ? filename.length() - ".sql".length() : separator);
  }
}
