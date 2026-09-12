package com.crm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.DataRetentionPolicyRequest;
import com.crm.dto.DataRetentionPolicyResponse;
import com.crm.model.entity.DataRetentionExecution;
import com.crm.model.entity.DataRetentionPolicy;
import com.crm.repository.DataRetentionExecutionRepository;
import com.crm.repository.DataRetentionPolicyRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 数据保留策略 Service 单元测试（080-data-retention）。 */
@ExtendWith(MockitoExtension.class)
class DataRetentionPolicyServiceTest {

  @Mock private DataRetentionPolicyRepository policyRepository;

  @Mock private DataRetentionExecutionRepository executionRepository;

  @Mock private AuditService auditService;

  @InjectMocks private DataRetentionPolicyServiceImpl dataRetentionPolicyService;

  private DataRetentionPolicy samplePolicy;

  @BeforeEach
  void setUp() {
    samplePolicy = new DataRetentionPolicy();
    samplePolicy.setId(1L);
    samplePolicy.setEntityType("CUSTOMER");
    samplePolicy.setRetentionDays(365);
    samplePolicy.setActionType("ARCHIVE");
    samplePolicy.setStatus("ACTIVE");
    samplePolicy.setCreatedAt(LocalDateTime.now());
    samplePolicy.setUpdatedAt(LocalDateTime.now());
  }

  @Test
  @DisplayName("T018: createPolicy 创建数据保留策略")
  void createPolicy_shouldCreateAndReturnResponse() {
    // Given
    DataRetentionPolicyRequest request = new DataRetentionPolicyRequest();
    request.setEntityType("CUSTOMER");
    request.setRetentionDays(365);
    request.setActionType("ARCHIVE");

    // MyBatis-Plus 在真实库上会把自增主键回填进实体，mock 必须模拟这一步：
    // 服务返回的 id 与审计记录的 id 都取自 entity.getId()，只桩返回值不会让 id 出现。
    when(policyRepository.insert(any(DataRetentionPolicy.class)))
        .thenAnswer(
            inv -> {
              inv.getArgument(0, DataRetentionPolicy.class).setId(1L);
              return 1;
            });

    // When
    DataRetentionPolicyResponse response = dataRetentionPolicyService.createPolicy(request);

    // Then
    assertNotNull(response);
    assertEquals(1L, response.getId());
    assertEquals("CUSTOMER", response.getEntityType());
    assertEquals(365, response.getRetentionDays());
    assertEquals("ARCHIVE", response.getActionType());
    assertEquals("ACTIVE", response.getStatus());

    verify(policyRepository).insert(any(DataRetentionPolicy.class));
    verify(auditService).record(eq("CREATE"), eq("DATA_RETENTION_POLICY"), anyLong(), anyString());
  }

  @Test
  @DisplayName("T019: getAllPolicies 获取所有策略")
  void getAllPolicies_shouldReturnAllPolicies() {
    // Given
    when(policyRepository.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(samplePolicy));

    // When
    List<DataRetentionPolicyResponse> responses = dataRetentionPolicyService.getAllPolicies();

    // Then
    assertNotNull(responses);
    assertEquals(1, responses.size());
    assertEquals("CUSTOMER", responses.get(0).getEntityType());
  }

  @Test
  @DisplayName("T020: getPolicy 获取单个策略")
  void getPolicy_shouldReturnPolicy() {
    // Given
    when(policyRepository.selectById(1L)).thenReturn(samplePolicy);

    // When
    DataRetentionPolicyResponse response = dataRetentionPolicyService.getPolicy(1L);

    // Then
    assertNotNull(response);
    assertEquals(1L, response.getId());
    assertEquals("CUSTOMER", response.getEntityType());
  }

  @Test
  @DisplayName("T021: updatePolicy 更新策略")
  void updatePolicy_shouldUpdatePolicy() {
    // Given
    DataRetentionPolicyRequest request = new DataRetentionPolicyRequest();
    request.setEntityType("CONTACT");
    request.setRetentionDays(180);
    request.setActionType("DELETE");

    when(policyRepository.selectById(1L)).thenReturn(samplePolicy);
    when(policyRepository.updateById(any(DataRetentionPolicy.class))).thenReturn(1);

    // When
    DataRetentionPolicyResponse response = dataRetentionPolicyService.updatePolicy(1L, request);

    // Then
    assertNotNull(response);
    assertEquals("CONTACT", response.getEntityType());
    assertEquals(180, response.getRetentionDays());

    verify(policyRepository).updateById(any(DataRetentionPolicy.class));
    verify(auditService).record(eq("UPDATE"), eq("DATA_RETENTION_POLICY"), anyLong(), anyString());
  }

  @Test
  @DisplayName("T022: deletePolicy 删除策略")
  void deletePolicy_shouldDeletePolicy() {
    // Given
    when(policyRepository.selectById(1L)).thenReturn(samplePolicy);

    // When
    dataRetentionPolicyService.deletePolicy(1L);

    // Then
    verify(policyRepository).deleteById(1L);
    verify(auditService).record(eq("DELETE"), eq("DATA_RETENTION_POLICY"), anyLong(), anyString());
  }

  @Test
  @DisplayName("T023: executeArchival 执行归档")
  void executeArchival_shouldExecuteForActivePolicies() {
    // Given
    when(policyRepository.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(samplePolicy));
    when(executionRepository.insert(any(DataRetentionExecution.class))).thenReturn(1);

    // When
    dataRetentionPolicyService.executeArchival();

    // Then
    verify(executionRepository, times(1)).insert(any(DataRetentionExecution.class));
  }

  @Test
  @DisplayName("T024: getExecutions 获取执行历史")
  void getExecutions_shouldReturnExecutionHistory() {
    // Given
    DataRetentionExecution execution = new DataRetentionExecution();
    execution.setId(1L);
    execution.setPolicyId(1L);
    execution.setStatus("SUCCESS");
    execution.setExecutedAt(LocalDateTime.now());
    execution.setProcessedCount(100);

    when(executionRepository.findByPolicyIdOrderByExecutedAtDesc(1L))
        .thenReturn(List.of(execution));

    // When
    var responses = dataRetentionPolicyService.getExecutions(1L);

    // Then
    assertNotNull(responses);
    assertEquals(1, responses.size());
    assertEquals("SUCCESS", responses.get(0).getStatus());
    assertEquals(100, responses.get(0).getProcessedCount());
  }

  @Test
  @DisplayName("T020: getPolicy 不存在的策略返回 null")
  void getPolicy_shouldReturnNullForNonExistent() {
    // Given
    when(policyRepository.selectById(999L)).thenReturn(null);

    // When
    DataRetentionPolicyResponse response = dataRetentionPolicyService.getPolicy(999L);

    // Then
    assertNull(response);
  }
}
