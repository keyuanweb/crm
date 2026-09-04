/** 数据保留策略 Controller（080-data-retention）。 */
package com.crm.controller;

import com.crm.dto.DataRetentionExecutionResponse;
import com.crm.dto.DataRetentionPolicyRequest;
import com.crm.dto.DataRetentionPolicyResponse;
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
  public ResponseEntity<DataRetentionPolicyResponse> updatePolicy(
      @PathVariable Long id, @Valid @RequestBody DataRetentionPolicyRequest request) {
    return ResponseEntity.ok(dataRetentionPolicyService.updatePolicy(id, request));
  }

  @DeleteMapping("/policies/{id}")
  public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
    dataRetentionPolicyService.deletePolicy(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/policies/{id}/executions")
  public ResponseEntity<List<DataRetentionExecutionResponse>> getExecutions(@PathVariable Long id) {
    return ResponseEntity.ok(dataRetentionPolicyService.getExecutions(id));
  }

  @PostMapping("/execute")
  public ResponseEntity<Void> executeArchival() {
    dataRetentionPolicyService.executeArchival();
    return ResponseEntity.ok().build();
  }
}
