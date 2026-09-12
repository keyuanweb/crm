package com.crm.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
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
 * <p>判定依据是<b>显式维护的版本清单</b>，而非解析 {@code schema-h2.sql} 的文本：后者需要脆弱且易误判的字符串匹配，且 无法表达"某迁移只需加列"这类非建表改动。
 *
 * <p><b>本守卫不覆盖</b>：迁移文件内容被修改但其版本号未变的情形（例如往 V70 里追加一句 SQL）。以版本号为准是有意为之—— 与
 * FR-G07"新增迁移未同步镜像时构建失败"的表述一致；内容漂移需靠评审而非本测试。
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
          "78", "79", "80", "81", "82", "83", "84", "85", "86", "87");

  @Test
  @DisplayName("每个生产迁移都必须已镜像进 H2 测试库")
  void everyMigrationIsMirroredInTestSchema() throws IOException {
    Set<String> missing = new TreeSet<>(migrationVersionsOnClasspath());
    missing.removeAll(MIRRORED_MIGRATIONS);

    assertTrue(
        missing.isEmpty(),
        "以下迁移尚未镜像进 schema-h2.sql，H2 测试库将缺表/缺列，依赖它们的用例只会以误导性的方式失败："
            + missing
            + "。请补齐镜像并把版本号加入 MIRRORED_MIGRATIONS。");
  }

  @Test
  @DisplayName("镜像清单不得包含不存在的迁移（防清单笔误与序号断档误记）")
  void mirroredListHasNoPhantomMigrations() throws IOException {
    Set<String> phantom = new TreeSet<>(MIRRORED_MIGRATIONS);
    phantom.removeAll(migrationVersionsOnClasspath());

    assertTrue(phantom.isEmpty(), "MIRRORED_MIGRATIONS 中的下列版本没有对应的迁移文件，请核对（注意 V72 不存在）：" + phantom);
  }

  /** 枚举类路径下全部迁移文件的版本号。 */
  private static Set<String> migrationVersionsOnClasspath() throws IOException {
    Set<String> versions = new TreeSet<>();
    for (Resource resource :
        new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql")) {
      String filename = resource.getFilename();
      if (filename != null) {
        versions.add(versionOf(filename));
      }
    }
    return versions;
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
