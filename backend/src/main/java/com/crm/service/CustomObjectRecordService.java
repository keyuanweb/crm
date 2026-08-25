package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.customobject.CustomObjectRequest;
import com.crm.dto.customobject.RecordRequest;
import com.crm.dto.customobject.RecordResponse;
import com.crm.entity.CustomObject;
import com.crm.entity.CustomObjectRecord;
import com.crm.repository.CustomObjectRecordMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 自定义对象记录服务（059，FR-C04~C07）：记录 CRUD/必填校验/搜索/停用拦截。 */
@Service
public class CustomObjectRecordService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CustomObjectRecordMapper recordMapper;
  private final CustomObjectService objectService;

  public CustomObjectRecordService(
      CustomObjectRecordMapper recordMapper, CustomObjectService objectService) {
    this.recordMapper = recordMapper;
    this.objectService = objectService;
  }

  public PageResult<RecordResponse> page(Long objectId, String keyword, long page, long pageSize) {
    objectService.require(objectId);
    LambdaQueryWrapper<CustomObjectRecord> qw =
        new LambdaQueryWrapper<CustomObjectRecord>().eq(CustomObjectRecord::getObjectId, objectId);
    if (StringUtils.hasText(keyword)) {
      qw.like(CustomObjectRecord::getRecordValues, keyword.trim());
    }
    qw.orderByDesc(CustomObjectRecord::getId);
    Page<CustomObjectRecord> p = recordMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  public RecordResponse detail(Long objectId, Long recordId) {
    CustomObjectRecord record = requireRecord(objectId, recordId);
    return toResponse(record);
  }

  @Transactional
  public RecordResponse create(Long objectId, RecordRequest req) {
    CustomObject obj = objectService.require(objectId);
    if (obj.getEnabled() == null || obj.getEnabled() != 1) {
      throw new BusinessException(ErrorCode.OBJECT_DISABLED);
    }
    Map<String, Object> values = normalizeValues(req.getValues());
    validateRequired(obj, values);
    CustomObjectRecord record = new CustomObjectRecord();
    record.setObjectId(objectId);
    record.setRecordValues(writeJson(values));
    record.setCreatedBy(SecurityUtil.currentUserId());
    record.setCreatedAt(LocalDateTime.now());
    recordMapper.insert(record);
    return toResponse(record);
  }

  @Transactional
  public RecordResponse update(Long objectId, Long recordId, RecordRequest req) {
    CustomObjectRecord record = requireRecord(objectId, recordId);
    CustomObject obj = objectService.require(objectId);
    Map<String, Object> values = normalizeValues(req.getValues());
    validateRequired(obj, values);
    record.setRecordValues(writeJson(values));
    recordMapper.updateById(record);
    return toResponse(recordMapper.selectById(recordId));
  }

  @Transactional
  public void delete(Long objectId, Long recordId) {
    recordMapper.deleteById(requireRecord(objectId, recordId).getId());
  }

  private void validateRequired(CustomObject obj, Map<String, Object> values) {
    for (CustomObjectRequest.FieldDef f : objectService.fieldsOf(obj)) {
      if (Boolean.TRUE.equals(f.getRequired())
          && (values.get(f.getField()) == null
              || String.valueOf(values.get(f.getField())).isBlank())) {
        throw new BusinessException(ErrorCode.OBJECT_FIELD_REQUIRED);
      }
    }
  }

  private Map<String, Object> normalizeValues(Map<String, Object> raw) {
    return raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
  }

  private CustomObjectRecord requireRecord(Long objectId, Long recordId) {
    CustomObjectRecord record = recordMapper.selectById(recordId);
    if (record == null || !objectId.equals(record.getObjectId())) {
      throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND);
    }
    return record;
  }

  private String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.OBJECT_FIELD_INVALID);
    }
  }

  private RecordResponse toResponse(CustomObjectRecord record) {
    RecordResponse resp = new RecordResponse();
    resp.setId(record.getId());
    resp.setObjectId(record.getObjectId());
    resp.setValues(readValues(record.getRecordValues()));
    resp.setCreatedBy(record.getCreatedBy());
    resp.setCreatedAt(record.getCreatedAt());
    return resp;
  }

  private Map<String, Object> readValues(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      return Map.of();
    }
  }
}
