package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.field.AvailableFieldResponse;
import com.crm.dto.field.FieldPermissionRequest;
import com.crm.dto.field.FieldPermissionResponse;
import com.crm.dto.field.FieldPermissionView;
import com.crm.entity.CustomField;
import com.crm.entity.FieldPermission;
import com.crm.repository.CustomFieldMapper;
import com.crm.repository.FieldPermissionMapper;
import com.crm.security.SecurityUtil;
import com.crm.support.BuiltinField;
import com.crm.support.BuiltinFieldRegistry;
import java.util.ArrayList;
import java.util.Collection;
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
  private final CustomFieldMapper customFieldMapper;

  /**
   * ⚠️ {@code CustomFieldMapper} 是**只读字段定义**用的，不能换成 {@code CustomFieldService}：后者已经依赖本类
   * （自定义字段值的权限计算），反向注入会构成构造器环 ⇒ Spring Boot 2.6+ 默认禁止循环引用、应用启动即失败。 本类对它的两处使用（可配字段表、{@code
   * fieldName}）都只需要一张表的一次查询，不值得为它建新类。
   */
  public FieldPermissionService(
      FieldPermissionMapper permissionMapper,
      BuiltinFieldRegistry builtinFieldRegistry,
      CustomFieldMapper customFieldMapper) {
    this.permissionMapper = permissionMapper;
    this.builtinFieldRegistry = builtinFieldRegistry;
    this.customFieldMapper = customFieldMapper;
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
    return PageResult.of(toResponses(p.getRecords()), p.getTotal(), page, pageSize);
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

  /**
   * 102 配置面：某实体在本系统里**全部可配字段**（内置 + 自定义），供字段权限页的「字段」下拉。
   *
   * <p><b>为什么读 {@link CustomFieldMapper} 而不是 {@code CustomFieldService}</b>：后者已依赖本类，见构造器上的注释。
   *
   * <p><b>与 {@code CustomFieldService.listByEntity} 的两点刻意不同</b>：
   *
   * <ol>
   *   <li><b>不按调用者角色的 HIDDEN 过滤自定义字段</b>。配置面要配的正是「被隐藏的那些」——按角色过滤会让已配成 HIDDEN
   *       的字段从下拉里消失，配好了却再也改不回来（而今天唯一能进本端点的角色是 ADMIN，过滤与否看不出差别， 所以这条不写下来就会被后人当成冗余代码删掉）。
   *   <li><b>不带 permission 标记</b>：那是编辑实体值时要看的，不是配权限时要看的。
   * </ol>
   *
   * <p><b>结果为空 ⇒ 422 而不是空表</b>（契约 §3.1）：空表让「这个实体没有可配字段」与「实体名拼错了」不可区分。 故「未知
   * entityType」在本方法里**不需要**另立一份已知实体清单——注册表认不出、自定义字段也没有的实体， 聚合出来本来就是空的。少一份清单就少一处会与前端 {@code
   * ENUM_KEYS.fieldEntity} 漂移的第二真相。
   */
  public PageResult<AvailableFieldResponse> availableFields(
      String entityType, long page, long pageSize) {
    String type = entityType == null ? "" : entityType.trim();
    List<AvailableFieldResponse> all = new ArrayList<>();
    for (BuiltinField f : builtinFieldRegistry.fieldsOf(type)) {
      all.add(AvailableFieldResponse.builtin(f.fieldKey(), f.label()));
    }
    for (CustomField f : enabledCustomFields(type)) {
      all.add(AvailableFieldResponse.custom(f.getId(), f.getName()));
    }
    if (all.isEmpty()) {
      throw new BusinessException(
          ErrorCode.FIELD_PERMISSION_INVALID, "该实体没有可配置权限的字段：" + entityType);
    }
    return PageResult.of(slice(all, page, pageSize), all.size(), page, pageSize);
  }

  /** 该实体的启用中自定义字段（与 {@code listByEntity} 同一筛选与排序，但不做权限装饰）。 */
  private List<CustomField> enabledCustomFields(String entityType) {
    return customFieldMapper.selectList(
        new LambdaQueryWrapper<CustomField>()
            .eq(CustomField::getEntityType, entityType)
            .eq(CustomField::getEnabled, 1)
            .orderByAsc(CustomField::getSortOrder)
            .orderByAsc(CustomField::getId));
  }

  /**
   * 内存分页（内置在前、自定义在后）。判据只有一条：**不得因越界的 page/pageSize 抛异常**——下拉只需要一页到底， 真正的风险是有人传了 {@code
   * pageSize=Long.MAX_VALUE} 让 {@code (page-1)*size} 溢出成负数，再被 {@code subList} 拒掉（配置面报 500
   * 而看不出原因）。故先把两个乘数都夹到列表长度以内。
   */
  private static <T> List<T> slice(List<T> all, long page, long pageSize) {
    long from = Math.max(1, page);
    long size = (pageSize <= 0 || pageSize > all.size()) ? all.size() : pageSize;
    if (from > all.size()) {
      return List.of(); // 每页至少一条 ⇒ page 超过总条数时必然为空
    }
    int start = (int) ((from - 1) * size);
    return start >= all.size()
        ? List.of()
        : all.subList(start, (int) Math.min(all.size(), start + size));
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
    return toResponse(fp, namesFor(List.of(fp)));
  }

  private List<FieldPermissionResponse> toResponses(List<FieldPermission> rows) {
    Map<Long, String> names = namesFor(rows);
    return rows.stream().map(fp -> toResponse(fp, names)).toList();
  }

  /** 自定义字段名批量取——按行查库会让一页 20 条配置发 20 次查询。内置行没有 {@code fieldId}，不进这次查询。 */
  private Map<Long, String> namesFor(Collection<FieldPermission> rows) {
    List<Long> ids =
        rows.stream().map(FieldPermission::getFieldId).filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return customFieldMapper.selectBatchIds(ids).stream()
        .collect(Collectors.toMap(CustomField::getId, CustomField::getName, (a, b) -> a));
  }

  /**
   * 102：{@code fieldName} 由**声明**变为**真正赋值**（056 的契约里它一直有，实现里一直是 null）。
   *
   * <p>内置字段名取注册表的 {@code label}，自定义字段名取字段定义——两种字段名在同一个响应里同一个键， 配置列表不必再回头查一次字段表。找不到时留 null
   * 而不是编一个占位串：字段定义可能已被删除， 而「这个配置指向一个已不存在的字段」正是调用方需要看见的事实。
   */
  private FieldPermissionResponse toResponse(FieldPermission fp, Map<Long, String> customNames) {
    FieldPermissionResponse resp = new FieldPermissionResponse();
    resp.setId(fp.getId());
    resp.setRoleCode(fp.getRoleCode());
    resp.setEntityType(fp.getEntityType());
    resp.setFieldId(fp.getFieldId());
    resp.setFieldKey(fp.getFieldKey());
    resp.setFieldName(fieldName(fp, customNames));
    resp.setPermission(fp.getPermission());
    resp.setCreatedAt(fp.getCreatedAt());
    return resp;
  }

  private String fieldName(FieldPermission fp, Map<Long, String> customNames) {
    if (fp.getFieldKey() != null) {
      BuiltinField builtin = builtinFieldRegistry.find(fp.getEntityType(), fp.getFieldKey());
      return builtin == null ? null : builtin.label();
    }
    return fp.getFieldId() == null ? null : customNames.get(fp.getFieldId());
  }
}
