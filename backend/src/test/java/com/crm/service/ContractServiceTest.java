package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.contract.ContractRequest;
import com.crm.entity.Contract;
import com.crm.entity.ContractTemplate;
import com.crm.entity.Customer;
import com.crm.entity.Quote;
import com.crm.repository.ContractAttachmentMapper;
import com.crm.repository.ContractMapper;
import com.crm.repository.ContractTemplateMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.QuoteMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** ContractService 单元测试（008 T012/T019）：基于报价创建/算额/状态机/模板渲染。 */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

  private ContractMapper contractMapper;
  private ContractAttachmentMapper attachmentMapper;
  private ContractTemplateMapper templateMapper;
  private CustomerMapper customerMapper;
  private QuoteMapper quoteMapper;
  private AuditService auditService;
  private ApprovalEngineService approvalEngineService;
  private ContractService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    contractMapper = mock(ContractMapper.class);
    attachmentMapper = mock(ContractAttachmentMapper.class);
    templateMapper = mock(ContractTemplateMapper.class);
    customerMapper = mock(CustomerMapper.class);
    quoteMapper = mock(QuoteMapper.class);
    auditService = mock(AuditService.class);
    approvalEngineService = mock(ApprovalEngineService.class);
    service =
        new ContractService(
            contractMapper,
            attachmentMapper,
            templateMapper,
            customerMapper,
            quoteMapper,
            auditService,
            approvalEngineService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private Customer customer(Long id) {
    Customer c = new Customer();
    c.setId(id);
    c.setName("测试客户");
    return c;
  }

  private Quote approvedQuote(Long id, Long customerId, long amount) {
    Quote q = new Quote();
    q.setId(id);
    q.setQuoteNo("Q-20260822-0001");
    q.setCustomerId(customerId);
    q.setStatus(QuoteService.STATUS_APPROVED);
    q.setTotalAmount(amount);
    return q;
  }

  private ContractRequest request(Long customerId, Long quoteId, Long amount) {
    ContractRequest req = new ContractRequest();
    req.setTitle("测试合同");
    req.setCustomerId(customerId);
    req.setQuoteId(quoteId);
    req.setAmount(amount);
    return req;
  }

  private Contract contract(Long id, String status) {
    Contract c = new Contract();
    c.setId(id);
    c.setContractNo("HT-20260822-0001");
    c.setCustomerId(10L);
    c.setStatus(status);
    c.setVersion(0);
    return c;
  }

  @Test
  @DisplayName("基于已通过报价创建：金额自动带入报价总额")
  void createFromApprovedQuote() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(quoteMapper.selectById(5L)).thenReturn(approvedQuote(5L, 10L, 1470000L));
    when(contractMapper.selectList(any())).thenReturn(List.of());
    final Contract[] saved = new Contract[1];
    when(contractMapper.insert(any(Contract.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              c.setId(1L);
              saved[0] = c;
              return 1;
            });
    when(contractMapper.selectById(1L)).thenAnswer(invocation -> saved[0]);
    when(attachmentMapper.selectList(any())).thenReturn(List.of());

    var resp = service.create(request(10L, 5L, null));

    assertThat(resp.getAmount()).isEqualTo(1470000L);
    assertThat(resp.getStatus()).isEqualTo("DRAFT");
    assertThat(resp.getContractNo()).startsWith("HT-2026");
    verify(contractMapper).insert(any(Contract.class));
    String todayNo =
        "HT-"
            + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
            + "-0001";
    verify(auditService).record("CREATE", "CONTRACT", 1L, "创建合同：" + todayNo);
  }

  @Test
  @DisplayName("基于未通过报价创建抛出 QUOTE_NOT_APPROVED")
  void createFromUnapprovedQuoteThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    Quote draft = approvedQuote(5L, 10L, 1000L);
    draft.setStatus(QuoteService.STATUS_DRAFT);
    when(quoteMapper.selectById(5L)).thenReturn(draft);

    assertThatThrownBy(() -> service.create(request(10L, 5L, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUOTE_NOT_APPROVED);
    verify(contractMapper, never()).insert(any());
  }

  @Test
  @DisplayName("基于报价创建：商机与客户不匹配抛出 OPPORTUNITY_CUSTOMER_MISMATCH")
  void createQuoteCustomerMismatchThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(quoteMapper.selectById(5L)).thenReturn(approvedQuote(5L, 999L, 1000L));

    assertThatThrownBy(() -> service.create(request(10L, 5L, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_CUSTOMER_MISMATCH);
  }

  @Test
  @DisplayName("模板渲染：占位符替换为客户名/编号/金额")
  void templateRendersPlaceholders() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(contractMapper.selectList(any())).thenReturn(List.of());
    ContractTemplate template = new ContractTemplate();
    template.setId(1L);
    template.setStatus("ACTIVE");
    template.setContent("甲方：{customerName}，编号 {contractNo}，金额 {amount} 元");
    when(templateMapper.selectById(1L)).thenReturn(template);
    final Contract[] saved = new Contract[1];
    when(contractMapper.insert(any(Contract.class)))
        .thenAnswer(
            invocation -> {
              Contract c = invocation.getArgument(0);
              c.setId(1L);
              saved[0] = c;
              return 1;
            });
    when(contractMapper.selectById(1L)).thenAnswer(invocation -> saved[0]);
    when(attachmentMapper.selectList(any())).thenReturn(List.of());

    ContractRequest req = request(10L, null, 150000L);
    req.setTemplateId(1L);
    var resp = service.create(req);

    String todayNo =
        "HT-"
            + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
            + "-0001";
    assertThat(resp.getContent()).isEqualTo("甲方：测试客户，编号 " + todayNo + "，金额 1,500.00 元");
  }

  @Test
  @DisplayName("日期校验：生效晚于结束抛出 AMOUNT_RANGE_INVALID")
  void invalidDatesThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    ContractRequest req = request(10L, null, 1000L);
    req.setStartDate(LocalDate.of(2026, 10, 1));
    req.setEndDate(LocalDate.of(2026, 9, 1));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AMOUNT_RANGE_INVALID);
    verify(contractMapper, never()).insert(any());
  }

  @Test
  @DisplayName("编辑已提交合同抛出 CONTRACT_INVALID_STATE")
  void updatePendingThrows() {
    when(contractMapper.selectById(1L)).thenReturn(contract(1L, "PENDING_APPROVAL"));

    assertThatThrownBy(() -> service.update(1L, request(10L, null, 1000L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTRACT_INVALID_STATE);
  }

  @Test
  @DisplayName("审批通过：记录审批人与时间")
  void approveSucceeds() {
    Contract approved = contract(1L, "APPROVED");
    // 顺序 stub：require 读 PENDING，审批后回读 APPROVED
    when(contractMapper.selectById(1L)).thenReturn(contract(1L, "PENDING_APPROVAL"), approved);
    when(contractMapper.updateById(any(Contract.class))).thenReturn(1);

    var resp = service.approve(1L);

    assertThat(resp.getStatus()).isEqualTo("APPROVED");
    verify(contractMapper).updateById(any(Contract.class));
    verify(auditService).record("APPROVE", "CONTRACT", 1L, "审批通过：HT-20260822-0001");
  }

  @Test
  @DisplayName("生效：仅已通过可标记；草稿抛 CONTRACT_INVALID_STATE")
  void effectiveStateGuard() {
    when(contractMapper.selectById(1L)).thenReturn(contract(1L, "DRAFT"));

    assertThatThrownBy(() -> service.effective(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTRACT_INVALID_STATE);
  }

  @Test
  @DisplayName("终止：缺少原因抛 BAD_REQUEST")
  void terminateWithoutReasonThrows() {
    assertThatThrownBy(() -> service.terminate(1L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.BAD_REQUEST);
  }
}
