package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.CustomField;
import com.crm.entity.CustomFieldValue;
import com.crm.repository.CustomFieldMapper;
import com.crm.repository.CustomFieldValueMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CustomFieldFilterSupport 单元测试（016 FR-S03 / T042）：cf_ 参数解析 + 实体 id 匹配。 */
class CustomFieldFilterSupportTest {

  private CustomFieldMapper fieldMapper;
  private CustomFieldValueMapper valueMapper;
  private CustomFieldFilterSupport support;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CustomField.class);
    TableInfoHelper.initTableInfo(assistant, CustomFieldValue.class);
  }

  @BeforeEach
  void setUp() {
    fieldMapper = mock(CustomFieldMapper.class);
    valueMapper = mock(CustomFieldValueMapper.class);
    support = new CustomFieldFilterSupport(fieldMapper, valueMapper);
  }

  private CustomField field(Long id, String entityType, String fieldType) {
    CustomField f = new CustomField();
    f.setId(id);
    f.setEntityType(entityType);
    f.setFieldType(fieldType);
    return f;
  }

  private CustomFieldValue value(
      Long fieldId, String entityType, Long entityId, String fieldValue) {
    CustomFieldValue v = new CustomFieldValue();
    v.setFieldId(fieldId);
    v.setEntityType(entityType);
    v.setEntityId(entityId);
    v.setFieldValue(fieldValue);
    return v;
  }

  @Test
  @DisplayName("parseFilters：提取 cf_ 参数，忽略空值/非法 id/非 cf_ 前缀")
  void parseFilters() {
    Map<String, String> params =
        Map.of(
            "cf_1", "高预算",
            "cf_2", "  ",
            "cf_abc", "x",
            "name", "张三");

    Map<Long, String> result = support.parseFilters(params);

    assertThat(result).containsExactly(Map.entry(1L, "高预算"));
  }

  @Test
  @DisplayName("parseFilters：null 参数返回空 map")
  void parseFiltersNull() {
    assertThat(support.parseFilters(null)).isEmpty();
  }

  @Test
  @DisplayName("matchEntityIds：无筛选条件返回 null（不过滤）")
  void matchNoFiltersReturnsNull() {
    assertThat(support.matchEntityIds("LEAD", null)).isNull();
    assertThat(support.matchEntityIds("LEAD", Map.of())).isNull();
  }

  @Test
  @DisplayName("matchEntityIds：SELECT 类型精确匹配")
  void matchSelectExact() {
    when(fieldMapper.selectBatchIds(any())).thenReturn(List.of(field(1L, "LEAD", "SELECT")));
    when(valueMapper.selectList(any()))
        .thenReturn(List.of(value(1L, "LEAD", 10L, "大"), value(1L, "LEAD", 11L, "大")));

    List<Long> ids = support.matchEntityIds("LEAD", Map.of(1L, "大"));

    assertThat(ids).containsExactlyInAnyOrder(10L, 11L);
  }

  @Test
  @DisplayName("matchEntityIds：文本类型 LIKE 匹配")
  void matchTextLike() {
    when(fieldMapper.selectBatchIds(any())).thenReturn(List.of(field(1L, "LEAD", "TEXT")));
    when(valueMapper.selectList(any())).thenReturn(List.of(value(1L, "LEAD", 20L, "预算充足")));

    List<Long> ids = support.matchEntityIds("LEAD", Map.of(1L, "预算"));

    assertThat(ids).containsExactly(20L);
  }

  @Test
  @DisplayName("matchEntityIds：多条件取交集")
  void matchMultipleConditionsIntersect() {
    when(fieldMapper.selectBatchIds(any()))
        .thenReturn(List.of(field(1L, "LEAD", "SELECT"), field(2L, "LEAD", "TEXT")));
    when(valueMapper.selectList(any()))
        .thenReturn(List.of(value(1L, "LEAD", 10L, "大"), value(1L, "LEAD", 11L, "大")))
        .thenReturn(List.of(value(2L, "LEAD", 10L, "预算高"), value(2L, "LEAD", 12L, "预算高")));

    List<Long> ids = support.matchEntityIds("LEAD", Map.of(1L, "大", 2L, "预算"));

    assertThat(ids).containsExactly(10L);
  }

  @Test
  @DisplayName("matchEntityIds：字段不存在或实体类型不匹配时跳过该条件")
  void matchSkipsUnknownOrMismatchedField() {
    // 字段 1 属于 CUSTOMER 而非 LEAD；字段 999 不存在
    when(fieldMapper.selectBatchIds(any())).thenReturn(List.of(field(1L, "CUSTOMER", "TEXT")));
    when(valueMapper.selectList(any())).thenReturn(List.of(value(1L, "LEAD", 10L, "任意")));

    List<Long> ids = support.matchEntityIds("LEAD", Map.of(1L, "任意"));

    // 唯一条件被跳过 → 无命中 → 空列表
    assertThat(ids).isEmpty();
  }

  @Test
  @DisplayName("matchEntityIds：某条件无命中时短路返回空列表")
  void matchEmptyReturnsEmpty() {
    when(fieldMapper.selectBatchIds(any()))
        .thenReturn(List.of(field(1L, "LEAD", "TEXT"), field(2L, "LEAD", "TEXT")));
    when(valueMapper.selectList(any()))
        .thenReturn(List.of(value(1L, "LEAD", 10L, "预算高")))
        .thenReturn(List.of());

    List<Long> ids = support.matchEntityIds("LEAD", Map.of(1L, "预算", 2L, "不存在"));

    assertThat(ids).isEmpty();
  }
}
