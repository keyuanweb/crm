package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.workflow.WorkflowRuleRequest;
import com.crm.entity.WorkflowRule;
import com.crm.repository.WorkflowRuleMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** WorkflowRuleService 单元测试（013 T012）：CRUD/动作校验。 */
@ExtendWith(MockitoExtension.class)
class WorkflowRuleServiceTest {

  private WorkflowRuleMapper ruleMapper;
  private AuditService auditService;
  private WorkflowRuleService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    ruleMapper = mock(WorkflowRuleMapper.class);
    auditService = mock(AuditService.class);
    service = new WorkflowRuleService(ruleMapper, auditService, new ObjectMapper());
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private WorkflowRuleRequest assignRequest() {
    WorkflowRuleRequest req = new WorkflowRuleRequest();
    req.setName("线索自动分配");
    req.setEventType("LEAD_CREATED");
    req.setActionType("ASSIGN");
    req.setAction(Map.of("targetUserId", 5));
    req.setEnabled(true);
    return req;
  }

  @Test
  @DisplayName("创建规则成功：默认启用，审计记录")
  void createSucceeds() {
    when(ruleMapper.insert(any(WorkflowRule.class)))
        .thenAnswer(
            invocation -> {
              WorkflowRule r = invocation.getArgument(0);
              r.setId(1L);
              return 1;
            });

    var resp = service.create(assignRequest());

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getActionType()).isEqualTo("ASSIGN");
    assertThat(resp.getAction().get("targetUserId")).isEqualTo(5);
    verify(ruleMapper).insert(any(WorkflowRule.class));
    verify(auditService).record("CREATE", "WORKFLOW_RULE", 1L, "创建规则：线索自动分配");
  }

  @Test
  @DisplayName("ASSIGN 动作缺 targetUserId 抛出 WORKFLOW_INVALID_ACTION")
  void assignMissingTargetThrows() {
    WorkflowRuleRequest req = assignRequest();
    req.setAction(Map.of());

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.WORKFLOW_INVALID_ACTION);
  }

  @Test
  @DisplayName("CREATE_TASK 动作缺 titleTemplate 抛出 WORKFLOW_INVALID_ACTION")
  void createTaskMissingTitleThrows() {
    WorkflowRuleRequest req = new WorkflowRuleRequest();
    req.setName("建任务");
    req.setEventType("OPPORTUNITY_STAGE_CHANGED");
    req.setActionType("CREATE_TASK");
    req.setAction(Map.of("dueDays", 3));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.WORKFLOW_INVALID_ACTION);
  }

  @Test
  @DisplayName("启停规则：enabled 翻转")
  void toggleFlipsEnabled() {
    WorkflowRule rule = new WorkflowRule();
    rule.setId(1L);
    rule.setName("规则A");
    rule.setEnabled(true);
    WorkflowRule toggled = new WorkflowRule();
    toggled.setId(1L);
    toggled.setName("规则A");
    toggled.setEnabled(false);
    // 顺序 stub：require 读 enabled=true，翻转后回读 false
    when(ruleMapper.selectById(1L)).thenReturn(rule, toggled);
    when(ruleMapper.updateById(any(WorkflowRule.class))).thenReturn(1);

    var resp = service.toggle(1L);

    assertThat(resp.getEnabled()).isFalse();
    verify(auditService).record("TOGGLE", "WORKFLOW_RULE", 1L, "规则启停：规则A");
  }

  @Test
  @DisplayName("规则不存在抛出 WORKFLOW_RULE_NOT_FOUND")
  void missingRuleThrows() {
    when(ruleMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> service.toggle(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.WORKFLOW_RULE_NOT_FOUND);
  }
}
