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
import com.crm.dto.marketing.CampaignRequest;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.MarketingCampaign;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.MarketingCampaignMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** MarketingCampaignService 单元测试（014 T010/T019）：CRUD/状态流转/删除防护。 */
@ExtendWith(MockitoExtension.class)
class MarketingCampaignServiceTest {

  private MarketingCampaignMapper campaignMapper;
  private LeadMapper leadMapper;
  private CustomerMapper customerMapper;
  private AuditService auditService;
  private MarketingCampaignService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    campaignMapper = mock(MarketingCampaignMapper.class);
    leadMapper = mock(LeadMapper.class);
    customerMapper = mock(CustomerMapper.class);
    auditService = mock(AuditService.class);
    service =
        new MarketingCampaignService(campaignMapper, leadMapper, customerMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private CampaignRequest request(String name) {
    CampaignRequest req = new CampaignRequest();
    req.setName(name);
    req.setChannel("AD");
    req.setBudget(100000L);
    req.setCost(50000L);
    return req;
  }

  private MarketingCampaign campaign(Long id, String status) {
    MarketingCampaign c = new MarketingCampaign();
    c.setId(id);
    c.setName("广告活动");
    c.setChannel("AD");
    c.setStatus(status);
    c.setVersion(0);
    return c;
  }

  @Test
  @DisplayName("创建活动成功：默认 PLANNING，审计记录")
  void createSucceeds() {
    when(campaignMapper.insert(any(MarketingCampaign.class)))
        .thenAnswer(
            invocation -> {
              MarketingCampaign c = invocation.getArgument(0);
              c.setId(1L);
              return 1;
            });

    var resp = service.create(request("广告活动"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("PLANNING");
    verify(campaignMapper).insert(any(MarketingCampaign.class));
    verify(auditService).record("CREATE", "CAMPAIGN", 1L, "创建活动：广告活动");
  }

  @Test
  @DisplayName("日期校验：开始晚于结束抛出 AMOUNT_RANGE_INVALID")
  void invalidDatesThrows() {
    CampaignRequest req = request("活动A");
    req.setStartDate(LocalDate.of(2026, 10, 1));
    req.setEndDate(LocalDate.of(2026, 9, 1));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AMOUNT_RANGE_INVALID);
    verify(campaignMapper, never()).insert(any());
  }

  @Test
  @DisplayName("开始：PLANNING→RUNNING")
  void startTransitions() {
    MarketingCampaign running = campaign(1L, "RUNNING");
    when(campaignMapper.selectById(1L)).thenReturn(campaign(1L, "PLANNING"), running);
    when(campaignMapper.updateById(any(MarketingCampaign.class))).thenReturn(1);

    var resp = service.start(1L);

    assertThat(resp.getStatus()).isEqualTo("RUNNING");
    verify(auditService).record("START", "CAMPAIGN", 1L, "活动开始：广告活动");
  }

  @Test
  @DisplayName("结束：非 RUNNING 状态抛出 CAMPAIGN_INVALID_STATE")
  void endInvalidStateThrows() {
    when(campaignMapper.selectById(1L)).thenReturn(campaign(1L, "ENDED"));

    assertThatThrownBy(() -> service.end(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CAMPAIGN_INVALID_STATE);
  }

  @Test
  @DisplayName("删除：有归因线索抛出 CAMPAIGN_HAS_ATTRIBUTION")
  void deleteWithAttributionThrows() {
    when(campaignMapper.selectById(1L)).thenReturn(campaign(1L, "PLANNING"));
    when(leadMapper.selectCount(any())).thenReturn(2L);
    when(customerMapper.selectCount(any())).thenReturn(0L);

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CAMPAIGN_HAS_ATTRIBUTION);
    verify(campaignMapper, never()).deleteById(org.mockito.ArgumentMatchers.<Long>any());
  }

  @Test
  @DisplayName("删除：无归因成功")
  void deleteSucceeds() {
    when(campaignMapper.selectById(1L)).thenReturn(campaign(1L, "PLANNING"));
    when(leadMapper.selectCount(any())).thenReturn(0L);
    when(customerMapper.selectCount(any())).thenReturn(0L);

    service.delete(1L);

    verify(campaignMapper).deleteById(org.mockito.ArgumentMatchers.<Long>any());
    verify(auditService).record("DELETE", "CAMPAIGN", 1L, "删除活动：广告活动");
  }

  @Test
  @DisplayName("Lead/Customer 归因字段映射")
  void attributionFields() {
    Lead lead = new Lead();
    lead.setCampaignId(5L);
    assertThat(lead.getCampaignId()).isEqualTo(5L);
    Customer customer = new Customer();
    customer.setCampaignId(5L);
    assertThat(customer.getCampaignId()).isEqualTo(5L);
  }
}
