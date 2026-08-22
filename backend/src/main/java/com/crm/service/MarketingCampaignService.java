package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.marketing.CampaignRequest;
import com.crm.dto.marketing.CampaignResponse;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.MarketingCampaign;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.MarketingCampaignMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 营销活动服务（014，FR-M01~M05）：CRUD/状态流转/归因计数/删除防护。 */
@Service
public class MarketingCampaignService {

  public static final String STATUS_PLANNING = "PLANNING";
  public static final String STATUS_RUNNING = "RUNNING";
  public static final String STATUS_ENDED = "ENDED";

  private final MarketingCampaignMapper campaignMapper;
  private final LeadMapper leadMapper;
  private final CustomerMapper customerMapper;
  private final AuditService auditService;

  public MarketingCampaignService(
      MarketingCampaignMapper campaignMapper,
      LeadMapper leadMapper,
      CustomerMapper customerMapper,
      AuditService auditService) {
    this.campaignMapper = campaignMapper;
    this.leadMapper = leadMapper;
    this.customerMapper = customerMapper;
    this.auditService = auditService;
  }

  public PageResult<CampaignResponse> page(
      String keyword, String channel, String status, long page, long pageSize) {
    LambdaQueryWrapper<MarketingCampaign> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.like(MarketingCampaign::getName, keyword.trim());
    }
    if (StringUtils.hasText(channel)) {
      qw.eq(MarketingCampaign::getChannel, channel.trim());
    }
    if (StringUtils.hasText(status)) {
      qw.eq(MarketingCampaign::getStatus, status.trim());
    }
    qw.orderByDesc(MarketingCampaign::getId);
    Page<MarketingCampaign> p = campaignMapper.selectPage(new Page<>(page, pageSize), qw);
    List<MarketingCampaign> records = p.getRecords();
    // 批量归因计数（避免 N+1）
    Map<Long, Long> leadCounts = countAttribution(leadMapper, records, "LEAD");
    Map<Long, Long> customerCounts = countAttribution(customerMapper, records, "CUSTOMER");
    List<CampaignResponse> items =
        records.stream()
            .map(
                c -> {
                  CampaignResponse resp = toResponse(c);
                  resp.setLeadCount(leadCounts.getOrDefault(c.getId(), 0L));
                  resp.setCustomerCount(customerCounts.getOrDefault(c.getId(), 0L));
                  return resp;
                })
            .toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  @Transactional
  public CampaignResponse create(CampaignRequest req) {
    validateDates(req);
    MarketingCampaign campaign = new MarketingCampaign();
    apply(req, campaign);
    campaign.setStatus(STATUS_PLANNING);
    campaign.setCreatedBy(SecurityUtil.currentUserId());
    campaignMapper.insert(campaign);
    auditService.record("CREATE", "CAMPAIGN", campaign.getId(), "创建活动：" + campaign.getName());
    return toResponse(campaign);
  }

  @Transactional
  public CampaignResponse update(Long id, CampaignRequest req) {
    MarketingCampaign existing = require(id);
    validateDates(req);
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = campaignMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "CAMPAIGN", id, "编辑活动：" + existing.getName());
    return toResponse(campaignMapper.selectById(id));
  }

  /** 开始：PLANNING→RUNNING。 */
  @Transactional
  public CampaignResponse start(Long id) {
    MarketingCampaign campaign = require(id);
    if (!STATUS_PLANNING.equals(campaign.getStatus())) {
      throw new BusinessException(ErrorCode.CAMPAIGN_INVALID_STATE);
    }
    campaign.setStatus(STATUS_RUNNING);
    campaignMapper.updateById(campaign);
    auditService.record("START", "CAMPAIGN", id, "活动开始：" + campaign.getName());
    return toResponse(campaignMapper.selectById(id));
  }

  /** 结束：RUNNING→ENDED（ENDED 不可回退）。 */
  @Transactional
  public CampaignResponse end(Long id) {
    MarketingCampaign campaign = require(id);
    if (!STATUS_RUNNING.equals(campaign.getStatus())) {
      throw new BusinessException(ErrorCode.CAMPAIGN_INVALID_STATE);
    }
    campaign.setStatus(STATUS_ENDED);
    campaignMapper.updateById(campaign);
    auditService.record("END", "CAMPAIGN", id, "活动结束：" + campaign.getName());
    return toResponse(campaignMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    MarketingCampaign campaign = require(id);
    Long leads = leadMapper.selectCount(new LambdaQueryWrapper<Lead>().eq(Lead::getCampaignId, id));
    Long customers =
        customerMapper.selectCount(
            new LambdaQueryWrapper<Customer>().eq(Customer::getCampaignId, id));
    if ((leads != null && leads > 0) || (customers != null && customers > 0)) {
      throw new BusinessException(ErrorCode.CAMPAIGN_HAS_ATTRIBUTION);
    }
    campaignMapper.deleteById(id);
    auditService.record("DELETE", "CAMPAIGN", id, "删除活动：" + campaign.getName());
  }

  public MarketingCampaign require(Long id) {
    MarketingCampaign campaign = campaignMapper.selectById(id);
    if (campaign == null) {
      throw new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
    }
    return campaign;
  }

  private void validateDates(CampaignRequest req) {
    if (req.getStartDate() != null
        && req.getEndDate() != null
        && req.getStartDate().isAfter(req.getEndDate())) {
      throw new BusinessException(ErrorCode.AMOUNT_RANGE_INVALID);
    }
  }

  private void apply(CampaignRequest req, MarketingCampaign campaign) {
    campaign.setName(req.getName().trim());
    campaign.setChannel(req.getChannel().trim());
    campaign.setBudget(req.getBudget() == null ? 0L : req.getBudget());
    campaign.setCost(req.getCost() == null ? 0L : req.getCost());
    campaign.setStartDate(req.getStartDate());
    campaign.setEndDate(req.getEndDate());
  }

  /** 批量归因计数：campaign_id → count。 */
  private <T> Map<Long, Long> countAttribution(
      com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
      List<MarketingCampaign> campaigns,
      String entityType) {
    List<Long> ids = campaigns.stream().map(MarketingCampaign::getId).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    if ("LEAD".equals(entityType)) {
      return leadMapper
          .selectList(new LambdaQueryWrapper<Lead>().in(Lead::getCampaignId, ids))
          .stream()
          .collect(Collectors.groupingBy(Lead::getCampaignId, Collectors.counting()));
    }
    return customerMapper
        .selectList(new LambdaQueryWrapper<Customer>().in(Customer::getCampaignId, ids))
        .stream()
        .collect(Collectors.groupingBy(Customer::getCampaignId, Collectors.counting()));
  }

  private CampaignResponse toResponse(MarketingCampaign campaign) {
    CampaignResponse resp = new CampaignResponse();
    resp.setId(campaign.getId());
    resp.setName(campaign.getName());
    resp.setChannel(campaign.getChannel());
    resp.setBudget(campaign.getBudget());
    resp.setCost(campaign.getCost());
    resp.setStartDate(campaign.getStartDate());
    resp.setEndDate(campaign.getEndDate());
    resp.setStatus(campaign.getStatus());
    resp.setVersion(campaign.getVersion());
    resp.setCreatedAt(campaign.getCreatedAt());
    return resp;
  }
}
