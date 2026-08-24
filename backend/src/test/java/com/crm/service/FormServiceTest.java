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
import com.crm.dto.form.FormRequest;
import com.crm.dto.form.FormResponse;
import com.crm.entity.Form;
import com.crm.entity.FormSubmission;
import com.crm.entity.Lead;
import com.crm.repository.FormMapper;
import com.crm.repository.FormSubmissionMapper;
import com.crm.repository.LeadMapper;
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

/** FormService 单元测试（036 T004）。 */
class FormServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Form.class);
    TableInfoHelper.initTableInfo(assistant, FormSubmission.class);
    TableInfoHelper.initTableInfo(assistant, Lead.class);
  }

  private FormMapper formMapper;
  private FormSubmissionMapper submissionMapper;
  private LeadMapper leadMapper;
  private AuditService auditService;
  private FormService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    formMapper = mock(FormMapper.class);
    submissionMapper = mock(FormSubmissionMapper.class);
    leadMapper = mock(LeadMapper.class);
    auditService = mock(AuditService.class);
    service = new FormService(formMapper, submissionMapper, leadMapper, auditService);
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

  private Form form() {
    Form f = new Form();
    f.setId(1L);
    f.setName("试用申请");
    f.setFields(
        "[{\"field\":\"name\",\"label\":\"姓名\",\"type\":\"TEXT\",\"required\":true},{\"field\":\"phone\",\"label\":\"手机\",\"type\":\"TEL\",\"required\":true}]");
    f.setSource("WEBSITE");
    f.setStatus("ENABLED");
    f.setSubmissionCount(0);
    return f;
  }

  @Test
  @DisplayName("创建表单成功")
  void createSuccess() {
    FormRequest req = new FormRequest();
    req.setName("试用申请");
    FormRequest.FormField f1 = new FormRequest.FormField();
    f1.setField("name");
    f1.setLabel("姓名");
    f1.setType("TEXT");
    f1.setRequired(true);
    req.setFields(List.of(f1));

    FormResponse resp = service.create(req);

    assertThat(resp.getName()).isEqualTo("试用申请");
    verify(formMapper).insert(any(Form.class));
  }

  @Test
  @DisplayName("提交：必填缺失拒绝")
  void submitRejectsMissingRequired() {
    when(formMapper.selectById(1L)).thenReturn(form());

    Map<String, Object> payload = Map.of("name", "张三"); // 缺 phone

    assertThatThrownBy(() -> service.submit(1L, payload, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("提交：防重复（phone 已有线索）拦截")
  void submitRejectsDuplicate() {
    when(formMapper.selectById(1L)).thenReturn(form());
    when(leadMapper.selectCount(any())).thenReturn(1L);

    Map<String, Object> payload = Map.of("name", "张三", "phone", "13800000000");

    assertThatThrownBy(() -> service.submit(1L, payload, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("提交成功：建线索 + 快照 + 计数")
  void submitSuccess() {
    when(formMapper.selectById(1L)).thenReturn(form());
    when(leadMapper.selectCount(any())).thenReturn(0L);

    Map<String, Object> result =
        service.submit(1L, Map.of("name", "张三", "phone", "13800000000"), null);

    assertThat(result.get("message")).isNotNull();
    verify(leadMapper).insert(any(Lead.class));
    verify(submissionMapper).insert(any(FormSubmission.class));
    verify(formMapper).updateById(any(Form.class));
  }

  @Test
  @DisplayName("提交：停用表单拒绝")
  void submitRejectsDisabled() {
    Form disabled = form();
    disabled.setStatus("DISABLED");
    when(formMapper.selectById(1L)).thenReturn(disabled);

    assertThatThrownBy(() -> service.submit(1L, Map.of("name", "x"), null))
        .isInstanceOf(BusinessException.class);
  }
}
