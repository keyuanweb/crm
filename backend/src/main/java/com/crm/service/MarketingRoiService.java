package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.marketing.ChannelRoiResponse;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.MarketingCampaign;
import com.crm.entity.Opportunity;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.MarketingCampaignMapper;
import com.crm.repository.OpportunityMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 渠道 ROI 统计（014，FR-M06/M07）。 */
@Service
public class MarketingRoiService {

  private final MarketingCampaignMapper campaignMapper;
  private final LeadMapper leadMapper;
  private final CustomerMapper customerMapper;
  private final OpportunityMapper opportunityMapper;

  public MarketingRoiService(
      MarketingCampaignMapper campaignMapper,
      LeadMapper leadMapper,
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper) {
    this.campaignMapper = campaignMapper;
    this.leadMapper = leadMapper;
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
  }

  public List<ChannelRoiResponse> channelRoi() {
    List<MarketingCampaign> campaigns =
        campaignMapper.selectList(
            new LambdaQueryWrapper<MarketingCampaign>()
                .select(
                    MarketingCampaign::getId,
                    MarketingCampaign::getChannel,
                    MarketingCampaign::getCost));
    Map<String, List<Long>> campaignIdsByChannel = new LinkedHashMap<>();
    Map<Long, String> channelByCampaign = new LinkedHashMap<>();
    for (MarketingCampaign c : campaigns) {
      channelByCampaign.put(c.getId(), c.getChannel());
      campaignIdsByChannel.computeIfAbsent(c.getChannel(), k -> new ArrayList<>()).add(c.getId());
    }
    if (campaigns.isEmpty()) {
      return List.of();
    }
    List<Long> allIds = campaigns.stream().map(MarketingCampaign::getId).toList();

    // 归因线索/客户（按 campaign_id）
    Map<Long, Long> leadsByCampaign =
        leadMapper
            .selectList(new LambdaQueryWrapper<Lead>().in(Lead::getCampaignId, allIds))
            .stream()
            .collect(Collectors.groupingBy(Lead::getCampaignId, Collectors.counting()));
    Map<Long, Long> customersByCampaign =
        customerMapper
            .selectList(new LambdaQueryWrapper<Customer>().in(Customer::getCampaignId, allIds))
            .stream()
            .collect(Collectors.groupingBy(Customer::getCampaignId, Collectors.counting()));

    // 归因客户 → 商机预估收益（expected_amount_max 合计）
    List<Customer> attributedCustomers =
        customerMapper.selectList(
            new LambdaQueryWrapper<Customer>().in(Customer::getCampaignId, allIds));
    Map<Long, Long> revenueByCustomer = new LinkedHashMap<>();
    if (!attributedCustomers.isEmpty()) {
      List<Long> customerIds =
          attributedCustomers.stream().map(Customer::getId).distinct().toList();
      Map<Long, Long> revenue =
          opportunityMapper
              .selectList(
                  new LambdaQueryWrapper<Opportunity>()
                      .select(Opportunity::getCustomerId, Opportunity::getExpectedAmountMax)
                      .in(Opportunity::getCustomerId, customerIds))
              .stream()
              .collect(
                  Collectors.groupingBy(
                      Opportunity::getCustomerId,
                      Collectors.summingLong(
                          o -> o.getExpectedAmountMax() == null ? 0L : o.getExpectedAmountMax())));
      for (Customer c : attributedCustomers) {
        revenueByCustomer.merge(c.getCampaignId(), revenue.getOrDefault(c.getId(), 0L), Long::sum);
      }
    }

    List<ChannelRoiResponse> result = new ArrayList<>();
    for (Map.Entry<String, List<Long>> entry : campaignIdsByChannel.entrySet()) {
      String channel = entry.getKey();
      List<Long> ids = entry.getValue();
      long campaignCount = ids.size();
      long totalCost =
          campaigns.stream()
              .filter(c -> ids.contains(c.getId()))
              .mapToLong(c -> c.getCost() == null ? 0L : c.getCost())
              .sum();
      long leadCount = ids.stream().mapToLong(id -> leadsByCampaign.getOrDefault(id, 0L)).sum();
      long customerCount =
          ids.stream().mapToLong(id -> customersByCampaign.getOrDefault(id, 0L)).sum();
      long estimatedRevenue =
          ids.stream().mapToLong(id -> revenueByCustomer.getOrDefault(id, 0L)).sum();
      Double conversionRate = leadCount == 0 ? null : (double) customerCount / leadCount;
      Double roi = totalCost == 0 ? null : (double) estimatedRevenue / totalCost;

      ChannelRoiResponse resp = new ChannelRoiResponse();
      resp.setChannel(channel);
      resp.setCampaignCount(campaignCount);
      resp.setTotalCost(totalCost);
      resp.setLeadCount(leadCount);
      resp.setCustomerCount(customerCount);
      resp.setConversionRate(conversionRate);
      resp.setEstimatedRevenue(estimatedRevenue);
      resp.setRoi(roi);
      result.add(resp);
    }
    return result;
  }
}
