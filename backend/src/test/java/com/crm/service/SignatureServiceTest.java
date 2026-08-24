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
import com.crm.dto.signature.SignRequest;
import com.crm.entity.Contract;
import com.crm.entity.Quote;
import com.crm.entity.SignatureRecord;
import com.crm.entity.User;
import com.crm.repository.ContractMapper;
import com.crm.repository.QuoteMapper;
import com.crm.repository.SignatureRecordMapper;
import com.crm.repository.UserMapper;
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

/** SignatureService 单元测试（047 T009）：签署校验/状态/重复/大小。 */
class SignatureServiceTest {

  private SignatureRecordMapper recordMapper;
  private QuoteMapper quoteMapper;
  private ContractMapper contractMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private SignatureService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  private static final String TINY_PNG =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, SignatureRecord.class);
    TableInfoHelper.initTableInfo(assistant, Quote.class);
    TableInfoHelper.initTableInfo(assistant, Contract.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  void setUp() {
    recordMapper = mock(SignatureRecordMapper.class);
    quoteMapper = mock(QuoteMapper.class);
    contractMapper = mock(ContractMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service =
        new SignatureService(recordMapper, quoteMapper, contractMapper, userMapper, auditService);
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

  private SignRequest request() {
    SignRequest req = new SignRequest();
    req.setSignatureImage(TINY_PNG);
    return req;
  }

  private Quote approvedQuote(Long id) {
    Quote q = new Quote();
    q.setId(id);
    q.setStatus("APPROVED");
    return q;
  }

  @Test
  @DisplayName("签署报价单：APPROVED→SIGNED + 记录 + 审计")
  void signQuoteSucceeds() {
    when(quoteMapper.selectById(1L)).thenReturn(approvedQuote(1L));
    when(recordMapper.selectCount(any())).thenReturn(0L);
    when(recordMapper.insert(any(SignatureRecord.class)))
        .thenAnswer(
            invocation -> {
              SignatureRecord r = invocation.getArgument(0);
              r.setId(100L);
              return 1;
            });
    User signer = new User();
    signer.setId(1L);
    signer.setDisplayName("系统管理员");
    when(userMapper.selectById(1L)).thenReturn(signer);

    var resp = service.signQuote(1L, request());

    assertThat(resp.getId()).isEqualTo(100L);
    assertThat(resp.getSignerName()).isEqualTo("系统管理员");
    verify(quoteMapper).updateById(any(Quote.class));
    verify(auditService).record("SIGN", "QUOTE", 1L, "报价单签署");
  }

  @Test
  @DisplayName("签署报价单：非 APPROVED → 422")
  void signQuoteWrongStateThrows() {
    Quote q = approvedQuote(1L);
    q.setStatus("DRAFT");
    when(quoteMapper.selectById(1L)).thenReturn(q);

    assertThatThrownBy(() -> service.signQuote(1L, request()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SIGNATURE_STATE_INVALID);
    verify(recordMapper, never()).insert(any(SignatureRecord.class));
  }

  @Test
  @DisplayName("重复签署 → 409")
  void signQuoteDuplicateThrows() {
    when(quoteMapper.selectById(1L)).thenReturn(approvedQuote(1L));
    when(recordMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.signQuote(1L, request()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SIGNATURE_ALREADY_SIGNED);
  }

  @Test
  @DisplayName("签名图为空 → 422；过大 → 422")
  void signImageValidation() {
    // 每次调用返回新实例，避免 setStatus 污染共享 mock
    when(quoteMapper.selectById(1L)).thenAnswer(inv -> approvedQuote(1L));
    when(recordMapper.selectCount(any())).thenReturn(0L);

    SignRequest empty = new SignRequest();
    empty.setSignatureImage("   ");
    assertThatThrownBy(() -> service.signQuote(1L, empty))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SIGNATURE_IMAGE_REQUIRED);

    SignRequest huge = new SignRequest();
    huge.setSignatureImage("x".repeat(700_100));
    assertThatThrownBy(() -> service.signQuote(1L, huge))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SIGNATURE_IMAGE_TOO_LARGE);
  }

  @Test
  @DisplayName("签署合同：APPROVED→SIGNED")
  void signContractSucceeds() {
    Contract c = new Contract();
    c.setId(7L);
    c.setStatus("APPROVED");
    when(contractMapper.selectById(7L)).thenReturn(c);
    when(recordMapper.selectCount(any())).thenReturn(0L);
    when(recordMapper.insert(any(SignatureRecord.class)))
        .thenAnswer(
            invocation -> {
              SignatureRecord r = invocation.getArgument(0);
              r.setId(200L);
              return 1;
            });

    var resp = service.signContract(7L, request());

    assertThat(resp.getId()).isEqualTo(200L);
    verify(contractMapper).updateById(any(Contract.class));
    verify(auditService).record("SIGN", "CONTRACT", 7L, "合同签署");
  }
}
