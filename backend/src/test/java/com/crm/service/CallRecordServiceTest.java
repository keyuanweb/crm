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
import com.crm.dto.call.CallRecordRequest;
import com.crm.entity.CallRecord;
import com.crm.entity.Contact;
import com.crm.repository.CallRecordMapper;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
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

/** CallRecordService 单元测试（061 T008）：CRUD/归属校验/统计。 */
class CallRecordServiceTest {

  private CallRecordMapper recordMapper;
  private CustomerMapper customerMapper;
  private ContactMapper contactMapper;
  private CallRecordService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CallRecord.class);
    TableInfoHelper.initTableInfo(assistant, Contact.class);
  }

  @BeforeEach
  void setUp() {
    recordMapper = mock(CallRecordMapper.class);
    customerMapper = mock(CustomerMapper.class);
    contactMapper = mock(ContactMapper.class);
    service = new CallRecordService(recordMapper, customerMapper, contactMapper);
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

  private CallRecordRequest request(Long contactId) {
    CallRecordRequest req = new CallRecordRequest();
    req.setCustomerId(3L);
    req.setContactId(contactId);
    req.setDirection("OUTBOUND");
    req.setDurationSeconds(300);
    req.setResult("CONNECTED");
    return req;
  }

  private Contact contact(Long id, Long customerId) {
    Contact c = new Contact();
    c.setId(id);
    c.setCustomerId(customerId);
    c.setName("张三");
    return c;
  }

  @Test
  @DisplayName("创建：联系人归属正确 → 成功")
  void createSucceeds() {
    when(contactMapper.selectById(5L)).thenReturn(contact(5L, 3L));
    when(recordMapper.insert(any(CallRecord.class)))
        .thenAnswer(
            invocation -> {
              CallRecord r = invocation.getArgument(0);
              r.setId(10L);
              return 1;
            });
    CallRecord stored = new CallRecord();
    stored.setId(10L);
    stored.setCustomerId(3L);
    stored.setContactId(5L);
    stored.setDirection("OUTBOUND");
    stored.setDurationSeconds(300);
    stored.setResult("CONNECTED");
    when(recordMapper.selectById(10L)).thenReturn(stored);
    com.crm.entity.Customer cust = new com.crm.entity.Customer();
    cust.setId(3L);
    cust.setName("客户A");
    when(customerMapper.selectBatchIds(any())).thenReturn(List.of(cust));
    when(contactMapper.selectBatchIds(any())).thenReturn(List.of(contact(5L, 3L)));

    var resp = service.create(request(5L));

    assertThat(resp.getId()).isEqualTo(10L);
    assertThat(resp.getDirection()).isEqualTo("OUTBOUND");
    assertThat(resp.getCustomerName()).isEqualTo("客户A");
  }

  @Test
  @DisplayName("创建：联系人归属不符 → 422")
  void createContactMismatchThrows() {
    when(contactMapper.selectById(5L)).thenReturn(contact(5L, 99L));

    assertThatThrownBy(() -> service.create(request(5L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CALL_CONTACT_MISMATCH);
  }

  @Test
  @DisplayName("创建：联系人为空（外部号码直录）→ 成功")
  void createWithoutContact() {
    when(recordMapper.insert(any(CallRecord.class)))
        .thenAnswer(
            invocation -> {
              CallRecord r = invocation.getArgument(0);
              r.setId(11L);
              return 1;
            });
    CallRecord stored = new CallRecord();
    stored.setId(11L);
    stored.setDirection("INBOUND");
    stored.setDurationSeconds(0);
    stored.setResult("NO_ANSWER");
    when(recordMapper.selectById(11L)).thenReturn(stored);
    when(customerMapper.selectBatchIds(any())).thenReturn(List.of());
    when(contactMapper.selectBatchIds(any())).thenReturn(List.of());

    var resp = service.create(request(null));

    assertThat(resp.getId()).isEqualTo(11L);
  }

  @Test
  @DisplayName("统计：次数/总时长/平均时长")
  void statsCalculates() {
    CallRecord r1 = new CallRecord();
    r1.setDirection("OUTBOUND");
    r1.setDurationSeconds(300);
    CallRecord r2 = new CallRecord();
    r2.setDirection("OUTBOUND");
    r2.setDurationSeconds(600);
    CallRecord r3 = new CallRecord();
    r3.setDirection("INBOUND");
    r3.setDurationSeconds(120);
    when(recordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));

    var stats = service.stats(null, null, null);

    assertThat(stats.getTotalCount()).isEqualTo(3);
    assertThat(stats.getTotalDurationSeconds()).isEqualTo(1020);
    assertThat(stats.getAvgDurationSeconds()).isEqualTo(340);
    assertThat(stats.getByDirection()).hasSize(2);
  }
}
