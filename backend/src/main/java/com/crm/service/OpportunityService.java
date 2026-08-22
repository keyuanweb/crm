package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.opportunity.OpportunityDetailResponse;
import com.crm.dto.opportunity.OpportunityRequest;
import com.crm.dto.opportunity.OpportunityResponse;
import com.crm.entity.Customer;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.CustomerMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.security.SecurityUtil;
import com.crm.support.SalesOpportunityAssembler;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 商机服务（父实体，FR-007~011）。 */
@Service
public class OpportunityService {

  private final OpportunityMapper opportunityMapper;
  private final SalesOpportunityMapper salesOpportunityMapper;
  private final CustomerMapper customerMapper;
  private final OpportunityStatsService statsService;
  private final DashboardStatsService dashboardStatsService;
  private final SalesOpportunityAssembler salesOpportunityAssembler;
  private final CustomFieldService customFieldService;

  public OpportunityService(
      OpportunityMapper opportunityMapper,
      SalesOpportunityMapper salesOpportunityMapper,
      CustomerMapper customerMapper,
      OpportunityStatsService statsService,
      DashboardStatsService dashboardStatsService,
      SalesOpportunityAssembler salesOpportunityAssembler,
      CustomFieldService customFieldService) {
    this.opportunityMapper = opportunityMapper;
    this.salesOpportunityMapper = salesOpportunityMapper;
    this.customerMapper = customerMapper;
    this.statsService = statsService;
    this.dashboardStatsService = dashboardStatsService;
    this.salesOpportunityAssembler = salesOpportunityAssembler;
    this.customFieldService = customFieldService;
  }

  public PageResult<OpportunityResponse> page(
      String keyword, Long customerId, String status, long page, long pageSize) {
    LambdaQueryWrapper<Opportunity> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.like(Opportunity::getName, keyword.trim());
    }
    if (customerId != null) {
      qw.eq(Opportunity::getCustomerId, customerId);
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Opportunity::getStatus, status.trim());
    }
    qw.orderByDesc(Opportunity::getId);
    Page<Opportunity> p = opportunityMapper.selectPage(new Page<>(page, pageSize), qw);
    List<OpportunityResponse> items = fillResponses(p.getRecords());
    // 016：批量回填自定义字段值
    List<Long> ids = p.getRecords().stream().map(Opportunity::getId).toList();
    if (!ids.isEmpty()) {
      Map<Long, List<com.crm.dto.customfield.CustomFieldValueDTO>> values =
          customFieldService.readValuesBatch("OPPORTUNITY", ids);
      items.forEach(i -> i.setCustomFieldValues(values.getOrDefault(i.getId(), List.of())));
    }
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  public OpportunityDetailResponse detail(Long id) {
    Opportunity opportunity = require(id);
    OpportunityDetailResponse resp = new OpportunityDetailResponse();
    fillResponse(opportunity, resp);

    List<SalesOpportunity> children =
        salesOpportunityMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .eq(SalesOpportunity::getOpportunityId, id)
                .orderByDesc(SalesOpportunity::getId));
    resp.setSalesOpportunities(salesOpportunityAssembler.assemble(children));
    resp.setCustomFieldValues(customFieldService.readValues("OPPORTUNITY", id));
    return resp;
  }

  @Transactional
  public OpportunityResponse create(OpportunityRequest req) {
    validateAmountRange(req.getExpectedAmountMin(), req.getExpectedAmountMax());
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    Opportunity opportunity = new Opportunity();
    opportunity.setCustomerId(req.getCustomerId());
    opportunity.setName(req.getName().trim());
    opportunity.setExpectedAmountMin(
        req.getExpectedAmountMin() == null ? 0L : req.getExpectedAmountMin());
    opportunity.setExpectedAmountMax(
        req.getExpectedAmountMax() == null ? 0L : req.getExpectedAmountMax());
    opportunity.setRemark(req.getRemark());
    opportunity.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "ACTIVE");
    opportunity.setCreatedBy(SecurityUtil.currentUserId());
    opportunityMapper.insert(opportunity);
    if (req.getCustomFieldValues() != null && !req.getCustomFieldValues().isEmpty()) {
      customFieldService.saveValues("OPPORTUNITY", opportunity.getId(), req.getCustomFieldValues());
    }
    dashboardStatsService.evict();
    return toResponse(opportunity);
  }

  @Transactional
  public OpportunityResponse update(Long id, OpportunityRequest req) {
    Opportunity existing = require(id);
    validateAmountRange(req.getExpectedAmountMin(), req.getExpectedAmountMax());
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    existing.setCustomerId(req.getCustomerId());
    existing.setName(req.getName().trim());
    existing.setExpectedAmountMin(
        req.getExpectedAmountMin() == null ? 0L : req.getExpectedAmountMin());
    existing.setExpectedAmountMax(
        req.getExpectedAmountMax() == null ? 0L : req.getExpectedAmountMax());
    existing.setRemark(req.getRemark());
    if (StringUtils.hasText(req.getStatus())) {
      existing.setStatus(req.getStatus().trim());
    }
    existing.setVersion(req.getVersion());
    int rows = opportunityMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    if (req.getCustomFieldValues() != null && !req.getCustomFieldValues().isEmpty()) {
      customFieldService.saveValues("OPPORTUNITY", id, req.getCustomFieldValues());
    }
    dashboardStatsService.evict();
    return toResponse(opportunityMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    require(id);
    opportunityMapper.deleteById(id);
    // 级联逻辑删除下属销售机会
    salesOpportunityMapper.delete(
        new LambdaQueryWrapper<SalesOpportunity>().eq(SalesOpportunity::getOpportunityId, id));
    statsService.evict();
    dashboardStatsService.evict();
  }

  public Opportunity require(Long id) {
    Opportunity opportunity = opportunityMapper.selectById(id);
    if (opportunity == null) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_NOT_FOUND);
    }
    return opportunity;
  }

  private void validateAmountRange(Long min, Long max) {
    long lo = min == null ? 0L : min;
    long hi = max == null ? 0L : max;
    if (lo < 0 || hi < 0 || lo > hi) {
      throw new BusinessException(ErrorCode.AMOUNT_RANGE_INVALID);
    }
  }

  private List<OpportunityResponse> fillResponses(List<Opportunity> opportunities) {
    if (opportunities.isEmpty()) {
      return List.of();
    }
    List<Long> customerIds =
        opportunities.stream().map(Opportunity::getCustomerId).distinct().toList();
    Map<Long, String> customerNames =
        customerMapper.selectBatchIds(customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
    List<Long> oppIds = opportunities.stream().map(Opportunity::getId).toList();
    Map<Long, Long> soCounts =
        salesOpportunityMapper
            .selectList(
                new LambdaQueryWrapper<SalesOpportunity>()
                    .in(SalesOpportunity::getOpportunityId, oppIds))
            .stream()
            .collect(
                Collectors.groupingBy(SalesOpportunity::getOpportunityId, Collectors.counting()));
    return opportunities.stream()
        .map(
            o -> {
              OpportunityResponse resp = toResponse(o);
              resp.setCustomerName(customerNames.get(o.getCustomerId()));
              resp.setSalesOpportunityCount(soCounts.getOrDefault(o.getId(), 0L).intValue());
              return resp;
            })
        .toList();
  }

  private void fillResponse(Opportunity opportunity, OpportunityResponse resp) {
    resp.setId(opportunity.getId());
    resp.setName(opportunity.getName());
    resp.setCustomerId(opportunity.getCustomerId());
    resp.setExpectedAmountMin(opportunity.getExpectedAmountMin());
    resp.setExpectedAmountMax(opportunity.getExpectedAmountMax());
    resp.setRemark(opportunity.getRemark());
    resp.setStatus(opportunity.getStatus());
    resp.setVersion(opportunity.getVersion());
    resp.setCreatedAt(opportunity.getCreatedAt());
    Customer customer = customerMapper.selectById(opportunity.getCustomerId());
    resp.setCustomerName(customer == null ? null : customer.getName());
    Long count =
        salesOpportunityMapper.selectCount(
            new LambdaQueryWrapper<SalesOpportunity>()
                .eq(SalesOpportunity::getOpportunityId, opportunity.getId()));
    resp.setSalesOpportunityCount(count == null ? 0 : count.intValue());
  }

  private OpportunityResponse toResponse(Opportunity opportunity) {
    OpportunityResponse resp = new OpportunityResponse();
    fillResponse(opportunity, resp);
    return resp;
  }
}
