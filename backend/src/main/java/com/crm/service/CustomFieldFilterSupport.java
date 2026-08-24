package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.CustomField;
import com.crm.entity.CustomFieldValue;
import com.crm.repository.CustomFieldMapper;
import com.crm.repository.CustomFieldValueMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 自定义字段列表筛选支持（016，FR-S03）： 解析 `cf_<fieldId>=value` 请求参数，并在 custom_field_value 表中匹配满足全部条件的实体 id，
 * 供各实体列表服务以 `id IN (...)` 过滤。
 */
@Component
public class CustomFieldFilterSupport {

  private static final String PARAM_PREFIX = "cf_";

  private final CustomFieldMapper fieldMapper;
  private final CustomFieldValueMapper valueMapper;

  public CustomFieldFilterSupport(
      CustomFieldMapper fieldMapper, CustomFieldValueMapper valueMapper) {
    this.fieldMapper = fieldMapper;
    this.valueMapper = valueMapper;
  }

  /** 从请求参数中提取 `cf_<fieldId>=value` 筛选条件（忽略空值与非法 id）。 */
  public Map<Long, String> parseFilters(Map<String, String> params) {
    Map<Long, String> filters = new HashMap<>();
    if (params == null) {
      return filters;
    }
    params.forEach(
        (key, value) -> {
          if (key.startsWith(PARAM_PREFIX) && value != null && !value.isBlank()) {
            try {
              filters.put(Long.parseLong(key.substring(PARAM_PREFIX.length())), value.trim());
            } catch (NumberFormatException ignored) {
              // 非法 cf_ 参数忽略
            }
          }
        });
    return filters;
  }

  /**
   * 返回同时满足全部筛选条件的实体 id 集合。
   *
   * @return 无筛选条件返回 null（不过滤）；字段不存在或类型不匹配的单个条件被跳过；无命中返回空列表
   */
  public List<Long> matchEntityIds(String entityType, Map<Long, String> filters) {
    if (filters == null || filters.isEmpty()) {
      return null;
    }
    Map<Long, CustomField> defs =
        fieldMapper.selectBatchIds(filters.keySet()).stream()
            .collect(Collectors.toMap(CustomField::getId, Function.identity()));
    List<Long> matched = null;
    for (Map.Entry<Long, String> entry : filters.entrySet()) {
      CustomField def = defs.get(entry.getKey());
      if (def == null || !entityType.equals(def.getEntityType())) {
        continue;
      }
      LambdaQueryWrapper<CustomFieldValue> qw =
          new LambdaQueryWrapper<CustomFieldValue>()
              .eq(CustomFieldValue::getFieldId, entry.getKey())
              .eq(CustomFieldValue::getEntityType, entityType);
      // 下拉类型精确匹配，其余文本 LIKE
      if ("SELECT".equals(def.getFieldType())) {
        qw.eq(CustomFieldValue::getFieldValue, entry.getValue());
      } else {
        qw.like(CustomFieldValue::getFieldValue, entry.getValue());
      }
      List<Long> ids =
          valueMapper.selectList(qw.select(CustomFieldValue::getEntityId)).stream()
              .map(CustomFieldValue::getEntityId)
              .toList();
      if (ids.isEmpty()) {
        return List.of();
      }
      matched = matched == null ? ids : matched.stream().filter(ids::contains).toList();
    }
    return matched == null ? List.of() : matched;
  }
}
