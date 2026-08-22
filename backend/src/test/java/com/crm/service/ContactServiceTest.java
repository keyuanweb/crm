package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.contact.ContactRequest;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** ContactService 单元测试（T012）：客户校验/唯一性/逻辑删除/乐观锁。 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

  private ContactMapper contactMapper;
  private CustomerMapper customerMapper;
  private AuditService auditService;
  private ContactService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    contactMapper = mock(ContactMapper.class);
    customerMapper = mock(CustomerMapper.class);
    auditService = mock(AuditService.class);
    service = new ContactService(contactMapper, customerMapper, auditService);
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

  private Contact contact(Long id) {
    Contact c = new Contact();
    c.setId(id);
    c.setCustomerId(10L);
    c.setName("张三");
    c.setPhone("13800138000");
    c.setRole("DECISION_MAKER");
    c.setVersion(0);
    return c;
  }

  private ContactRequest request(String name, String phone) {
    ContactRequest req = new ContactRequest();
    req.setCustomerId(10L);
    req.setName(name);
    req.setPhone(phone);
    return req;
  }

  @Test
  @DisplayName("创建联系人成功：默认角色 OTHER，记录审计")
  void createSucceeds() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(contactMapper.selectCount(any())).thenReturn(0L);
    when(contactMapper.insert(any(Contact.class)))
        .thenAnswer(
            invocation -> {
              Contact c = invocation.getArgument(0);
              c.setId(1L);
              return 1;
            });

    var resp = service.create(request("张三", "13800138000"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getRole()).isEqualTo("OTHER");
    verify(contactMapper).insert(any(Contact.class));
    verify(auditService).record("CREATE", "CONTACT", 1L, "创建联系人：张三");
  }

  @Test
  @DisplayName("创建联系人：客户不存在抛出 CUSTOMER_NOT_FOUND")
  void createCustomerMissingThrows() {
    when(customerMapper.selectById(10L)).thenReturn(null);

    assertThatThrownBy(() -> service.create(request("张三", "13800138000")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND);
    verify(contactMapper, never()).insert(any());
  }

  @Test
  @DisplayName("创建联系人：同客户同名同电话抛出 CONTACT_DUPLICATE")
  void createDuplicateThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(contactMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.create(request("张三", "13800138000")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTACT_DUPLICATE);
    verify(contactMapper, never()).insert(any());
  }

  @Test
  @DisplayName("创建联系人：电话为空时不重复校验空串")
  void createBlankPhoneNotDuplicate() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(contactMapper.selectCount(any())).thenReturn(0L);
    when(contactMapper.insert(any(Contact.class))).thenReturn(1);

    var resp = service.create(request("李四", ""));

    assertThat(resp.getPhone()).isNull();
    verify(contactMapper).insert(any(Contact.class));
  }

  @Test
  @DisplayName("编辑联系人成功：乐观锁版本更新")
  void updateSucceeds() {
    when(contactMapper.selectById(1L)).thenReturn(contact(1L));
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(contactMapper.selectCount(any())).thenReturn(0L);
    when(contactMapper.updateById(any(Contact.class))).thenReturn(1);
    Contact updated = contact(1L);
    updated.setName("张三-改");
    when(contactMapper.selectById(1L)).thenReturn(updated);

    ContactRequest req = request("张三-改", "13800138000");
    req.setVersion(0);
    var resp = service.update(1L, req);

    assertThat(resp.getName()).isEqualTo("张三-改");
    verify(contactMapper).updateById(any(Contact.class));
    verify(auditService).record("UPDATE", "CONTACT", 1L, "编辑联系人：张三-改");
  }

  @Test
  @DisplayName("编辑联系人：不存在抛出 CONTACT_NOT_FOUND")
  void updateMissingThrows() {
    when(contactMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> service.update(99L, request("张三", "13800138000")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTACT_NOT_FOUND);
  }

  @Test
  @DisplayName("编辑联系人：版本冲突抛出 VERSION_CONFLICT")
  void updateVersionConflictThrows() {
    when(contactMapper.selectById(1L)).thenReturn(contact(1L));
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(contactMapper.selectCount(any())).thenReturn(0L);
    when(contactMapper.updateById(any(Contact.class))).thenReturn(0);

    ContactRequest req = request("张三", "13800138000");
    req.setVersion(5);

    assertThatThrownBy(() -> service.update(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERSION_CONFLICT);
  }

  @Test
  @DisplayName("删除联系人成功：逻辑删除 + 审计")
  void deleteSucceeds() {
    when(contactMapper.selectById(1L)).thenReturn(contact(1L));
    doReturn(1).when(contactMapper).deleteById(1L);

    service.delete(1L);

    verify(contactMapper).deleteById(1L);
    verify(auditService).record("DELETE", "CONTACT", 1L, "删除联系人：张三");
  }

  @Test
  @DisplayName("删除联系人：不存在抛出 CONTACT_NOT_FOUND")
  void deleteMissingThrows() {
    when(contactMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> service.delete(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTACT_NOT_FOUND);
    verify(contactMapper, never()).deleteById(org.mockito.ArgumentMatchers.<Long>any());
  }

  @Test
  @DisplayName("分页查询：批量装配客户名，无 N+1")
  void pageAssemblesCustomerNames() {
    Contact c1 = contact(1L);
    c1.setCustomerId(10L);
    Contact c2 = contact(2L);
    c2.setCustomerId(11L);
    c2.setName("王五");
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Contact> p =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
    p.setRecords(List.of(c1, c2));
    p.setTotal(2);
    when(contactMapper.selectPage(any(), any())).thenReturn(p);
    Customer c10 = customer(10L);
    Customer c11 = customer(11L);
    c11.setName("另一客户");
    when(customerMapper.selectBatchIds(List.of(10L, 11L))).thenReturn(List.of(c10, c11));

    var result = service.page(null, null, null, 1, 20);

    assertThat(result.getTotal()).isEqualTo(2);
    assertThat(result.getItems().get(0).getCustomerName()).isEqualTo("测试客户");
    assertThat(result.getItems().get(1).getCustomerName()).isEqualTo("另一客户");
    verify(customerMapper).selectBatchIds(List.of(10L, 11L));
  }
}
