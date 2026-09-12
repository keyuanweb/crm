package com.crm.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 建库脚本幂等性守卫（083-engineering-consolidation，T018 实施期新增）。
 *
 * <p><b>为什么需要本守卫</b>：测试库是<b>共享</b>的具名内存库（{@code jdbc:h2:mem:crm;DB_CLOSE_DELAY=-1}），而同一个 JVM
 * 内可能出现多套 {@code ApplicationContext}——只要上下文键不同即可（实测：{@code AuthCaptchaIT} 带
 * {@code @TestPropertySource}，与 {@code AbstractIntegrationTest}
 * 不是同一套）。第二套上下文会<b>重跑本脚本</b>，因此脚本必须幂等。
 *
 * <p>该前提在接入 failsafe 前不成立：{@code AuthCaptchaIT} 是 {@code *IT}，surefire 从不执行它，JVM
 * 内只存在一套上下文，脚本只跑一次——DROP 块不完整的问题因此长期不可见。
 *
 * <p><b>为什么直接执行两次，而不是比对 {@code CREATE}／{@code DROP} 的名字集合</b>：名字集合只是<b>已知 根因</b>的代理指标，只能抓住"漏写
 * DROP"这一种非幂等成因。直接执行两次检验的是<b>不变量本身</b>——任何成因（漏写 DROP、二次执行violating唯一约束的种子
 * INSERT、语法在二次执行时才有差异）都会被抓到。代价是本用例约几十毫秒。
 *
 * <p><b>与 {@link SchemaParityIT} 的分工</b>：那个守卫比对"迁移文件集合 vs 已镜像版本清单"，<b>刻意不解析 schema-h2.sql
 * 的文本</b>（脆弱且易误判）；本守卫检验的是同一个文件的<b>内部自洽性</b>——这里没有比"执行它"更可靠的表示方式。两者是同族但不同轴的不变量。
 *
 * <p><b>不覆盖</b>：脚本对<b>生产</b> Flyway 迁移的可执行性（那是迁移工具与真实 MySQL 的职责），以及脚本语义是否正确（只保证可重复执行，不保证内容对）。
 */
class SchemaIdempotencyIT {

  private static final String SCHEMA_SCRIPT = "schema-h2.sql";

  @Test
  @DisplayName("建库脚本必须可重复执行（幂等），否则第二套上下文会污染共享测试库")
  void schemaScriptIsIdempotent() throws Exception {
    // 库名带随机后缀：本用例自带一套库，不与 application-test.yml 的 jdbc:h2:mem:crm 互相干扰。
    String url =
        "jdbc:h2:mem:idempotency-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCHEMA_SCRIPT));

      assertDoesNotThrow(
          () -> ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCHEMA_SCRIPT)),
          "schema-h2.sql 第二次执行失败——脚本不是幂等的。请检查："
              + "① 是否每张表都有对应的 DROP TABLE IF EXISTS（含新增表）；"
              + "② 是否有种子 INSERT 在二次执行时违反唯一约束。"
              + "该缺陷的实测症状是：第二套 ApplicationContext 重跑脚本时先清库再建表失败，"
              + "导致该 JVM 内其后**全部**用例级联失败，而报错指向的建表语句与真正的调用链相隔很远。");
    }
  }
}
