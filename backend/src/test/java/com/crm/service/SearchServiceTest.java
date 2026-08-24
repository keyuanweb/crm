package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.dto.search.SearchResponse;
import com.crm.entity.Customer;
import com.crm.entity.Product;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.ProductMapper;
import com.crm.repository.TicketMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** SearchService 单元测试（032 T001）。 */
class SearchServiceTest {

  private CustomerMapper customerMapper;
  private LeadMapper leadMapper;
  private ContactMapper contactMapper;
  private OpportunityMapper oppMapper;
  private TicketMapper ticketMapper;
  private ProductMapper productMapper;
  private SearchService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    customerMapper = mock(CustomerMapper.class);
    leadMapper = mock(LeadMapper.class);
    contactMapper = mock(ContactMapper.class);
    oppMapper = mock(OpportunityMapper.class);
    ticketMapper = mock(TicketMapper.class);
    productMapper = mock(ProductMapper.class);
    service =
        new SearchService(
            customerMapper, leadMapper, contactMapper, oppMapper, ticketMapper, productMapper);
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
  @DisplayName("ADMIN 搜索客户返回分组结果")
  void searchCustomers() {
    Customer c = new Customer();
    c.setId(1L);
    c.setName("Acme 科技");
    c.setCompany("Acme Inc.");
    when(customerMapper.selectList(any())).thenReturn(List.of(c));
    when(leadMapper.selectList(any())).thenReturn(List.of());
    when(contactMapper.selectList(any())).thenReturn(List.of());
    when(oppMapper.selectList(any())).thenReturn(List.of());
    when(ticketMapper.selectList(any())).thenReturn(List.of());
    when(productMapper.selectList(any())).thenReturn(List.of());

    SearchResponse resp = service.search("acme");

    assertThat(resp.getGroups()).isNotEmpty();
    SearchResponse.SearchGroup g = resp.getGroups().get(0);
    assertThat(g.getType()).isEqualTo("CUSTOMER");
    assertThat(g.getItems().get(0).getTitle()).isEqualTo("Acme 科技");
    assertThat(g.getItems().get(0).getPath()).isEqualTo("/customers/1");
  }

  @Test
  @DisplayName("按类型过滤只查该实体")
  void filterByType() {
    Product p = new Product();
    p.setId(5L);
    p.setName("CRM 企业版");
    p.setCode("CRM-ENT");
    when(productMapper.selectList(any())).thenReturn(List.of(p));

    SearchResponse resp = service.searchFull("CRM", "PRODUCT");

    assertThat(resp.getGroups()).hasSize(1);
    assertThat(resp.getGroups().get(0).getType()).isEqualTo("PRODUCT");
  }

  @Test
  @DisplayName("无关键字返回空")
  void emptyKeyword() {
    SearchResponse resp = service.search("  ");

    assertThat(resp.getGroups()).isEmpty();
  }
}
