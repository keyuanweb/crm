package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.SalesOrder;
import com.crm.entity.Segment;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.repository.SegmentMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** SegmentService 单元测试（031 T005）：条件解析 + 成员计算。 */
class SegmentServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Customer.class);
    TableInfoHelper.initTableInfo(assistant, SalesOrder.class);
    TableInfoHelper.initTableInfo(assistant, FollowUp.class);
    TableInfoHelper.initTableInfo(assistant, Segment.class);
  }

  private SegmentMapper segmentMapper;
  private CustomerMapper customerMapper;
  private TagService tagService;
  private SalesOrderMapper orderMapper;
  private FollowUpMapper followUpMapper;
  private AuditService auditService;
  private SegmentService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    segmentMapper = mock(SegmentMapper.class);
    customerMapper = mock(CustomerMapper.class);
    tagService = mock(TagService.class);
    orderMapper = mock(SalesOrderMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    auditService = mock(AuditService.class);
    service =
        new SegmentService(
            segmentMapper, customerMapper, tagService, orderMapper, followUpMapper, auditService);
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

  private Segment seg(String name, String conditions) {
    Segment s = new Segment();
    s.setId(1L);
    s.setName(name);
    s.setConditions(conditions);
    return s;
  }

  private Customer cust(Long id) {
    Customer c = new Customer();
    c.setId(id);
    return c;
  }

  @Test
  @DisplayName("ADMIN 可见全部客户，无条件细分返回全部")
  void allCustomersNoCondition() {
    when(customerMapper.selectList(any())).thenReturn(List.of(cust(1L), cust(2L), cust(3L)));

    List<Long> members = service.members(seg("全部", "{\"logic\":\"AND\",\"filters\":[]}"));

    assertThat(members).containsExactlyInAnyOrder(1L, 2L, 3L);
  }

  @Test
  @DisplayName("按标签条件过滤（AND）")
  void tagFilterAnd() {
    when(customerMapper.selectList(any())).thenReturn(List.of(cust(1L), cust(2L), cust(3L)));
    when(tagService.customerIdsByTagNames(List.of("VIP"))).thenReturn(List.of(1L, 2L));
    when(orderMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());

    Segment s =
        seg(
            "VIP",
            "{\"logic\":\"AND\",\"filters\":[{\"field\":\"tag\",\"op\":\"IN\",\"values\":[\"VIP\"]},{\"field\":\"amount\",\"op\":\"GT\",\"value\":100}]}");
    List<Long> members = service.members(s);

    // VIP = [1,2]，金额 >100 = []（无订单）→ AND 交集 = []
    assertThat(members).isEmpty();
  }

  @Test
  @DisplayName("按金额过滤（GT）")
  void amountFilter() {
    when(customerMapper.selectList(any())).thenReturn(List.of(cust(1L), cust(2L)));
    SalesOrder o1 = new SalesOrder();
    o1.setCustomerId(1L);
    o1.setAmount(200000L);
    SalesOrder o2 = new SalesOrder();
    o2.setCustomerId(2L);
    o2.setAmount(50000L);
    when(orderMapper.selectList(any())).thenReturn(List.of(o1, o2));

    Segment s =
        seg(
            "大额",
            "{\"logic\":\"AND\",\"filters\":[{\"field\":\"amount\",\"op\":\"GT\",\"value\":100000}]}");
    List<Long> members = service.members(s);

    assertThat(members).containsExactly(1L);
  }

  @Test
  @DisplayName("按最近跟进天数过滤（GT 30）")
  void followUpFilter() {
    when(customerMapper.selectList(any())).thenReturn(List.of(cust(1L), cust(2L)));
    FollowUp f1 = new FollowUp();
    f1.setCustomerId(1L);
    f1.setCreatedAt(LocalDateTime.now().minusDays(5));
    when(followUpMapper.selectList(any())).thenReturn(List.of(f1));

    Segment s =
        seg(
            "久未跟进",
            "{\"logic\":\"AND\",\"filters\":[{\"field\":\"lastFollowUpDays\",\"op\":\"GT\",\"value\":30}]}");
    List<Long> members = service.members(s);

    // 客户 1 最近跟进 5 天前（不匹配）；客户 2 无跟进（999 天，匹配）
    assertThat(members).containsExactly(2L);
  }

  @Test
  @DisplayName("OR 逻辑合并")
  void orLogic() {
    when(customerMapper.selectList(any())).thenReturn(List.of(cust(1L), cust(2L)));
    when(tagService.customerIdsByTagNames(List.of("VIP"))).thenReturn(List.of(1L));
    when(orderMapper.selectList(any())).thenReturn(List.of());

    Segment s =
        seg(
            "VIP或高金额",
            "{\"logic\":\"OR\",\"filters\":[{\"field\":\"tag\",\"op\":\"IN\",\"values\":[\"VIP\"]},{\"field\":\"amount\",\"op\":\"GT\",\"value\":100000}]}");
    List<Long> members = service.members(s);

    assertThat(members).contains(1L);
  }
}
