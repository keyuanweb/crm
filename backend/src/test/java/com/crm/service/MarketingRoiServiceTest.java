package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.dto.marketing.ChannelRoiResponse;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.MarketingCampaign;
import com.crm.entity.Opportunity;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.MarketingCampaignMapper;
import com.crm.repository.OpportunityMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MarketingRoiService 单元测试（014 T025）：渠道聚合/ROI/除零。 */
class MarketingRoiServiceTest {

  /** 纯 Mockito 测试无 Spring 上下文：注册实体 TableInfo，供 LambdaQueryWrapper 解析列名。 */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, MarketingCampaign.class);
    TableInfoHelper.initTableInfo(assistant, Lead.class);
    TableInfoHelper.initTableInfo(assistant, Customer.class);
    TableInfoHelper.initTableInfo(assistant, Opportunity.class);
  }

  private MarketingCampaignMapper campaignMapper;
  private LeadMapper leadMapper;
  private CustomerMapper customerMapper;
  private OpportunityMapper opportunityMapper;
  private MarketingRoiService service;

  @BeforeEach
  void setUp() {
    campaignMapper = mock(MarketingCampaignMapper.class);
    leadMapper = mock(LeadMapper.class);
    customerMapper = mock(CustomerMapper.class);
    opportunityMapper = mock(OpportunityMapper.class);
    service =
        new MarketingRoiService(campaignMapper, leadMapper, customerMapper, opportunityMapper);
  }

  private MarketingCampaign campaign(Long id, String channel, long cost) {
    MarketingCampaign c = new MarketingCampaign();
    c.setId(id);
    c.setChannel(channel);
    c.setCost(cost);
    return c;
  }

  @Test
  @DisplayName("渠道 ROI：聚合/转化率/收益/ROI 计算正确")
  void channelRoiAggregates() {
    when(campaignMapper.selectList(any()))
        .thenReturn(
            List.of(
                campaign(1L, "AD", 100000L),
                campaign(2L, "AD", 60000L),
                campaign(3L, "EMAIL", 0L)));
    Lead l1 = new Lead();
    l1.setCampaignId(1L);
    Lead l2 = new Lead();
    l2.setCampaignId(2L);
    Lead l3 = new Lead();
    l3.setCampaignId(3L);
    when(leadMapper.selectList(any())).thenReturn(List.of(l1, l2, l3));
    Customer c1 = new Customer();
    c1.setId(10L);
    c1.setCampaignId(1L);
    Customer c2 = new Customer();
    c2.setId(11L);
    c2.setCampaignId(1L);
    Customer c3 = new Customer();
    c3.setId(12L);
    c3.setCampaignId(2L);
    when(customerMapper.selectList(any())).thenReturn(List.of(c1, c2, c3));
    Opportunity o1 = new Opportunity();
    o1.setCustomerId(10L);
    o1.setExpectedAmountMax(300000L);
    Opportunity o2 = new Opportunity();
    o2.setCustomerId(11L);
    o2.setExpectedAmountMax(200000L);
    Opportunity o3 = new Opportunity();
    o3.setCustomerId(12L);
    o3.setExpectedAmountMax(400000L);
    when(opportunityMapper.selectList(any())).thenReturn(List.of(o1, o2, o3));

    List<ChannelRoiResponse> result = service.channelRoi();

    // AD：2 活动、成本 160000、线索 2、客户 3、收益 900000、ROI=5.625
    ChannelRoiResponse ad =
        result.stream().filter(r -> "AD".equals(r.getChannel())).findFirst().orElseThrow();
    assertThat(ad.getCampaignCount()).isEqualTo(2);
    assertThat(ad.getTotalCost()).isEqualTo(160000L);
    assertThat(ad.getLeadCount()).isEqualTo(2);
    assertThat(ad.getCustomerCount()).isEqualTo(3);
    assertThat(ad.getEstimatedRevenue()).isEqualTo(900000L);
    assertThat(ad.getConversionRate()).isEqualTo(1.5);
    assertThat(ad.getRoi()).isEqualTo(5.625);

    // EMAIL：成本 0 → ROI null
    ChannelRoiResponse email =
        result.stream().filter(r -> "EMAIL".equals(r.getChannel())).findFirst().orElseThrow();
    assertThat(email.getTotalCost()).isZero();
    assertThat(email.getRoi()).isNull();
  }

  @Test
  @DisplayName("无活动返回空列表")
  void noCampaigns() {
    when(campaignMapper.selectList(any())).thenReturn(List.of());

    var result = service.channelRoi();

    assertThat(result).isEmpty();
  }
}
