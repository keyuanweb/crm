/** 数据保留策略 Service 接口（080-data-retention）。 */
package com.crm.service;

import com.crm.dto.DataRetentionExecutionResponse;
import com.crm.dto.DataRetentionPolicyRequest;
import com.crm.dto.DataRetentionPolicyResponse;
import java.util.List;

public interface DataRetentionPolicyService {

  DataRetentionPolicyResponse createPolicy(DataRetentionPolicyRequest request);

  List<DataRetentionPolicyResponse> getAllPolicies();

  DataRetentionPolicyResponse getPolicy(Long id);

  DataRetentionPolicyResponse updatePolicy(Long id, DataRetentionPolicyRequest request);

  void deletePolicy(Long id);

  List<DataRetentionExecutionResponse> getExecutions(Long policyId);

  void executeArchival();
}
