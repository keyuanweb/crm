package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.landing.LandingPageRequest;
import com.crm.entity.Form;
import com.crm.entity.FormSubmission;
import com.crm.entity.LandingPage;
import com.crm.repository.FormMapper;
import com.crm.repository.FormSubmissionMapper;
import com.crm.repository.LandingPageMapper;
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

/** LandingPageService 单元测试（053 T008）：CRUD/公开渲染校验/UTM 统计。 */
class LandingPageServiceTest {

  private LandingPageMapper landingPageMapper;
  private FormMapper formMapper;
  private FormSubmissionMapper submissionMapper;
  private FormService formService;
  private AuditService auditService;
  private LandingPageService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, LandingPage.class);
    TableInfoHelper.initTableInfo(assistant, Form.class);
    TableInfoHelper.initTableInfo(assistant, FormSubmission.class);
  }

  @BeforeEach
  void setUp() {
    landingPageMapper = mock(LandingPageMapper.class);
    formMapper = mock(FormMapper.class);
    submissionMapper = mock(FormSubmissionMapper.class);
    formService = mock(FormService.class);
    auditService = mock(AuditService.class);
    service =
        new LandingPageService(
            landingPageMapper, formMapper, submissionMapper, formService, auditService);
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

  private Form enabledForm() {
    Form f = new Form();
    f.setId(3L);
    f.setStatus("ENABLED");
    f.setName("线索表单");
    return f;
  }

  private LandingPage lp(Long id) {
    LandingPage lp = new LandingPage();
    lp.setId(id);
    lp.setTitle("落地页");
    lp.setFormId(3L);
    lp.setEnabled(1);
    return lp;
  }

  private LandingPageRequest request() {
    LandingPageRequest req = new LandingPageRequest();
    req.setTitle("夏季促销");
    req.setFormId(3L);
    req.setEnabled(true);
    return req;
  }

  @Test
  @DisplayName("创建：关联表单启用 → 成功")
  void createSucceeds() {
    when(formMapper.selectById(3L)).thenReturn(enabledForm());
    when(landingPageMapper.insert(any(LandingPage.class)))
        .thenAnswer(
            invocation -> {
              LandingPage lp = invocation.getArgument(0);
              lp.setId(10L);
              return 1;
            });
    when(landingPageMapper.selectById(10L)).thenReturn(lp(10L));
    when(formMapper.selectById(3L)).thenReturn(enabledForm());

    var resp = service.create(request());

    assertThat(resp.getId()).isEqualTo(10L);
  }

  @Test
  @DisplayName("创建：关联表单停用 → 422")
  void createDisabledFormThrows() {
    Form f = enabledForm();
    f.setStatus("DISABLED");
    when(formMapper.selectById(3L)).thenReturn(f);

    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LANDING_FORM_INVALID);
  }

  @Test
  @DisplayName("公开渲染：落地页或表单停用 → 404")
  void publicViewUnavailable() {
    LandingPage disabled = lp(1L);
    disabled.setEnabled(0);
    when(landingPageMapper.selectById(1L)).thenReturn(disabled);
    when(formMapper.selectById(3L)).thenReturn(enabledForm());

    assertThatThrownBy(() -> service.publicView(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.LANDING_PAGE_UNAVAILABLE);
  }

  @Test
  @DisplayName("UTM 统计：按 source/campaign 聚合")
  void statsAggregates() {
    when(landingPageMapper.selectById(1L)).thenReturn(lp(1L));
    when(formMapper.selectById(3L)).thenReturn(enabledForm());
    FormSubmission s1 = submission("facebook", "summer");
    FormSubmission s2 = submission("facebook", "summer");
    FormSubmission s3 = submission("google", "winter");
    when(submissionMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));

    var stats = service.stats(1L, null, null);

    assertThat(stats.getTotal()).isEqualTo(3);
    assertThat(stats.getBySource().get(0).getDimension()).isEqualTo("facebook");
    assertThat(stats.getBySource().get(0).getCount()).isEqualTo(2);
    assertThat(stats.getByCampaign().get(0).getCount()).isEqualTo(2);
  }

  private FormSubmission submission(String source, String campaign) {
    FormSubmission s = new FormSubmission();
    s.setFormId(3L);
    s.setUtmSource(source);
    s.setUtmCampaign(campaign);
    return s;
  }
}
