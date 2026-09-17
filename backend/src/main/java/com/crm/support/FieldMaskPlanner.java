package com.crm.support;

import com.crm.security.SecurityUtil;
import com.crm.service.FieldPermissionService;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 内置字段掩码的**唯一判据源**（102-builtin-field-permission）：某角色在某实体上「看不见」/「受保护」的内置字段名。
 *
 * <p>出参收口点（{@link FieldMaskingResponseBodyAdvice}）、两个 xlsx 导出、写侧回补（{@link BuiltinWriteGuard}）
 * 都从这里取，**不各自算一套**——分开算就会出现「列表页不显示、导出里还在」这类判据漂移。
 *
 * <p>返回**字段名集合**而不是某个计划对象：少一个类、少一份 record 的 {@code equals/hashCode/toString} 覆盖税， 且调用方要的本来就只是一个集合。
 *
 * <p><b>不加缓存</b>：配置改一次就该立刻生效；代价是每次出参/每次导出多一次查询（与既有 {@code readValues} 同量级）。收口点在**单次响应内**按 (角色, 实体)
 * 复用一次结果，故一个列表页不会按行数查库（102 债务 7）。
 */
@Component
public class FieldMaskPlanner {

  private final FieldPermissionService fieldPermissionService;

  public FieldMaskPlanner(FieldPermissionService fieldPermissionService) {
    this.fieldPermissionService = fieldPermissionService;
  }

  /**
   * 当前请求主体的角色编码。
   *
   * <p>{@code 无主体 ⇒ ADMIN}（fail-open）——这不是本类发明的口径，而是照 {@code CustomFieldService.currentRoleCode} 与
   * 056 既有实现的同一写法；匿名端点今天不返回任何注册载体。如实登记在 102 的 research.md。
   */
  public String currentRole() {
    var principal = SecurityUtil.currentPrincipal();
    return principal == null ? "ADMIN" : principal.role();
  }

  /** 该角色在该实体上**不可见**的内置字段名（HIDDEN）⇒ 出参里置 null、导出里格空。 */
  public Set<String> plan(String roleCode, String entityType) {
    Set<String> keys = new LinkedHashSet<>();
    for (Map.Entry<String, String> entry :
        fieldPermissionService.builtinPermissionsForRole(roleCode, entityType).entrySet()) {
      if (FieldPermissionService.PERM_HIDDEN.equals(entry.getValue())) {
        keys.add(entry.getKey());
      }
    }
    return keys;
  }

  /**
   * 该角色在该实体上**受保护**的内置字段名（HIDDEN ∪ READ_ONLY）⇒ 写侧必须回补库中原值。
   *
   * <p>判据写成 {@code !EDITABLE}（而不是并列枚举两个值）：将来若多出一种权限值， 「不是可编辑」一律按受保护处理——**往严的一侧倒**，不会静默放行一个未知权限的写入。
   */
  public Set<String> protectedKeys(String roleCode, String entityType) {
    Set<String> keys = new LinkedHashSet<>();
    for (Map.Entry<String, String> entry :
        fieldPermissionService.builtinPermissionsForRole(roleCode, entityType).entrySet()) {
      if (!FieldPermissionService.PERM_EDITABLE.equals(entry.getValue())) {
        keys.add(entry.getKey());
      }
    }
    return keys;
  }
}
