package com.crm.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 实体层约定校验（083-engineering-consolidation，FR-G26）。
 *
 * <p><b>这个测试防的是什么</b>：{@code MybatisPlusConfig} 用 {@code strictInsertFill} 自动填充 {@code createdBy}，而
 * {@code strictInsertFill} **只填充声明了 {@code @TableField(fill = FieldFill.INSERT)} 的字段，没有标注就静默跳过**。
 * 也就是说，漏标注不会报错、不会告警，只会在某张表的某一行上少写一个操作人——而审计与合规导出都以该列为依据。
 *
 * <p>40 个实体是靠一次脚本批量补标注的，这类改动最典型的风险就是"漏了一个"。故此处把该约定变成**可执行的断言**： 新增实体时如果声明了 {@code createdBy}
 * 却忘了标注，本测试立刻失败，而不是等到某次合规导出缺了操作人。
 *
 * <p><b>与集成测试的分工</b>（章程原则四：测试金字塔）：本类只验证"实体声明齐不齐"，是纯反射、无 Spring 上下文； "填充真的落到了数据库列上"由 {@code
 * PerformanceRegressionIT.createdByIsFilledOnInsert} 端到端验证。两者都必要——
 * 只有集成测试时，漏标注的实体若恰好没被那个用例覆盖就查不出来；只有本类时，标注齐了但 handler 没接上同样查不出来。
 */
class CreatedByFillAnnotationTest {

  /** 当前实体包下的类数量下限：防止扫描失败（路径变了/打包进 jar）后退化成"零个实体 → 全部通过"的假绿。 */
  private static final int MIN_EXPECTED_ENTITIES = 30;

  @Test
  @DisplayName("083：凡声明了 createdBy 的实体，都必须带 @TableField(fill = INSERT)")
  void everyCreatedByFieldDeclaresInsertFill() throws Exception {
    List<Class<?>> entities = entityClasses();
    assertThat(entities)
        .as("实体扫描结果异常偏少，说明扫描本身失效了（否则下面的断言会因没有实体而空转通过）")
        .hasSizeGreaterThanOrEqualTo(MIN_EXPECTED_ENTITIES);

    List<String> violations = new ArrayList<>();
    int inspected = 0;
    for (Class<?> entity : entities) {
      for (Field field : entity.getDeclaredFields()) {
        if (!"createdBy".equals(field.getName())) {
          continue;
        }
        inspected++;
        TableField annotation = field.getAnnotation(TableField.class);
        if (annotation == null || annotation.fill() != FieldFill.INSERT) {
          violations.add(entity.getSimpleName());
        }
      }
    }

    assertThat(inspected).as("一个 createdBy 字段都没扫到，说明扫描到的类不对（这条断言防止本测试空转通过）").isGreaterThan(0);
    assertThat(violations)
        .as("以下实体声明了 createdBy 却没有 @TableField(fill = FieldFill.INSERT)，新增时操作人会被静默留空")
        .isEmpty();
  }

  /**
   * 列出 {@code com.crm.entity} 包下已编译的类（测试运行时 target/classes 以目录形式在类路径上）。
   *
   * <p><b>为什么用 BaseEntity.class 定位目录，而不是直接查包名</b>：本测试类自己也在这个包里，而 {@code target/test-classes}
   * 在测试类路径上**先于** {@code target/classes}，故 {@code getResource("com/crm/entity")}
   * 会命中测试输出目录——那里只有本测试一个类， 扫描结果会退化成"零个实体"，而零个实体恰好让断言全部通过（正是本 spec 反复处理的假绿）。 用只存在于主代码里的 {@code
   * BaseEntity.class} 定位，才能拿到真正的实体目录。
   */
  private List<Class<?>> entityClasses() throws Exception {
    URL anchor = getClass().getClassLoader().getResource("com/crm/entity/BaseEntity.class");
    assertThat(anchor).as("类路径上找不到 com/crm/entity/BaseEntity.class").isNotNull();
    assertThat(anchor.getProtocol()).as("需要目录形式的类路径才能扫描").isEqualTo("file");

    File[] files = new File(anchor.toURI()).getParentFile().listFiles();
    assertThat(files).isNotNull();

    List<Class<?>> classes = new ArrayList<>();
    for (File file : files) {
      String name = file.getName();
      // 跳过匿名/内部类（Outer$1.class）
      if (!name.endsWith(".class") || name.contains("$")) {
        continue;
      }
      classes.add(
          Class.forName("com.crm.entity." + name.substring(0, name.length() - ".class".length())));
    }
    return classes;
  }
}
