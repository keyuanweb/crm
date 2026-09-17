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
import com.crm.support.BuiltinFieldRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字段级权限服务（056，FR-F01~F08）：配置 CRUD/权限计算/保存校验。
 *
 * <p><b>102 起同一张表也承载内置字段</b>：自定义字段行按 {@code field_id} 定位，内置字段行按 {@code field_key}
 * 定位，两者二选一。故本类里每一条自定义字段路径都要问一句「内置行会不会混进来」，反之亦然——见 {@link #permissionsForRole} 与 {@link
 * #permissionForKey} 的注释。
 */
@Service
public class FieldPermissionService {

  public static final String PERM_HIDDEN = "HIDDEN";
  public static final String PERM_READ_ONLY = "READ_ONLY";
  public static final String PERM_EDITABLE = "EDITABLE";

  private final FieldPermissionMapper permissionMapper;
  private final BuiltinFieldRegistry builtinFieldRegistry;

  public FieldPermissionService(
      FieldPermissionMapper permissionMapper, BuiltinFieldRegistry builtinFieldRegistry) {
    this.permissionMapper = permissionMapper;
    this.builtinFieldRegistry = builtinFieldRegistry;
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

  /**
   * 覆盖式 upsert（角色+实体+字段 唯一）；字段标识为 {@code fieldId}（自定义）或 {@code fieldKey}（内置）**恰好一个**。
   *
   * <p><b>为什么「二选一」必须在这里强制</b>：库里**没有**「恰好一列非空」的约束（H2 与 MySQL 实测都接受两列皆空的行， 见 V91 的注释），所以这是唯一的一道门。违反
   * ⇒ 422 {@code FIELD_PERMISSION_INVALID}（该码定义于 056、本批首次真正抛出； 文案按实际情况给出，不复用码上那句「权限值不合法」）。
   */
  @Transactional
  public FieldPermissionResponse upsert(FieldPermissionRequest req) {
    String entityType = req.getEntityType().trim();
    String fieldKey = req.getFieldKey() == null ? null : req.getFieldKey().trim();
    boolean builtin = fieldKey != null && !fieldKey.isEmpty();
    if (builtin == (req.getFieldId() != null)) {
      throw new BusinessException(
          ErrorCode.FIELD_PERMISSION_INVALID, "fieldId 与 fieldKey 必须且只能给一个");
    }
    if (builtin && builtinFieldRegistry.find(entityType, fieldKey) == null) {
      throw new BusinessException(
          ErrorCode.FIELD_PERMISSION_INVALID, "该实体下没有可配权限的内置字段：" + fieldKey);
    }
    FieldPermission existing =
        permissionMapper.selectOne(
            new LambdaQueryWrapper<FieldPermission>()
                .eq(FieldPermission::getRoleCode, req.getRoleCode().trim())
                .eq(FieldPermission::getEntityType, entityType)
                // ⚠️ 条件式 eq，不能写成「按分支只调其中一个」之外的样子：`.eq(getFieldId, null)`
                // 生成的是 `field_id = NULL`，永不匹配 ⇒ 第二次保存会再 INSERT 并撞唯一键（实测）。
                .eq(builtin, FieldPermission::getFieldKey, fieldKey)
                .eq(!builtin, FieldPermission::getFieldId, req.getFieldId())
                .last("LIMIT 1"));
    if (existing == null) {
      FieldPermission fp = new FieldPermission();
      fp.setRoleCode(req.getRoleCode().trim());
      fp.setEntityType(entityType);
      fp.setFieldId(builtin ? null : req.getFieldId());
      fp.setFieldKey(builtin ? fieldKey : null);
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

  /**
   * 某角色对**自定义字段**的权限（ADMIN 恒 EDITABLE；未配置默认 EDITABLE）。
   *
   * <p>⚠️ <b>只认 {@code field_id}</b>：传 null（或拿内置字段名来查）会生成 {@code field_id = NULL}，永不匹配 ⇒ 回落 {@code
   * EDITABLE}（**fail-open**）。内置字段一律走 {@link #permissionForKey}（102）。
   */
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
                .eq(FieldPermission::getEntityType, entityType)
                // ⚠️ 这一句是**防炸**不是卫生：内置行的 field_id 为 NULL，本实体若配了两条内置字段
                // ⇒ toMap 撞上重复 null 键 ⇒ IllegalStateException（500）。只配一条时能跑，
                // 正是这种「只在第二种配置下才炸」的形态最容易被漏掉（实测见 102 的 research.md）。
                .isNotNull(FieldPermission::getFieldId));
    return list.stream()
        .collect(Collectors.toMap(FieldPermission::getFieldId, FieldPermission::getPermission));
  }

  /**
   * 某角色对**内置字段**的权限（ADMIN 恒 EDITABLE；未配置默认 EDITABLE）。
   *
   * <p>用 {@code Map} 的显式循环而不是 {@code Collectors.toMap}：内置行的键（{@code field_key}）可能为 NULL
   * （自定义行混在同一实体下），而 {@code toMap} 对 null 键/重复键的容错正是上面那条注释要躲的东西。
   */
  public Map<String, String> builtinPermissionsForRole(String roleCode, String entityType) {
    if ("ADMIN".equals(roleCode)) {
      return Map.of();
    }
    List<FieldPermission> list =
        permissionMapper.selectList(
            new LambdaQueryWrapper<FieldPermission>()
                .eq(FieldPermission::getRoleCode, roleCode)
                .eq(FieldPermission::getEntityType, entityType)
                .isNotNull(FieldPermission::getFieldKey));
    Map<String, String> byKey = new LinkedHashMap<>();
    for (FieldPermission fp : list) {
      byKey.put(fp.getFieldKey(), fp.getPermission());
    }
    return byKey;
  }

  /**
   * 某角色对单个**内置字段**的权限（102）。
   *
   * <p>⚠️ 不要退化成 {@code permissionFor(role, entity, null)}——那条按 {@code field_id} 查，传 null 时
   * fail-open 成 EDITABLE（见其 javadoc）。{@code fieldKey} 由调用方从 {@link BuiltinFieldRegistry} 取，不会是
   * null。
   */
  public String permissionForKey(String roleCode, String entityType, String fieldKey) {
    if ("ADMIN".equals(roleCode)) {
      return PERM_EDITABLE;
    }
    FieldPermission fp =
        permissionMapper.selectOne(
            new LambdaQueryWrapper<FieldPermission>()
                .eq(FieldPermission::getRoleCode, roleCode)
                .eq(FieldPermission::getEntityType, entityType)
                .eq(FieldPermission::getFieldKey, fieldKey)
                .last("LIMIT 1"));
    return fp == null ? PERM_EDITABLE : fp.getPermission();
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

  /**
   * 保存**内置字段**值前校验权限（102）：HIDDEN 提交 ⇒ 422 {@code FIELD_HIDDEN}；READ_ONLY 提交了与库中**不同**的值 ⇒ 422
   * {@code FIELD_READ_ONLY}（与自定义字段同码同语义）。
   *
   * <p><b>与 {@link #validateWrite} 的两处刻意差别</b>（照抄那条会造出两个 bug）：
   *
   * <ol>
   *   <li><b>只看客户端真正提交了的字段</b>。上一条遍历的是「提交上来的值列表」，本身就不含省略项；内置字段没有这样的列表， {@code submitted} 由请求 DTO
   *       反射而来，**省略项的值也是 null**。若照抄 READ_ONLY 那句 {@code existing == null ||
   *       !existing.equals(value)}，则「省略一个库中有值的只读字段」会被判成 changed ⇒
   *       422——而正确行为是**回补库中原值**（看不见不等于该被删除）。
   *   <li><b>「显式传 null」与「省略」在此不可区分</b>（JSON→DTO 两层都是 null），两者一律按省略处理（回补）。代价是： 客户端**无法**用传 null 把
   *       HIDDEN / READ_ONLY 字段清空——这比「能清空」安全，但它与自定义字段那条 （HIDDEN + 显式 null ⇒ 422）不完全一致，如实登记在 102 的
   *       research.md。
   * </ol>
   *
   * @param submitted 该实体**全部已注册内置字段**的提交值（省略项为 null）
   * @param existing 库中实体上对应字段的现值
   */
  public void validateBuiltinWrite(
      String roleCode,
      String entityType,
      Map<String, Object> submitted,
      Map<String, Object> existing) {
    if ("ADMIN".equals(roleCode)) {
      return;
    }
    if (submitted == null) {
      return;
    }
    for (Map.Entry<String, Object> entry : submitted.entrySet()) {
      Object value = entry.getValue();
      if (value == null) {
        continue; // 省略（或显式 null）⇒ 不构成「修改」，由写侧回补保留库中原值
      }
      String permission = permissionForKey(roleCode, entityType, entry.getKey());
      if (PERM_HIDDEN.equals(permission)) {
        throw new BusinessException(ErrorCode.FIELD_HIDDEN);
      }
      if (PERM_READ_ONLY.equals(permission)
          && !Objects.equals(existing.get(entry.getKey()), value)) {
        throw new BusinessException(ErrorCode.FIELD_READ_ONLY);
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
    resp.setFieldKey(fp.getFieldKey());
    resp.setPermission(fp.getPermission());
    resp.setCreatedAt(fp.getCreatedAt());
    return resp;
  }
}
