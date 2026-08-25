package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.customobject.CustomObjectRequest;
import com.crm.dto.customobject.CustomObjectResponse;
import com.crm.entity.CustomObject;
import com.crm.repository.CustomObjectMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 自定义对象服务（059，FR-C01~C07）：对象 CRUD/字段校验。 */
@Service
public class CustomObjectService {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> FIELD_TYPES = Set.of("TEXT", "NUMBER", "DATE", "SELECT");

  private final CustomObjectMapper objectMapper;

  public CustomObjectService(CustomObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public PageResult<CustomObjectResponse> page(String keyword, long page, long pageSize) {
    LambdaQueryWrapper<CustomObject> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.and(
          w ->
              w.like(CustomObject::getName, keyword.trim())
                  .or()
                  .like(CustomObject::getCode, keyword.trim()));
    }
    qw.orderByDesc(CustomObject::getId);
    Page<CustomObject> p = objectMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public CustomObjectResponse create(CustomObjectRequest req) {
    validateFields(req.getFields());
    Long exists =
        objectMapper.selectCount(
            new LambdaQueryWrapper<CustomObject>().eq(CustomObject::getCode, req.getCode()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.OBJECT_CODE_DUPLICATE);
    }
    CustomObject obj = new CustomObject();
    obj.setName(req.getName().trim());
    obj.setCode(req.getCode().trim());
    obj.setFields(writeJson(req.getFields()));
    obj.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    obj.setCreatedBy(SecurityUtil.currentUserId());
    objectMapper.insert(obj);
    return toResponse(objectMapper.selectById(obj.getId()));
  }

  @Transactional
  public CustomObjectResponse update(Long id, CustomObjectRequest req) {
    CustomObject existing = require(id);
    validateFields(req.getFields());
    existing.setName(req.getName().trim());
    existing.setCode(req.getCode().trim());
    existing.setFields(writeJson(req.getFields()));
    existing.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    existing.setVersion(req.getVersion());
    objectMapper.updateById(existing);
    return toResponse(objectMapper.selectById(id));
  }

  @Transactional
  public CustomObjectResponse toggle(Long id) {
    CustomObject existing = require(id);
    existing.setEnabled(existing.getEnabled() != null && existing.getEnabled() == 1 ? 0 : 1);
    objectMapper.updateById(existing);
    return toResponse(objectMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    objectMapper.deleteById(id);
  }

  /** 对象字段集解析。 */
  public List<CustomObjectRequest.FieldDef> fieldsOf(CustomObject obj) {
    if (obj.getFields() == null || obj.getFields().isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(obj.getFields(), new TypeReference<List<CustomObjectRequest.FieldDef>>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }

  public CustomObject require(Long id) {
    CustomObject obj = objectMapper.selectById(id);
    if (obj == null) {
      throw new BusinessException(ErrorCode.OBJECT_NOT_FOUND);
    }
    return obj;
  }

  private void validateFields(List<CustomObjectRequest.FieldDef> fields) {
    if (fields == null || fields.isEmpty()) {
      throw new BusinessException(ErrorCode.OBJECT_FIELD_INVALID);
    }
    for (CustomObjectRequest.FieldDef f : fields) {
      if (f.getField() == null || f.getField().isBlank() || f.getLabel() == null || f.getLabel().isBlank()) {
        throw new BusinessException(ErrorCode.OBJECT_FIELD_INVALID);
      }
      if (f.getType() == null || !FIELD_TYPES.contains(f.getType())) {
        throw new BusinessException(ErrorCode.OBJECT_FIELD_INVALID);
      }
    }
  }

  private String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.OBJECT_FIELD_INVALID);
    }
  }

  private CustomObjectResponse toResponse(CustomObject obj) {
    CustomObjectResponse resp = new CustomObjectResponse();
    resp.setId(obj.getId());
    resp.setName(obj.getName());
    resp.setCode(obj.getCode());
    resp.setFields(fieldsOf(obj));
    resp.setEnabled(obj.getEnabled() != null && obj.getEnabled() == 1);
    resp.setVersion(obj.getVersion());
    resp.setCreatedAt(obj.getCreatedAt());
    return resp;
  }
}
