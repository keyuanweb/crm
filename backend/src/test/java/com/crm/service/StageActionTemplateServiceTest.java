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
import com.crm.dto.playbook.ActionTemplateRequest;
import com.crm.entity.StageActionTemplate;
import com.crm.repository.StageActionTemplateMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** StageActionTemplateService 单元测试（045 T009）：CRUD/阶段校验/审计。 */
class StageActionTemplateServiceTest {

  private StageActionTemplateMapper templateMapper;
  private AuditService auditService;
  private StageActionTemplateService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, StageActionTemplate.class);
  }

  @BeforeEach
  void setUp() {
    templateMapper = mock(StageActionTemplateMapper.class);
    auditService = mock(AuditService.class);
    service = new StageActionTemplateService(templateMapper, auditService);
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

  private ActionTemplateRequest request(String stage, String name) {
    ActionTemplateRequest req = new ActionTemplateRequest();
    req.setStage(stage);
    req.setActionName(name);
    req.setRequired(true);
    return req;
  }

  private StageActionTemplate template(Long id) {
    StageActionTemplate t = new StageActionTemplate();
    t.setId(id);
    t.setStage("INITIAL_CONTACT");
    t.setActionName("发送产品资料");
    t.setRequired(1);
    t.setEnabled(1);
    t.setVersion(0);
    return t;
  }

  @Test
  @DisplayName("创建模板成功：审计记录")
  void createSucceeds() {
    when(templateMapper.insert(any(StageActionTemplate.class)))
        .thenAnswer(
            invocation -> {
              StageActionTemplate t = invocation.getArgument(0);
              t.setId(1L);
              return 1;
            });

    var resp = service.create(request("INITIAL_CONTACT", "发送产品资料"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getRequired()).isTrue();
    verify(auditService).record("CREATE", "STAGE_ACTION_TEMPLATE", 1L, "创建动作模板：发送产品资料");
  }

  @Test
  @DisplayName("创建模板：终态阶段 → 422 PLAYBOOK_STAGE_INVALID")
  void createTerminalStageThrows() {
    assertThatThrownBy(() -> service.create(request("CLOSED_WON", "x")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PLAYBOOK_STAGE_INVALID);
    verify(templateMapper, never()).insert(any(StageActionTemplate.class));
  }

  @Test
  @DisplayName("更新模板成功：回查返回")
  void updateSucceeds() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L));
    when(templateMapper.updateById(any(StageActionTemplate.class))).thenReturn(1);
    when(templateMapper.selectById(1L)).thenReturn(template(1L));

    ActionTemplateRequest req = request("INITIAL_CONTACT", "发送方案");
    req.setVersion(0);
    var resp = service.update(1L, req);

    assertThat(resp.getActionName()).isEqualTo("发送方案");
    verify(auditService).record("UPDATE", "STAGE_ACTION_TEMPLATE", 1L, "编辑动作模板：发送方案");
  }

  @Test
  @DisplayName("删除模板：逻辑删除 + 审计")
  void deleteSucceeds() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L));

    service.delete(1L);

    verify(templateMapper).deleteById(org.mockito.ArgumentMatchers.<Long>any());
    verify(auditService).record("DELETE", "STAGE_ACTION_TEMPLATE", 1L, "删除动作模板：发送产品资料");
  }
}
