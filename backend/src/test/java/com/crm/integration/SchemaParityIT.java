package com.crm.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 迁移-镜像一致性守卫（083-engineering-consolidation，FR-G07）。
 *
 * <p>断言 {@code src/main/resources/db/migration} 下的每一个迁移都已镜像进 H2 测试库 {@code
 * schema-h2.sql}。缺项意味着测试库没有对应的表或列，而依赖它们的用例**不会以"缺表"报错，只会以误导性的方式失败**—— 这正是 V70 起七个迁移长期未镜像、22
 * 个用例止步于登录 500 的原因。
 *
 * <p><b>三段判定，逐段补前者之不足</b>：
 *
 * <ol>
 *   <li><b>版本清单</b>：类路径上每个迁移的版本号都必须在 {@link #MIRRORED_MIGRATIONS} 里。清单刻意逐项枚举而不用区间——区间会让"顺手加一"
 *       看起来无害，从而失去把关作用。
 *   <li><b>建表覆盖</b>（T072 新增）：清单里每个迁移<b>建的每一张表</b>，镜像文件里都必须有对应的 {@code CREATE
 *       TABLE}。这一段才是"镜像文件本身被改动过"的机械证据——此前只比对版本号，"新增迁移 + 加一行清单 + 忘了镜像"可以静默通过。
 *   <li><b>无建表迁移的版本标记</b>（T072 新增）：只加列／加索引／改可空／写种子的迁移建不出表，第 2 段对它无能为力，故要求镜像中出现该版本的显式标记 {@code
 *       V<n>}（约定见 {@code schema-h2.sql} 头部）。
 * </ol>
 *
 * <p><b>本守卫不覆盖（须如实知悉）</b>：
 *
 * <ul>
 *   <li><b>列级漂移</b>：第 2 段认的是表名，列型、默认值、约束是否与迁移一致仍靠评审。这是有意的边界——对 2 000 余行的翻译产物做列级文本比对，
 *       脆弱度远高于它拦得住的真实缺陷。
 *   <li><b>标记的位置</b>：第 3 段只证明"镜像文件被改动过"，不证明标记落在了正确的位置。之所以可以这样弱：第 2 段对建表迁移给出的是位置级证据，
 *       需要标记的只剩无建表迁移，而它们的正确镜像动作（在原表块里加一列）本就该留一行说明。
 *   <li><b>内容漂移而版本号不变</b>：例如往 V70 里追加一句 SQL 而不改版本号。与 FR-G07"新增迁移未同步镜像时构建失败"的表述一致。
 * </ul>
 */
class SchemaParityIT {

  /**
   * 已镜像进 {@code schema-h2.sql} 的迁移版本清单。
   *
   * <p><b>新增迁移时必须把版本号加入本清单</b>，这是本守卫唯一的强制点。清单刻意逐项枚举而不用区间——区间会让"顺手加一" 看起来无害，从而失去把关作用。
   */
  private static final Set<String> MIRRORED_MIGRATIONS =
      Set.of(
          "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
          "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31",
          "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46",
          "47", "48", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61",
          "62", "63", "64", "65", "66", "67", "68", "69", "70", "71", "73", "74", "75", "76", "77",
          "78", "79", "80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "90");

  @Test
  @DisplayName("每个生产迁移都必须已镜像进 H2 测试库")
  void everyMigrationIsMirroredInTestSchema() throws IOException {
    Set<String> missing = new TreeSet<>(migrationVersionsOnClasspath());
    missing.removeAll(MIRRORED_MIGRATIONS);

    assertTrue(
        missing.isEmpty(),
        "以下迁移尚未镜像进 schema-h2.sql，H2 测试库将缺表/缺列，依赖它们的用例只会以误导性的方式失败："
            + missing
            + "。请补齐镜像并把版本号加入 MIRRORED_MIGRATIONS。"
            + "（若刚删除过迁移文件，注意 target/classes 下可能残留上一次构建的副本——"
            + "本类读的是类路径，残留会让删掉的文件仍然可见。）");
  }

  @Test
  @DisplayName("镜像清单不得包含不存在的迁移（防清单笔误与序号断档误记）")
  void mirroredListHasNoPhantomMigrations() throws IOException {
    Set<String> phantom = new TreeSet<>(MIRRORED_MIGRATIONS);
    phantom.removeAll(migrationVersionsOnClasspath());

    assertTrue(phantom.isEmpty(), "MIRRORED_MIGRATIONS 中的下列版本没有对应的迁移文件，请核对（注意 V72 不存在）：" + phantom);
  }

  @Test
  @DisplayName("清单里每个迁移建的表，镜像文件里都必须有对应的 CREATE TABLE")
  void everyTableOfAMirroredMigrationIsDefinedInTheMirror() throws IOException {
    Set<String> defined = tablesNamedIn(mirrorSchema());
    // 防呆：解析正则失效或镜像读空时，下面那条断言会因为"零缺失"而通过——假绿
    assertTrue(
        defined.size() >= 60 && defined.containsAll(Set.of("customer", "user", "lead", "ticket")),
        "镜像里只解析出 "
            + defined.size()
            + " 张 CREATE TABLE（预期 ≥60 且含 customer/user/lead/ticket），先查解析正则与文件本身");

    Map<String, String> migrations = migrationsOnClasspath();
    Set<String> created = new TreeSet<>();
    migrations.values().forEach(sql -> created.addAll(tablesNamedIn(sql)));
    assertTrue(
        created.size() >= 60,
        "从 " + migrations.size() + " 个迁移里只解析出 " + created.size() + " 张 CREATE TABLE，先查解析正则");

    Set<String> missing = new TreeSet<>();
    for (String version : MIRRORED_MIGRATIONS) {
      String sql = migrations.get(version);
      if (sql == null) {
        continue; // 不存在的版本由 mirroredListHasNoPhantomMigrations 负责
      }
      for (String table : tablesNamedIn(sql)) {
        if (!defined.contains(table)) {
          missing.add("V" + version + " → " + table);
        }
      }
    }

    assertTrue(
        missing.isEmpty(),
        "以下迁移建的表在 schema-h2.sql 里没有 CREATE TABLE："
            + missing
            + "。版本号加进 MIRRORED_MIGRATIONS 只是第一步，镜像文件本身也必须被改动"
            + "（补齐建表语句；纯增删列的迁移见另一条断言的要求）。");
  }

  @Test
  @DisplayName("不建表的迁移，镜像文件里必须有它的显式版本标记")
  void everyMirroredMigrationWithoutDdlIsMarkedInTheMirror() throws IOException {
    String mirror = mirrorSchema();
    Map<String, String> migrations = migrationsOnClasspath();

    Set<String> ddlFree = new TreeSet<>();
    for (String version : MIRRORED_MIGRATIONS) {
      String sql = migrations.get(version);
      if (sql != null && tablesNamedIn(sql).isEmpty()) {
        ddlFree.add(version);
      }
    }
    // 防呆：正则若把 DDL 也当成"没建表"，下面会退化成"零违规通过"；反之若全被判成建表，本断言会空转
    assertTrue(
        ddlFree.size() >= 10 && ddlFree.contains("78") && ddlFree.contains("87"),
        "预期至少有 10 个只加列/加索引/写种子的迁移（含 V78、V87），实得 " + ddlFree + "，先查解析正则");

    Set<String> unmarked = new TreeSet<>();
    for (String version : ddlFree) {
      if (!Pattern.compile("V" + version + "(?![0-9])").matcher(mirror).find()) {
        unmarked.add(version);
      }
    }

    assertTrue(
        unmarked.isEmpty(),
        "以下迁移不建表（只加列/加索引/改可空/写种子），因此无法靠表名核对，但镜像文件里也没有它们的版本标记 V<n>："
            + unmarked
            + "。请把该迁移改动落在 schema-h2.sql 的对应位置，并在那一行行尾留下 `-- V<n>`"
            + "（约定见 schema-h2.sql 头部）——这道标记要防的正是"
            + "「只往 MIRRORED_MIGRATIONS 里加一行、镜像文件一字未改」。");
  }

  /** 读取 H2 测试库的建库脚本。读取的是类路径上那份——即测试真正加载的那一份（application-test.yml 的 schema-locations）。 */
  private static String mirrorSchema() throws IOException {
    Resource resource =
        new PathMatchingResourcePatternResolver().getResource("classpath:schema-h2.sql");
    if (!resource.exists()) {
      throw new IllegalStateException("类路径上没有 schema-h2.sql：本守卫必须读它，读不到时要报错而不是跳过（跳过等于永远绿）");
    }
    try (var in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** 类路径上全部迁移文件：版本号 → SQL 文本。 */
  private static Map<String, String> migrationsOnClasspath() throws IOException {
    Map<String, String> byVersion = new TreeMap<>();
    for (Resource resource :
        new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql")) {
      String filename = resource.getFilename();
      if (filename == null) {
        continue;
      }
      try (var in = resource.getInputStream()) {
        byVersion.put(versionOf(filename), new String(in.readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new UncheckedIOException("读取迁移文件失败：" + filename, e);
      }
    }
    return byVersion;
  }

  /**
   * 枚举一段 SQL 里 {@code CREATE TABLE} 的目标表名。
   *
   * <p>先剥掉 {@code --} 行注释再匹配：迁移里的说明文字常整句引用 DDL（如"见 V1 的 CREATE TABLE customer"），
   * 不剥会把注释当成语句。剥注释的代价（字符串字面量里的 {@code --} 被截断）只是可能少认一张表， 而少认会让本守卫变弱、不会变红。
   */
  private static Set<String> tablesNamedIn(String sql) {
    Set<String> names = new TreeSet<>();
    String withoutComments = sql.replaceAll("(?m)--[^\\n]*", "");
    Matcher matcher = CREATE_TABLE.matcher(withoutComments);
    while (matcher.find()) {
      names.add(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
    }
    return names;
  }

  /** {@code CREATE TABLE [IF NOT EXISTS] `name`}；表名允许反引号（MySQL 风格）与双引号。 */
  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "(?i)create\\s+table(?:\\s+if\\s+not\\s+exists)?\\s+[`\"]?([A-Za-z0-9_]+)[`\"]?");

  /** 枚举类路径下全部迁移文件的版本号。 */
  private static Set<String> migrationVersionsOnClasspath() throws IOException {
    return new TreeSet<>(migrationsOnClasspath().keySet());
  }

  /**
   * 从 {@code V70__desc.sql} 取出版本号 {@code 70}。
   *
   * <p>取版本号而非完整文件名：迁移文件仅改描述不应触发本守卫。
   */
  private static String versionOf(String filename) {
    int separator = filename.indexOf("__");
    return filename.substring(1, separator < 0 ? filename.length() - ".sql".length() : separator);
  }
}
