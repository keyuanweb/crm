package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.customfield.CustomFieldRequest;
import com.crm.dto.customfield.CustomFieldResponse;
import com.crm.dto.customfield.CustomFieldValueDTO;
import com.crm.entity.CustomField;
import com.crm.entity.CustomFieldValue;
import com.crm.repository.CustomFieldMapper;
import com.crm.repository.CustomFieldValueMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 自定义字段服务（016，FR-S01~S03）：定义 CRUD/值读写/校验。 */
@Service
public class CustomFieldService {

  private final CustomFieldMapper fieldMapper;
  private final CustomFieldValueMapper valueMapper;
  private final AuditService auditService;
  private final FieldPermissionService fieldPermissionService;

  public CustomFieldService(
      CustomFieldMapper fieldMapper,
      CustomFieldValueMapper valueMapper,
      AuditService auditService,
      FieldPermissionService fieldPermissionService) {
    this.fieldMapper = fieldMapper;
    this.valueMapper = valueMapper;
    this.auditService = auditService;
    this.fieldPermissionService = fieldPermissionService;
  }

  /** 某实体的启用字段定义（按 sort_order 排序，含当前角色权限标记）。 */
  public List<CustomFieldResponse> listByEntity(String entityType) {
    String roleCode = currentRoleCode();
    java.util.Map<Long, String> perms =
        fieldPermissionService.permissionsForRole(roleCode, entityType);
    return fieldMapper
        .selectList(
            new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getEntityType, entityType)
                .eq(CustomField::getEnabled, 1)
                .orderByAsc(CustomField::getSortOrder)
                .orderByAsc(CustomField::getId))
        .stream()
        .map(
            f -> {
              CustomFieldResponse resp = toResponse(f);
              String p = perms.getOrDefault(f.getId(), FieldPermissionService.PERM_EDITABLE);
              resp.setPermission(com.crm.dto.field.FieldPermissionView.of(p));
              return resp;
            })
        .toList();
  }

  private String currentRoleCode() {
    var principal = SecurityUtil.currentPrincipal();
    return principal == null ? "ADMIN" : principal.role();
  }

  /** 该角色在本实体上被隐藏的字段 id（ADMIN 与未配置一律为空集）。 */
  private Set<Long> hiddenFieldIds(String roleCode, String entityType) {
    return fieldPermissionService.permissionsForRole(roleCode, entityType).entrySet().stream()
        .filter(e -> FieldPermissionService.PERM_HIDDEN.equals(e.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /**
   * 该角色在本实体上**受保护**的字段 id = HIDDEN ∪ READ_ONLY（103）。
   *
   * <p>判据写成「不等于 EDITABLE」而非并列枚举两个已知值，与 {@code support/FieldMaskPlanner.protectedKeys} **同口径**（其
   * javadoc 的「往严的一侧倒」）：将来多出一种权限值时，未知值一律按受保护处理，**不会静默放行**一个权限不明的字段被删除。
   *
   * <p>**私有**而非公开 API：目前只有 {@code saveValues} 一个消费者。而 {@code readValues} / {@code readValuesBatch}
   * / {@code dropHidden} **必须保持 HIDDEN-only** —— READ_ONLY 的值就得下发，否则客户端无从「原样回传」，056 的 422
   * 语义随之不可达。将来出现第二消费者再提升。
   */
  private Set<Long> protectedFieldIds(String roleCode, String entityType) {
    return fieldPermissionService.permissionsForRole(roleCode, entityType).entrySet().stream()
        .filter(e -> !FieldPermissionService.PERM_EDITABLE.equals(e.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private List<CustomFieldValueDTO> dropHidden(
      String roleCode, String entityType, List<CustomFieldValueDTO> values) {
    Set<Long> hidden = hiddenFieldIds(roleCode, entityType);
    if (hidden.isEmpty()) {
      return values;
    }
    return values.stream().filter(v -> !hidden.contains(v.getFieldId())).toList();
  }

  public PageResult<CustomFieldResponse> page(String entityType, long page, long pageSize) {
    LambdaQueryWrapper<CustomField> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(entityType)) {
      qw.eq(CustomField::getEntityType, entityType.trim());
    }
    qw.orderByAsc(CustomField::getSortOrder).orderByAsc(CustomField::getId);
    Page<CustomField> p = fieldMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public CustomFieldResponse create(CustomFieldRequest req) {
    validate(req);
    Long exists =
        fieldMapper.selectCount(
            new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getEntityType, req.getEntityType())
                .eq(CustomField::getName, req.getName().trim()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.CUSTOM_FIELD_DUPLICATE);
    }
    CustomField field = new CustomField();
    apply(req, field);
    field.setEnabled(1);
    field.setCreatedBy(SecurityUtil.currentUserId());
    fieldMapper.insert(field);
    auditService.record(
        "CREATE",
        "CUSTOM_FIELD",
        field.getId(),
        "创建自定义字段：" + field.getEntityType() + "." + field.getName());
    return toResponse(field);
  }

  @Transactional
  public CustomFieldResponse update(Long id, CustomFieldRequest req) {
    CustomField existing = require(id);
    validate(req);
    Long dup =
        fieldMapper.selectCount(
            new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getEntityType, existing.getEntityType())
                .eq(CustomField::getName, req.getName().trim())
                .ne(CustomField::getId, id));
    if (dup != null && dup > 0) {
      throw new BusinessException(ErrorCode.CUSTOM_FIELD_DUPLICATE);
    }
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = fieldMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "CUSTOM_FIELD", id, "编辑自定义字段：" + existing.getName());
    return toResponse(fieldMapper.selectById(id));
  }

  /** 删除字段定义并物理清理关联值。 */
  @Transactional
  public void delete(Long id) {
    CustomField field = require(id);
    valueMapper.delete(
        new LambdaQueryWrapper<CustomFieldValue>().eq(CustomFieldValue::getFieldId, id));
    fieldMapper.deleteById(id);
    auditService.record("DELETE", "CUSTOM_FIELD", id, "删除自定义字段：" + field.getName());
  }

  /** 校验：SELECT 必有选项，其余类型选项必须为空。 */
  private void validate(CustomFieldRequest req) {
    boolean select = "SELECT".equals(req.getFieldType());
    if (select && !StringUtils.hasText(req.getOptions())) {
      throw new BusinessException(ErrorCode.CUSTOM_FIELD_INVALID);
    }
    if (!select && StringUtils.hasText(req.getOptions())) {
      throw new BusinessException(ErrorCode.CUSTOM_FIELD_INVALID);
    }
  }

  private void apply(CustomFieldRequest req, CustomField field) {
    field.setEntityType(req.getEntityType().trim());
    field.setName(req.getName().trim());
    field.setFieldType(req.getFieldType().trim());
    field.setRequired(Boolean.TRUE.equals(req.getRequired()) ? 1 : 0);
    field.setOptions(req.getOptions());
    field.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
  }

  private CustomField require(Long id) {
    CustomField field = fieldMapper.selectById(id);
    if (field == null) {
      throw new BusinessException(ErrorCode.CUSTOM_FIELD_NOT_FOUND);
    }
    return field;
  }

  // ===== 值读写（供各实体 Service 复用） =====

  /** 校验并保存实体自定义字段值（先删后插，事务内调用）。 */
  public void saveValues(String entityType, Long entityId, List<CustomFieldValueDTO> values) {
    List<CustomField> fields = listEnabled(entityType);
    Map<Long, CustomField> fieldMap =
        fields.stream().collect(Collectors.toMap(CustomField::getId, f -> f));
    if (values != null) {
      for (CustomFieldValueDTO v : values) {
        if (v.getFieldId() == null) {
          continue;
        }
        CustomField field = fieldMap.get(v.getFieldId());
        if (field == null) {
          throw new BusinessException(ErrorCode.CUSTOM_FIELD_NOT_FOUND);
        }
        if (isRequired(field) && !StringUtils.hasText(v.getValue())) {
          throw new BusinessException(ErrorCode.CUSTOM_FIELD_REQUIRED);
        }
      }
    }
    // 必填字段校验（未出现在请求中的必填字段）
    if (values == null || values.isEmpty()) {
      for (CustomField field : fields) {
        if (isRequired(field)) {
          throw new BusinessException(ErrorCode.CUSTOM_FIELD_REQUIRED);
        }
      }
    } else {
      Map<Long, CustomFieldValueDTO> provided =
          values.stream()
              .filter(v -> v.getFieldId() != null)
              .collect(Collectors.toMap(CustomFieldValueDTO::getFieldId, v -> v, (a, b) -> a));
      for (CustomField field : fields) {
        if (isRequired(field) && !provided.containsKey(field.getId())) {
          throw new BusinessException(ErrorCode.CUSTOM_FIELD_REQUIRED);
        }
      }
    }
    // 056：字段权限校验（HIDDEN 拒绝写入 / READ_ONLY 拒绝修改；ADMIN 豁免）
    String roleCode = currentRoleCode();
    java.util.Map<Long, String> existing =
        readValuesRaw(entityType, entityId).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.crm.dto.customfield.CustomFieldValueDTO::getFieldId,
                    com.crm.dto.customfield.CustomFieldValueDTO::getValue));
    fieldPermissionService.validateWrite(roleCode, entityType, values, existing);

    // 先删后插
    valueMapper.delete(
        new LambdaQueryWrapper<CustomFieldValue>()
            .eq(CustomFieldValue::getEntityType, entityType)
            .eq(CustomFieldValue::getEntityId, entityId));
    if (values != null) {
      for (CustomFieldValueDTO v : values) {
        if (v.getFieldId() == null || !StringUtils.hasText(v.getValue())) {
          continue;
        }
        CustomFieldValue value = new CustomFieldValue();
        value.setFieldId(v.getFieldId());
        value.setEntityType(entityType);
        value.setEntityId(entityId);
        value.setFieldValue(v.getValue().trim());
        valueMapper.insert(value);
      }
    }
    // 056/103：**看不见**（HIDDEN）或**改不动**（READ_ONLY）的字段，客户端不会提交它们（HIDDEN
    // 有值则 validateWrite 已抛 422；READ_ONLY 只能原样回传）——而上面的"先删后插"会连它们一起
    // 删掉，故按库中原值补回：**看不见 / 改不动，都不等于该被删除**。
    //
    // 谓词取 !EDITABLE（= HIDDEN ∪ READ_ONLY，与 FieldMaskPlanner 同口径）而**绝不含 EDITABLE**：
    // 未配置权限的字段不在集合里 ⇒ 其省略**照旧删除**，那是客户端清除自定义字段的唯一手段
    // （utils/customField.ts 会跳过空值）——多回补一行就把这个能力毁了。
    //
    // submittedIds 排除**不是可选优化**：custom_field_value 上有 uk_field_entity_value
    // (field_id, entity_id)，而 READ_ONLY 的值**会被客户端原样回传、活着穿过 validateWrite**
    // （值相等 ⇒ 未变更 ⇒ 不 422）⇒ 少了这个排除，主循环插一行、这里再插同一个键 ⇒
    // DuplicateKeyException ⇒ 每次 PUT 都 500。过滤条件与主循环的跳过规则逐字对齐。
    Set<Long> protectedIds = protectedFieldIds(roleCode, entityType);
    if (!protectedIds.isEmpty()) {
      Set<Long> submittedIds =
          values == null
              ? Set.of()
              : values.stream()
                  .filter(v -> v.getFieldId() != null && StringUtils.hasText(v.getValue()))
                  .map(CustomFieldValueDTO::getFieldId)
                  .collect(Collectors.toSet());
      for (Map.Entry<Long, String> e : existing.entrySet()) {
        if (!protectedIds.contains(e.getKey())
            || submittedIds.contains(e.getKey())
            || !StringUtils.hasText(e.getValue())) {
          continue;
        }
        CustomFieldValue kept = new CustomFieldValue();
        kept.setFieldId(e.getKey());
        kept.setEntityType(entityType);
        kept.setEntityId(entityId);
        kept.setFieldValue(e.getValue());
        valueMapper.insert(kept);
      }
    }
  }

  /** 读取实体自定义字段值（含字段名）。**HIDDEN 字段不下发**——读路径原先不做权限计算， HIDDEN 字段的**值**会随各实体的 Response 外泄（056）。 */
  public List<CustomFieldValueDTO> readValues(String entityType, Long entityId) {
    String roleCode = currentRoleCode();
    return dropHidden(roleCode, entityType, readValuesRaw(entityType, entityId));
  }

  /** 库中原值、不做权限过滤：**仅供保存路径比对既有值**，不得直接对外返回。 */
  private List<CustomFieldValueDTO> readValuesRaw(String entityType, Long entityId) {
    List<CustomFieldValue> values =
        valueMapper.selectList(
            new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getEntityType, entityType)
                .eq(CustomFieldValue::getEntityId, entityId));
    if (values.isEmpty()) {
      return List.of();
    }
    List<Long> fieldIds = values.stream().map(CustomFieldValue::getFieldId).distinct().toList();
    Map<Long, String> names =
        fieldIds.isEmpty()
            ? Map.of()
            : fieldMapper.selectBatchIds(fieldIds).stream()
                .collect(Collectors.toMap(CustomField::getId, CustomField::getName));
    return values.stream()
        .map(
            v -> {
              CustomFieldValueDTO dto = new CustomFieldValueDTO();
              dto.setFieldId(v.getFieldId());
              dto.setFieldName(names.get(v.getFieldId()));
              dto.setValue(v.getFieldValue());
              return dto;
            })
        .toList();
  }

  /** 批量读取（避免 N+1）：entityId → 值列表。**HIDDEN 字段不下发**（056）。 */
  public Map<Long, List<CustomFieldValueDTO>> readValuesBatch(
      String entityType, List<Long> entityIds) {
    Map<Long, List<CustomFieldValueDTO>> raw = readValuesBatchRaw(entityType, entityIds);
    if (raw.isEmpty()) {
      return raw;
    }
    Set<Long> hidden = hiddenFieldIds(currentRoleCode(), entityType);
    if (hidden.isEmpty()) {
      return raw;
    }
    return raw.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().filter(v -> !hidden.contains(v.getFieldId())).toList()));
  }

  /** 库中原值、不做权限过滤（批量）。 */
  private Map<Long, List<CustomFieldValueDTO>> readValuesBatchRaw(
      String entityType, List<Long> entityIds) {
    if (entityIds.isEmpty()) {
      return Map.of();
    }
    List<CustomFieldValue> values =
        valueMapper.selectList(
            new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getEntityType, entityType)
                .in(CustomFieldValue::getEntityId, entityIds));
    if (values.isEmpty()) {
      return Map.of();
    }
    List<Long> fieldIds = values.stream().map(CustomFieldValue::getFieldId).distinct().toList();
    Map<Long, String> names =
        fieldIds.isEmpty()
            ? Map.of()
            : fieldMapper.selectBatchIds(fieldIds).stream()
                .collect(Collectors.toMap(CustomField::getId, CustomField::getName));
    return values.stream()
        .collect(
            Collectors.groupingBy(
                CustomFieldValue::getEntityId,
                Collectors.mapping(
                    v -> {
                      CustomFieldValueDTO dto = new CustomFieldValueDTO();
                      dto.setFieldId(v.getFieldId());
                      dto.setFieldName(names.get(v.getFieldId()));
                      dto.setValue(v.getFieldValue());
                      return dto;
                    },
                    Collectors.toList())));
  }

  private List<CustomField> listEnabled(String entityType) {
    return fieldMapper.selectList(
        new LambdaQueryWrapper<CustomField>()
            .eq(CustomField::getEntityType, entityType)
            .eq(CustomField::getEnabled, 1)
            .orderByAsc(CustomField::getSortOrder)
            .orderByAsc(CustomField::getId));
  }

  private boolean isRequired(CustomField field) {
    return field.getRequired() != null && field.getRequired() == 1;
  }

  private CustomFieldResponse toResponse(CustomField field) {
    CustomFieldResponse resp = new CustomFieldResponse();
    resp.setId(field.getId());
    resp.setEntityType(field.getEntityType());
    resp.setName(field.getName());
    resp.setFieldType(field.getFieldType());
    resp.setRequired(field.getRequired() != null && field.getRequired() == 1);
    resp.setOptions(field.getOptions());
    resp.setEnabled(field.getEnabled() != null && field.getEnabled() == 1);
    resp.setSortOrder(field.getSortOrder());
    resp.setVersion(field.getVersion());
    resp.setCreatedAt(field.getCreatedAt());
    return resp;
  }
}
