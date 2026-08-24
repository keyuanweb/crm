package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.PageResult;
import com.crm.dto.recycle.RecycleItem;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** RecycleBinService 单元测试（025 T002）：列表/恢复/冲突/权限。 */
class RecycleBinServiceTest {

  private CustomerMapper customerMapper;
  private LeadMapper leadMapper;
  private ContactMapper contactMapper;
  private OpportunityMapper oppMapper;
  private AuditService auditService;
  private RecycleBinService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    customerMapper = mock(CustomerMapper.class);
    leadMapper = mock(LeadMapper.class);
    contactMapper = mock(ContactMapper.class);
    oppMapper = mock(OpportunityMapper.class);
    auditService = mock(AuditService.class);
    service =
        new RecycleBinService(customerMapper, leadMapper, contactMapper, oppMapper, auditService);
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

  private Customer deletedCustomer(Long id, String name) {
    Customer c = new Customer();
    c.setId(id);
    c.setName(name);
    c.setCompany("公司");
    c.setUpdatedAt(LocalDateTime.now());
    return c;
  }

  private Lead deletedLead(Long id, String name) {
    Lead l = new Lead();
    l.setId(id);
    l.setName(name);
    l.setUpdatedAt(LocalDateTime.now());
    return l;
  }

  @Test
  @DisplayName("ADMIN 合并 4 实体已删记录返回列表")
  void adminListsAll() {
    when(customerMapper.selectDeletedAll()).thenReturn(List.of(deletedCustomer(1L, "客户A")));
    when(leadMapper.selectDeletedAll()).thenReturn(List.of(deletedLead(2L, "线索B")));
    when(contactMapper.selectDeletedAll()).thenReturn(List.of());
    when(oppMapper.selectDeletedAll()).thenReturn(List.of());

    PageResult<RecycleItem> result = service.list(null, null, 1, 20);

    assertThat(result.getTotal()).isEqualTo(2);
    assertThat(result.getItems().get(0).getType()).isEqualTo("CUSTOMER");
    assertThat(result.getItems().get(0).getName()).isEqualTo("客户A");
    assertThat(result.getItems().get(1).getType()).isEqualTo("LEAD");
  }

  @Test
  @DisplayName("按类型过滤")
  void filterByType() {
    when(customerMapper.selectDeletedAll()).thenReturn(List.of(deletedCustomer(1L, "客户A")));
    when(leadMapper.selectDeletedAll()).thenReturn(List.of(deletedLead(2L, "线索B")));
    when(contactMapper.selectDeletedAll()).thenReturn(List.of());
    when(oppMapper.selectDeletedAll()).thenReturn(List.of());

    PageResult<RecycleItem> result = service.list("LEAD", null, 1, 20);

    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getItems().get(0).getType()).isEqualTo("LEAD");
  }

  @Test
  @DisplayName("恢复：客户唯一性冲突跳过，其余恢复")
  void restoreWithConflict() {
    when(customerMapper.selectCount(org.mockito.ArgumentMatchers.any()))
        .thenReturn(1L) // 客户 1 冲突（已存在同名同公司）
        .thenReturn(0L); // 客户 3 无冲突
    when(customerMapper.restoreById(anyLong())).thenReturn(1);

    Map<String, Object> result =
        service.restore(List.of(new RecycleItem("CUSTOMER", 1L), new RecycleItem("CUSTOMER", 3L)));

    assertThat(result.get("restoredCount")).isEqualTo(1);
    assertThat((java.util.List<?>) result.get("failures")).hasSize(1);
    verify(auditService)
        .record(
            org.mockito.ArgumentMatchers.eq("RESTORE"),
            org.mockito.ArgumentMatchers.eq("CUSTOMER"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.contains("恢复"));
  }

  @Test
  @DisplayName("彻底删除分发到对应 Mapper")
  void purge() {
    when(customerMapper.purgeById(1L)).thenReturn(1);

    Map<String, Object> result = service.purge(List.of(new RecycleItem("CUSTOMER", 1L)));

    assertThat(result.get("purgedCount")).isEqualTo(1);
    verify(customerMapper).purgeById(1L);
  }
}
