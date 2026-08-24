package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.dto.merge.DuplicateGroupResponse;
import com.crm.dto.merge.MergeRequest;
import com.crm.entity.Customer;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.CustomerShareMapper;
import com.crm.repository.CustomerTagMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.repository.TicketMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** CustomerMergeService 单元测试（034 T001）：查重 + 合并。 */
class CustomerMergeServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Customer.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.SalesOrder.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.Opportunity.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.Contact.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.FollowUp.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.Ticket.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.CustomerTag.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.CustomerShare.class);
  }

  private CustomerMapper customerMapper;
  private SalesOrderMapper orderMapper;
  private OpportunityMapper oppMapper;
  private ContactMapper contactMapper;
  private FollowUpMapper followUpMapper;
  private TicketMapper ticketMapper;
  private CustomerTagMapper customerTagMapper;
  private CustomerShareMapper customerShareMapper;
  private AuditService auditService;
  private CustomerMergeService service;

  @BeforeEach
  void setUp() {
    customerMapper = mock(CustomerMapper.class);
    orderMapper = mock(SalesOrderMapper.class);
    oppMapper = mock(OpportunityMapper.class);
    contactMapper = mock(ContactMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    ticketMapper = mock(TicketMapper.class);
    customerTagMapper = mock(CustomerTagMapper.class);
    customerShareMapper = mock(CustomerShareMapper.class);
    auditService = mock(AuditService.class);
    service =
        new CustomerMergeService(
            customerMapper,
            orderMapper,
            oppMapper,
            contactMapper,
            followUpMapper,
            ticketMapper,
            customerTagMapper,
            customerShareMapper,
            auditService);
  }

  private Customer customer(Long id, String name, String company, String phone, String email) {
    Customer c = new Customer();
    c.setId(id);
    c.setName(name);
    c.setCompany(company);
    c.setPhone(phone);
    c.setEmail(email);
    return c;
  }

  @Test
  @DisplayName("名称归一化：去空格/全角转半角/小写")
  void normalizeWorks() {
    assertThat(CustomerMergeService.normalize(" Ａｃｍｅ  科技 ")).isEqualTo("acme科技");
  }

  @Test
  @DisplayName("查重：名称相同客户检出重复组")
  void scanFindsNameDuplicates() {
    when(customerMapper.selectList(any()))
        .thenReturn(
            List.of(
                customer(1L, "Acme 科技", "Acme Inc.", null, null),
                customer(2L, "Acme 科技", "Acme Inc.", null, null),
                customer(3L, "其他公司", "Other", null, null)));

    List<DuplicateGroupResponse> groups = service.scanDuplicates();

    assertThat(groups).isNotEmpty();
    DuplicateGroupResponse g = groups.get(0);
    assertThat(g.getDuplicates()).hasSize(1);
    assertThat(g.getDuplicates().get(0).getSimilarity()).isEqualTo(100);
  }

  @Test
  @DisplayName("查重：电话相同检出重复（85）")
  void scanFindsPhoneDuplicates() {
    when(customerMapper.selectList(any()))
        .thenReturn(
            List.of(
                customer(1L, "A公司", "A", "13800000000", null),
                customer(2L, "B公司", "B", "13800000000", null)));

    List<DuplicateGroupResponse> groups = service.scanDuplicates();

    assertThat(groups).isNotEmpty();
    assertThat(groups.get(0).getDuplicates().get(0).getSimilarity()).isEqualTo(85);
  }

  @Test
  @DisplayName("合并：主保留 + 从删除 + 审计")
  void mergeTransfersAndDeletes() {
    Customer primary = customer(1L, "主客户", "Main", null, null);
    Customer duplicate = customer(2L, "重复客户", "Dup", "13900000000", null);
    when(customerMapper.selectById(1L)).thenReturn(primary);
    when(customerMapper.selectById(2L)).thenReturn(duplicate);
    when(orderMapper.update(any(), any())).thenReturn(2);
    when(oppMapper.update(any(), any())).thenReturn(1);
    when(contactMapper.update(any(), any())).thenReturn(0);
    when(followUpMapper.update(any(), any())).thenReturn(0);
    when(ticketMapper.update(any(), any())).thenReturn(0);

    MergeRequest req = new MergeRequest();
    req.setPrimaryId(1L);
    req.setDuplicateId(2L);
    Map<String, Object> result = service.merge(req);

    assertThat(result.get("movedOrders")).isEqualTo(2L);
    assertThat(result.get("movedOpportunities")).isEqualTo(1L);
    verify(customerMapper).deleteById(2L);
    verify(auditService)
        .record(Mockito.eq("MERGE"), Mockito.eq("CUSTOMER"), Mockito.eq(1L), Mockito.anyString());
  }
}
