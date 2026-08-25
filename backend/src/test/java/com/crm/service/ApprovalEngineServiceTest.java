package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.dto.approval.TaskActionRequest;
import com.crm.entity.ApprovalFlow;
import com.crm.entity.ApprovalInstance;
import com.crm.entity.ApprovalTask;
import com.crm.repository.ApprovalInstanceMapper;
import com.crm.repository.ApprovalLogMapper;
import com.crm.repository.ApprovalTaskMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** ApprovalEngineService 单元测试（033 T003）：状态机。 */
class ApprovalEngineServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, ApprovalInstance.class);
    TableInfoHelper.initTableInfo(assistant, ApprovalTask.class);
  }

  private ApprovalInstanceMapper instanceMapper;
  private ApprovalTaskMapper taskMapper;
  private ApprovalLogMapper logMapper;
  private ApprovalFlowService flowService;
  private UserMapper userMapper;
  private NotificationService notificationService;
  private ApprovalEngineService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    instanceMapper = mock(ApprovalInstanceMapper.class);
    taskMapper = mock(ApprovalTaskMapper.class);
    logMapper = mock(ApprovalLogMapper.class);
    flowService = mock(ApprovalFlowService.class);
    userMapper = mock(UserMapper.class);
    notificationService = mock(NotificationService.class);
    service =
        new ApprovalEngineService(
            instanceMapper,
            taskMapper,
            logMapper,
            flowService,
            userMapper,
            notificationService,
            mock(com.crm.repository.ContractMapper.class),
            mock(com.crm.repository.QuoteMapper.class),
            mock(org.springframework.beans.factory.ObjectProvider.class));
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private ApprovalFlow flow() {
    ApprovalFlow f = new ApprovalFlow();
    f.setId(1L);
    f.setBusinessType("CONTRACT");
    f.setNodes(
        "[{\"name\":\"销售经理\",\"approverType\":\"USER\",\"approverValue\":\"2\"},{\"name\":\"总经理\",\"approverType\":\"USER\",\"approverValue\":\"3\"}]");
    return f;
  }

  @Test
  @DisplayName("发起：双节点生成两个任务，首节点激活")
  void startCreatesTasks() {
    when(flowService.enabledFlow("CONTRACT")).thenReturn(flow());
    when(flowService.resolveNodes(any(), org.mockito.ArgumentMatchers.anyDouble()))
        .thenReturn(
            List.of(
                Map.of("name", "销售经理", "approverType", "USER", "approverValue", "2"),
                Map.of("name", "总经理", "approverType", "USER", "approverValue", "3")));

    ApprovalInstance instance = service.start("CONTRACT", 10L, "测试合同", 50000);

    assertThat(instance.getStatus()).isEqualTo("PENDING");
    assertThat(instance.getBusinessId()).isEqualTo(10L);
    // 2 个任务 insert
    Mockito.verify(taskMapper, Mockito.times(2)).insert(any(ApprovalTask.class));
  }

  @Test
  @DisplayName("未配置审批流拒绝发起")
  void startWithoutFlowRejected() {
    when(flowService.enabledFlow("CONTRACT")).thenReturn(null);

    assertThatThrownBy(() -> service.start("CONTRACT", 10L, "测试", 1))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("驳回：任务 REJECTED + 实例 REJECTED")
  void rejectRejectsInstance() {
    ApprovalTask task = new ApprovalTask();
    task.setId(5L);
    task.setInstanceId(1L);
    task.setStatus("PENDING");
    task.setApproverId(1L);
    when(taskMapper.selectById(5L)).thenReturn(task);
    ApprovalInstance instance = new ApprovalInstance();
    instance.setId(1L);
    instance.setStatus("PENDING");
    instance.setInitiator(1L);
    when(instanceMapper.selectById(1L)).thenReturn(instance);

    service.reject(5L, new TaskActionRequest());

    assertThat(task.getStatus()).isEqualTo("REJECTED");
    assertThat(instance.getStatus()).isEqualTo("REJECTED");
  }

  @Test
  @DisplayName("重提：仅驳回状态可重提")
  void renewOnlyRejected() {
    ApprovalInstance instance = new ApprovalInstance();
    instance.setId(1L);
    instance.setStatus("APPROVED");
    instance.setInitiator(1L);
    when(instanceMapper.selectById(1L)).thenReturn(instance);

    assertThatThrownBy(() -> service.renew(1L)).isInstanceOf(BusinessException.class);
  }
}
