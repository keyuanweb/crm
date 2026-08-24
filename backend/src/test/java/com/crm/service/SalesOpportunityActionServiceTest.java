package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.SalesOpportunityAction;
import com.crm.entity.StageActionTemplate;
import com.crm.repository.SalesOpportunityActionMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.StageActionTemplateMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** SalesOpportunityActionService 单元测试（045 T017）：清单/勾选/唯一/必做。 */
class SalesOpportunityActionServiceTest {

  private SalesOpportunityActionMapper actionMapper;
  private SalesOpportunityMapper soMapper;
  private StageActionTemplateMapper templateMapper;
  private SalesOpportunityActionService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, SalesOpportunity.class);
    TableInfoHelper.initTableInfo(assistant, SalesOpportunityAction.class);
    TableInfoHelper.initTableInfo(assistant, StageActionTemplate.class);
  }

  @BeforeEach
  void setUp() {
    actionMapper = mock(SalesOpportunityActionMapper.class);
    soMapper = mock(SalesOpportunityMapper.class);
    templateMapper = mock(StageActionTemplateMapper.class);
    service = new SalesOpportunityActionService(actionMapper, soMapper, templateMapper);
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

  private SalesOpportunity so(Long id, String stage) {
    SalesOpportunity so = new SalesOpportunity();
    so.setId(id);
    so.setStage(stage);
    return so;
  }

  private StageActionTemplate template(Long id, boolean required) {
    StageActionTemplate t = new StageActionTemplate();
    t.setId(id);
    t.setStage("INITIAL_CONTACT");
    t.setActionName("动作" + id);
    t.setRequired(required ? 1 : 0);
    t.setEnabled(1);
    return t;
  }

  @Test
  @DisplayName("清单：含完成状态")
  void listActionsWithCompletion() {
    when(soMapper.selectById(10L)).thenReturn(so(10L, "INITIAL_CONTACT"));
    when(templateMapper.selectList(any()))
        .thenReturn(List.of(template(1L, true), template(2L, false)));
    SalesOpportunityAction done = new SalesOpportunityAction();
    done.setTemplateId(1L);
    done.setCompletedBy(1L);
    when(actionMapper.selectList(any())).thenReturn(List.of(done));

    var items = service.listForOpportunity(10L);

    assertThat(items).hasSize(2);
    assertThat(items.get(0).getCompleted()).isTrue();
    assertThat(items.get(1).getCompleted()).isFalse();
  }

  @Test
  @DisplayName("勾选完成成功：审计记录")
  void completeSucceeds() {
    when(soMapper.selectById(10L)).thenReturn(so(10L, "INITIAL_CONTACT"));
    when(templateMapper.selectById(1L)).thenReturn(template(1L, true));
    when(actionMapper.selectCount(any())).thenReturn(0L);
    when(actionMapper.insert(any(SalesOpportunityAction.class)))
        .thenAnswer(
            invocation -> {
              SalesOpportunityAction a = invocation.getArgument(0);
              a.setId(100L);
              return 1;
            });

    var resp = service.complete(10L, 1L);

    assertThat(resp.getCompleted()).isTrue();
    assertThat(resp.getCompletedBy()).isEqualTo(1L);
  }

  @Test
  @DisplayName("重复勾选 → 409 PLAYBOOK_ACTION_ALREADY_DONE")
  void completeDuplicateThrows() {
    when(soMapper.selectById(10L)).thenReturn(so(10L, "INITIAL_CONTACT"));
    when(templateMapper.selectById(1L)).thenReturn(template(1L, true));
    when(actionMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.complete(10L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PLAYBOOK_ACTION_ALREADY_DONE);
    verify(actionMapper, never()).insert(any(SalesOpportunityAction.class));
  }

  @Test
  @DisplayName("已关闭机会不可勾选 → 422")
  void completeClosedThrows() {
    SalesOpportunity closed = so(10L, "CLOSED_WON");
    closed.setClosedAt(java.time.LocalDateTime.now());
    when(soMapper.selectById(10L)).thenReturn(closed);

    assertThatThrownBy(() -> service.complete(10L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PLAYBOOK_OPPORTUNITY_CLOSED);
  }

  @Test
  @DisplayName("必做校验：全部完成 → false；有未完成 → true")
  void hasPendingRequired() {
    when(soMapper.selectById(10L)).thenReturn(so(10L, "INITIAL_CONTACT"));
    when(templateMapper.selectList(any()))
        .thenReturn(List.of(template(1L, true), template(2L, true)));
    when(actionMapper.selectCount(any())).thenReturn(1L);

    assertThat(service.hasPendingRequired(10L)).isTrue();
  }
}
