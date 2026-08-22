package com.crm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.WorkflowExecutionLog;
import com.crm.entity.WorkflowRule;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.WorkflowExecutionLogMapper;
import com.crm.repository.WorkflowRuleMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** WorkflowEngine 单元测试（013 T024）：匹配/动作/失败隔离/停用不触发。 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

  private WorkflowRuleService ruleService;
  private WorkflowExecutionLogMapper logMapper;
  private WorkflowNotificationService notificationService;
  private TaskService taskService;
  private LeadMapper leadMapper;
  private CustomerMapper customerMapper;
  private WorkflowEngine engine;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.WorkflowRule.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.WorkflowExecutionLog.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.WorkflowNotification.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.TaskItem.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.Lead.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.Customer.class);
  }

  @BeforeEach
  void setUp() {
    WorkflowRuleMapper ruleMapper = mock(WorkflowRuleMapper.class);
    ruleService = mock(WorkflowRuleService.class);
    logMapper = mock(WorkflowExecutionLogMapper.class);
    notificationService = mock(WorkflowNotificationService.class);
    taskService = mock(TaskService.class);
    leadMapper = mock(LeadMapper.class);
    customerMapper = mock(CustomerMapper.class);
    engine =
        new WorkflowEngine(
            ruleService,
            logMapper,
            notificationService,
            taskService,
            leadMapper,
            customerMapper,
            new ObjectMapper());
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    org.mockito.Mockito.lenient()
        .when(logMapper.insert(any(WorkflowExecutionLog.class)))
        .thenReturn(1);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private WorkflowRule assignRule() {
    WorkflowRule rule = new WorkflowRule();
    rule.setId(1L);
    rule.setName("线索自动分配");
    rule.setEventType("LEAD_CREATED");
    rule.setActionType("ASSIGN");
    rule.setActionJson("{\"targetUserId\":5}");
    rule.setEnabled(true);
    return rule;
  }

  @Test
  @DisplayName("ASSIGN 动作：线索创建后自动分配 ownerId")
  void assignLead() {
    when(ruleService.enabledRulesFor("LEAD_CREATED")).thenReturn(List.of(assignRule()));
    when(leadMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);

    engine.fire("LEAD_CREATED", "LEAD", 10L, Map.of("name", "张三"));

    verify(leadMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    verify(logMapper).insert(any(WorkflowExecutionLog.class));
  }

  @Test
  @DisplayName("条件不匹配：不执行动作但记录日志")
  void conditionNotMatched() {
    WorkflowRule rule = assignRule();
    rule.setConditionJson("{\"field\":\"source\",\"value\":\"WEBSITE\"}");
    when(ruleService.enabledRulesFor("LEAD_CREATED")).thenReturn(List.of(rule));

    engine.fire("LEAD_CREATED", "LEAD", 10L, Map.of("name", "张三", "source", "AD"));

    verify(leadMapper, never()).update(any(), any());
    verify(logMapper).insert(any(WorkflowExecutionLog.class));
  }

  @Test
  @DisplayName("CREATE_TASK 动作：创建任务并返回结果")
  void createTaskAction() {
    WorkflowRule rule = new WorkflowRule();
    rule.setId(2L);
    rule.setName("建任务");
    rule.setEventType("OPPORTUNITY_STAGE_CHANGED");
    rule.setActionType("CREATE_TASK");
    rule.setActionJson("{\"titleTemplate\":\"跟进{name}\",\"dueDays\":3}");
    when(ruleService.enabledRulesFor("OPPORTUNITY_STAGE_CHANGED")).thenReturn(List.of(rule));
    when(taskService.create(any()))
        .thenAnswer(
            invocation -> {
              com.crm.dto.task.TaskResponse resp = new com.crm.dto.task.TaskResponse();
              resp.setId(99L);
              return resp;
            });

    engine.fire(
        "OPPORTUNITY_STAGE_CHANGED",
        "SALES_OPPORTUNITY",
        5L,
        Map.of("stage", "NEGOTIATING", "name", "CRM"));

    verify(taskService).create(any());
    verify(logMapper).insert(any(WorkflowExecutionLog.class));
  }

  @Test
  @DisplayName("动作执行失败：隔离异常并记失败日志（不影响主流程）")
  void failureIsolated() {
    WorkflowRule rule = assignRule();
    when(ruleService.enabledRulesFor("LEAD_CREATED")).thenReturn(List.of(rule));
    when(leadMapper.update(org.mockito.ArgumentMatchers.isNull(), any()))
        .thenThrow(new RuntimeException("db error"));

    engine.fire("LEAD_CREATED", "LEAD", 10L, Map.of("name", "张三"));

    // 不抛出异常；日志记录了失败
    verify(logMapper).insert(any(WorkflowExecutionLog.class));
  }

  @Test
  @DisplayName("无启用规则：不执行不记日志")
  void noRules() {
    when(ruleService.enabledRulesFor("PAYMENT_RECORDED")).thenReturn(List.of());

    engine.fire("PAYMENT_RECORDED", "SALES_ORDER", 1L, Map.of("amount", "100"));

    verify(logMapper, never()).insert(any());
  }
}
