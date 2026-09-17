package com.crm.support;

import com.crm.service.FieldPermissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 写侧回补护栏（102-builtin-field-permission）：内置字段被配成 HIDDEN / READ_ONLY 后，**省略提交不得销毁库中原值**。
 *
 * <h2>它治的是哪一种缺陷</h2>
 *
 * <p>056 的权限语义是「提交 HIDDEN ⇒ 422、改 READ_ONLY ⇒ 422」——**两条都要求客户端先提交**。但看不见的字段客户端 自然会**省略**，而 {@code
 * CustomerService.apply} 是**无条件逐字段覆盖**（{@code OpportunityService} 更狠：金额 {@code null →
 * 0L}）。于是「配了只读」的真实后果是：用户正常编辑一次备注，就把客户的电话**静默清空**——权限 「生效」的那一瞬间正好是数据被销毁的那一瞬间。⇒ 写侧的缺口**不是提交侧，而是省略侧**。
 *
 * <h2>三步顺序（错一步就白做）</h2>
 *
 * <ol>
 *   <li>{@link #capture}：读**请求里提交了什么**与**库里原本是什么**，先交给 {@link
 *       FieldPermissionService#validateBuiltinWrite} 判定——提交 HIDDEN ⇒ 422、改 READ_ONLY ⇒ 422。
 *   <li>{@code apply(req, entity)}：既有装配，会（且**应当**）无条件覆盖全部字段。
 *   <li>{@link #restore}：把受保护字段**写回库中快照**。
 * </ol>
 *
 * <p>⚠️ {@code restore} **必须排在 {@code apply} 之后**：商机金额的 {@code null → 0L} 强转发生在装配内部，只有后置 回补才盖得住它。放在
 * {@code apply} 之前等于什么都没做，而且**看起来完全正常**（这是个只在金额上显形的顺序依赖）。
 *
 * <p>⚠️ create 路径**没有原值可回补**：{@link #validateOnly} 只做判定不返回快照。故内置字段在 create 上等价于 「不可设置」——省略即取默认值，提交
 * HIDDEN 则 422。这是**语义推论**而不是漏做，写在这里免得被读成缺口。
 */
@Component
public class BuiltinWriteGuard {

  private final FieldPermissionService fieldPermissionService;
  private final BuiltinFieldRegistry registry;
  private final FieldMaskPlanner planner;

  public BuiltinWriteGuard(
      FieldPermissionService fieldPermissionService,
      BuiltinFieldRegistry registry,
      FieldMaskPlanner planner) {
    this.fieldPermissionService = fieldPermissionService;
    this.registry = registry;
    this.planner = planner;
  }

  /**
   * 写前判定 + 取库中快照（**必须在装配之前调用**）。
   *
   * @param request 提交上来的请求 DTO（必须是对应实体的注册载体，否则读到的全是 null）
   * @param entity 库中实体；create 路径传 {@code null}
   * @return 受保护字段（HIDDEN ∪ READ_ONLY）→ **库中原值**；交给 {@link #restore} 用
   */
  public Map<String, Object> capture(String entityType, Object request, Object entity) {
    Map<String, Object> submitted = readAll(entityType, request);
    Map<String, Object> original = readAll(entityType, entity);
    String roleCode = planner.currentRole();
    fieldPermissionService.validateBuiltinWrite(roleCode, entityType, submitted, original);
    Set<String> protectedKeys = planner.protectedKeys(roleCode, entityType);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : original.entrySet()) {
      if (protectedKeys.contains(entry.getKey())) {
        snapshot.put(entry.getKey(), entry.getValue());
      }
    }
    return snapshot;
  }

  /** create 路径的写前判定：没有库中原值，故只判定、不回补（见类注释）。 */
  public void validateOnly(String entityType, Object request) {
    capture(entityType, request, null);
  }

  /**
   * 把受保护字段写回快照里的值（**必须在装配之后调用**）。
   *
   * <p>只遍历快照里那几个键：非受保护字段一律由装配决定，回补**不会吞掉正常编辑**（快照为空时整个方法是空操作， 未配置内置权限的部署就是这条路径）。
   */
  public void restore(String entityType, Object entity, Map<String, Object> snapshot) {
    if (snapshot == null || snapshot.isEmpty()) {
      return; // 未配置受保护字段 ⇒ 零成本直通（绝大多数部署）
    }
    for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
      registry.write(entity, entry.getKey(), entry.getValue());
    }
  }

  /** 该实体**全部注册字段**在给定载体上的值（载体为 null 或类型不匹配 ⇒ 全 null）。 */
  private Map<String, Object> readAll(String entityType, Object carrier) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (BuiltinField field : registry.fieldsOf(entityType)) {
      values.put(field.fieldKey(), registry.read(carrier, field.fieldKey()));
    }
    return values;
  }
}
