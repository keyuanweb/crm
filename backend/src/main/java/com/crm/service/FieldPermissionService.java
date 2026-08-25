package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.field.FieldPermissionRequest;
import com.crm.dto.field.FieldPermissionResponse;
import com.crm.dto.field.FieldPermissionView;
import com.crm.entity.FieldPermission;
import com.crm.repository.FieldPermissionMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 字段级权限服务（056，FR-F01~F08）：配置 CRUD/权限计算/保存校验。 */
@Service
public class FieldPermissionService {

  public static final String PERM_HIDDEN = "HIDDEN";
  public static final String PERM_READ_ONLY = "READ_ONLY";
  public static final String PERM_EDITABLE = "EDITABLE";

  private final FieldPermissionMapper permissionMapper;

  public FieldPermissionService(FieldPermissionMapper permissionMapper) {
    this.permissionMapper = permissionMapper;
  }

  // ===== 配置 CRUD =====

  public PageResult<FieldPermissionResponse> page(
      String roleCode, String entityType, long page, long pageSize) {
    LambdaQueryWrapper<FieldPermission> qw = new LambdaQueryWrapper<>();
    if (roleCode != null && !roleCode.isBlank()) {
      qw.eq(FieldPermission::getRoleCode, roleCode.trim());
    }
    if (entityType != null && !entityType.isBlank()) {
      qw.eq(FieldPermission::getEntityType, entityType.trim());
    }
    qw.orderByDesc(FieldPermission::getId);
    Page<FieldPermission> p = permissionMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  /** 覆盖式 upsert（角色+实体+字段 唯一）。 */
  @Transactional
  public FieldPermissionResponse upsert(FieldPermissionRequest req) {
    FieldPermission existing =
        permissionMapper.selectOne(
            new LambdaQueryWrapper<FieldPermission>()
                .eq(FieldPermission::getRoleCode, req.getRoleCode().trim())
                .eq(FieldPermission::getEntityType, req.getEntityType().trim())
                .eq(FieldPermission::getFieldId, req.getFieldId())
                .last("LIMIT 1"));
    if (existing == null) {
      FieldPermission fp = new FieldPermission();
      fp.setRoleCode(req.getRoleCode().trim());
      fp.setEntityType(req.getEntityType().trim());
      fp.setFieldId(req.getFieldId());
      fp.setPermission(req.getPermission());
      fp.setCreatedBy(SecurityUtil.currentUserId());
      permissionMapper.insert(fp);
      return toResponse(permissionMapper.selectById(fp.getId()));
    }
    existing.setPermission(req.getPermission());
    permissionMapper.updateById(existing);
    return toResponse(permissionMapper.selectById(existing.getId()));
  }

  @Transactional
  public void delete(Long id) {
    permissionMapper.deleteById(id);
  }

  // ===== 权限计算 =====

  /** 某角色对字段的权限（ADMIN 恒 EDITABLE；未配置默认 EDITABLE）。 */
  public String permissionFor(String roleCode, String entityType, Long fieldId) {
    if ("ADMIN".equals(roleCode)) {
      return PERM_EDITABLE;
    }
    FieldPermission fp =
        permissionMapper.selectOne(
            new LambdaQueryWrapper<FieldPermission>()
                .eq(FieldPermission::getRoleCode, roleCode)
                .eq(FieldPermission::getEntityType, entityType)
                .eq(FieldPermission::getFieldId, fieldId)
                .last("LIMIT 1"));
    return fp == null ? PERM_EDITABLE : fp.getPermission();
  }

  /** 批量权限视图（字段列表用）：fieldId → permission。 */
  public Map<Long, String> permissionsForRole(String roleCode, String entityType) {
    if ("ADMIN".equals(roleCode)) {
      return Map.of();
    }
    List<FieldPermission> list =
        permissionMapper.selectList(
            new LambdaQueryWrapper<FieldPermission>()
                .eq(FieldPermission::getRoleCode, roleCode)
                .eq(FieldPermission::getEntityType, entityType));
    return list.stream()
        .collect(Collectors.toMap(FieldPermission::getFieldId, FieldPermission::getPermission));
  }

  public FieldPermissionView viewFor(String roleCode, String entityType, Long fieldId) {
    String permission = permissionFor(roleCode, entityType, fieldId);
    return FieldPermissionView.of(permission);
  }

  // ===== 保存校验（服务端强制） =====

  /** 保存自定义字段值前校验权限： - HIDDEN 字段提交 → 422 FIELD_HIDDEN - READ_ONLY 字段修改已有值 → 422 FIELD_READ_ONLY */
  public void validateWrite(
      String roleCode,
      String entityType,
      List<? extends com.crm.dto.field.FieldValueLike> values,
      Map<Long, String> existingValues) {
    if ("ADMIN".equals(roleCode)) {
      return;
    }
    if (values == null) {
      return;
    }
    for (com.crm.dto.field.FieldValueLike v : values) {
      if (v.getFieldId() == null) {
        continue;
      }
      String permission = permissionFor(roleCode, entityType, v.getFieldId());
      if (PERM_HIDDEN.equals(permission)) {
        throw new BusinessException(ErrorCode.FIELD_HIDDEN);
      }
      if (PERM_READ_ONLY.equals(permission)) {
        String existing = existingValues.get(v.getFieldId());
        boolean changed = existing == null || !existing.equals(v.getValue());
        if (changed) {
          throw new BusinessException(ErrorCode.FIELD_READ_ONLY);
        }
      }
    }
  }

  // ===== 装配 =====

  private FieldPermissionResponse toResponse(FieldPermission fp) {
    FieldPermissionResponse resp = new FieldPermissionResponse();
    resp.setId(fp.getId());
    resp.setRoleCode(fp.getRoleCode());
    resp.setEntityType(fp.getEntityType());
    resp.setFieldId(fp.getFieldId());
    resp.setPermission(fp.getPermission());
    resp.setCreatedAt(fp.getCreatedAt());
    return resp;
  }
}
