/**
 * 数据保留策略 Controller（080-data-retention）。
 *
 * <p><b>为什么只有变更端点声明权限码、三个读端点不声明</b>（FR-G14 的显式决策，非遗漏）：本控制器与合规导出不同—— `retention:*` 四个码是 <b>97
 * 条既有权限码字典中实际存在的项</b>，而"查看保留策略／查看某策略的执行记录"在字典里 <b>没有</b>对应项。按
 * FR-G14"不得新增权限码"，读端点只能停在全局认证（`anyRequest().authenticated()`）这一层。
 *
 * <p>这不是"少做一步"：为其临时造一个读权限码会同时牵动角色矩阵种子与前端，属 FR-G14 明令排除的范围；而给读端点错挂一个 变更码（如复用
 * `retention:update`）更糟——那会让"能看"与"能改"变成同一件事，形似收紧、实为把权限语义弄错。 此判断由
 * `SecurityHardeningIT#endpointWithoutPermissionCodeMustStayAccessible` 从黑盒方向钉住
 * （读端点对无相关权限码的用户**必须仍可访问**），避免后人误以为该处是漏加注解。
 */
package com.crm.controller;

import com.crm.dto.DataRetentionExecutionResponse;
import com.crm.dto.DataRetentionPolicyRequest;
import com.crm.dto.DataRetentionPolicyResponse;
import com.crm.security.RequirePermission;
import com.crm.service.DataRetentionPolicyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-retention")
public class DataRetentionPolicyController {

  private final DataRetentionPolicyService dataRetentionPolicyService;

  public DataRetentionPolicyController(DataRetentionPolicyService dataRetentionPolicyService) {
    this.dataRetentionPolicyService = dataRetentionPolicyService;
  }

  @PostMapping("/policies")
  @RequirePermission("retention:create")
  public ResponseEntity<DataRetentionPolicyResponse> createPolicy(
      @Valid @RequestBody DataRetentionPolicyRequest request) {
    return ResponseEntity.ok(dataRetentionPolicyService.createPolicy(request));
  }

  @GetMapping("/policies")
  public ResponseEntity<List<DataRetentionPolicyResponse>> getAllPolicies() {
    return ResponseEntity.ok(dataRetentionPolicyService.getAllPolicies());
  }

  @GetMapping("/policies/{id}")
  public ResponseEntity<DataRetentionPolicyResponse> getPolicy(@PathVariable Long id) {
    return ResponseEntity.ok(dataRetentionPolicyService.getPolicy(id));
  }

  @PutMapping("/policies/{id}")
  @RequirePermission("retention:update")
  public ResponseEntity<DataRetentionPolicyResponse> updatePolicy(
      @PathVariable Long id, @Valid @RequestBody DataRetentionPolicyRequest request) {
    return ResponseEntity.ok(dataRetentionPolicyService.updatePolicy(id, request));
  }

  @DeleteMapping("/policies/{id}")
  @RequirePermission("retention:delete")
  public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
    dataRetentionPolicyService.deletePolicy(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/policies/{id}/executions")
  public ResponseEntity<List<DataRetentionExecutionResponse>> getExecutions(@PathVariable Long id) {
    return ResponseEntity.ok(dataRetentionPolicyService.getExecutions(id));
  }

  @PostMapping("/execute")
  @RequirePermission("retention:execute")
  public ResponseEntity<Void> executeArchival() {
    dataRetentionPolicyService.executeArchival();
    return ResponseEntity.ok().build();
  }
}
