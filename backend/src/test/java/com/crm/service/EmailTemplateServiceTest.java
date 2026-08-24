package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.dto.email.EmailTemplateRequest;
import com.crm.dto.email.EmailTemplateResponse;
import com.crm.entity.EmailTemplate;
import com.crm.repository.EmailTemplateMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** EmailTemplateService 单元测试（030 T004）。 */
class EmailTemplateServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, EmailTemplate.class);
  }

  private EmailTemplateMapper templateMapper;
  private AuditService auditService;
  private EmailTemplateService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    templateMapper = mock(EmailTemplateMapper.class);
    auditService = mock(AuditService.class);
    service = new EmailTemplateService(templateMapper, auditService);
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

  @Test
  @DisplayName("创建模板：名称重复拒绝")
  void createDuplicateRejected() {
    when(templateMapper.selectCount(any())).thenReturn(1L);
    EmailTemplateRequest req = new EmailTemplateRequest();
    req.setName("欢迎");
    req.setSubject("欢迎 {name}");
    req.setContent("<p>Hi</p>");

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("创建模板成功")
  void createSuccess() {
    when(templateMapper.selectCount(any())).thenReturn(0L);
    EmailTemplateRequest req = new EmailTemplateRequest();
    req.setName("欢迎");
    req.setSubject("欢迎 {name}");
    req.setContent("<p>Hi {name}</p>");
    req.setCategory("WELCOME");

    EmailTemplateResponse resp = service.create(req);

    assertThat(resp.getName()).isEqualTo("欢迎");
    verify(templateMapper).insert(any(EmailTemplate.class));
  }

  @Test
  @DisplayName("变量渲染：{name}/{company} 替换")
  void renderVars() {
    EmailTemplate t = new EmailTemplate();
    t.setSubject("欢迎 {name} 到 {company}");
    t.setContent("<p>Hi {name} of {company}</p>");

    String content =
        service.renderContent(t, Map.of("name", "张三", "company", "Acme 科技", "phone", "138"));

    assertThat(content).contains("Hi 张三 of Acme 科技");
    assertThat(content).doesNotContain("{name}");
  }

  @Test
  @DisplayName("变量未匹配保留原样")
  void renderUnmatchedKept() {
    EmailTemplate t = new EmailTemplate();
    t.setSubject("欢迎 {name}");
    t.setContent("<p>Hi {name} {phone}</p>");

    String content = service.renderContent(t, Map.of("name", "张三"));

    assertThat(content).contains("Hi 张三 {phone}");
  }
}
