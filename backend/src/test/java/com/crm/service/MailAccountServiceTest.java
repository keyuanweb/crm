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
import com.crm.dto.mail.MailAccountRequest;
import com.crm.entity.MailAccount;
import com.crm.repository.MailAccountMapper;
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

/** MailAccountService 单元测试（062 T008）：CRUD/邮箱校验/默认唯一。 */
class MailAccountServiceTest {

  private MailAccountMapper accountMapper;
  private MailAccountService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, MailAccount.class);
  }

  @BeforeEach
  void setUp() {
    accountMapper = mock(MailAccountMapper.class);
    service = new MailAccountService(accountMapper);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private MailAccountRequest request() {
    MailAccountRequest req = new MailAccountRequest();
    req.setEmail("sales@corp.com");
    req.setDisplayName("销售部");
    req.setImapHost("imap.corp.com");
    req.setImapPort(993);
    req.setSmtpHost("smtp.corp.com");
    req.setSmtpPort(465);
    req.setEnabled(true);
    req.setIsDefaultSender(true);
    return req;
  }

  @Test
  @DisplayName("创建：邮箱合法 → 成功且设默认发件")
  void createSucceeds() {
    when(accountMapper.selectCount(any())).thenReturn(0L);
    when(accountMapper.selectOne(any())).thenReturn(null); // 无既有默认
    when(accountMapper.insert(any(MailAccount.class)))
        .thenAnswer(
            invocation -> {
              MailAccount a = invocation.getArgument(0);
              a.setId(1L);
              return 1;
            });
    MailAccount stored = new MailAccount();
    stored.setId(1L);
    stored.setEmail("sales@corp.com");
    stored.setDisplayName("销售部");
    stored.setIsDefaultSender(1);
    when(accountMapper.selectById(1L)).thenReturn(stored);

    var resp = service.create(request());

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getEmail()).isEqualTo("sales@corp.com");
    assertThat(resp.getIsDefaultSender()).isTrue();
  }

  @Test
  @DisplayName("创建：邮箱格式非法 → 422")
  void createInvalidEmailThrows() {
    MailAccountRequest req = request();
    req.setEmail("not-an-email");
    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.MAIL_EMAIL_INVALID);
  }

  @Test
  @DisplayName("创建：邮箱重复 → 409")
  void createDuplicateEmailThrows() {
    when(accountMapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.MAIL_EMAIL_DUPLICATE);
  }
}
