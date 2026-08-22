package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.dto.contract.ContractTemplateRequest;
import com.crm.entity.ContractTemplate;
import com.crm.repository.ContractTemplateMapper;
import com.crm.security.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** ContractTemplateService 单元测试（008 T030）：占位符替换/创建/停用。 */
@ExtendWith(MockitoExtension.class)
class ContractTemplateServiceTest {

  private ContractTemplateMapper templateMapper;
  private AuditService auditService;
  private ContractTemplateService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    templateMapper = mock(ContractTemplateMapper.class);
    auditService = mock(AuditService.class);
    service = new ContractTemplateService(templateMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  @Test
  @DisplayName("占位符替换：客户名/编号/金额")
  void renderReplacesPlaceholders() {
    String content = "甲方：{customerName}，编号 {contractNo}，金额 {amount} 元";
    String rendered =
        ContractTemplateService.render(content, "Acme 科技", "HT-20260822-0001", 150000L);
    assertThat(rendered).isEqualTo("甲方：Acme 科技，编号 HT-20260822-0001，金额 1,500.00 元");
  }

  @Test
  @DisplayName("占位符替换：金额为空显示 -")
  void renderNullAmount() {
    String rendered = ContractTemplateService.render("金额 {amount} 元", "客户", "HT-1", null);
    assertThat(rendered).isEqualTo("金额 - 元");
  }

  @Test
  @DisplayName("创建模板：默认 ACTIVE，记录审计")
  void createSucceeds() {
    ContractTemplateRequest req = new ContractTemplateRequest();
    req.setName("标准合同");
    req.setContent("甲方：{customerName}");
    when(templateMapper.insert(any(ContractTemplate.class)))
        .thenAnswer(
            invocation -> {
              ContractTemplate t = invocation.getArgument(0);
              t.setId(1L);
              return 1;
            });

    var resp = service.create(req);

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("ACTIVE");
    verify(templateMapper).insert(any(ContractTemplate.class));
    verify(auditService).record("CREATE", "CONTRACT_TEMPLATE", 1L, "创建合同模板：标准合同");
  }
}
