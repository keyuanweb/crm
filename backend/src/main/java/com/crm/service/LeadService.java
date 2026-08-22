package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.followup.FollowUpBrief;
import com.crm.dto.lead.ConvertRequest;
import com.crm.dto.lead.LeadDetailResponse;
import com.crm.dto.lead.LeadRequest;
import com.crm.dto.lead.LeadResponse;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.Lead;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 线索服务：CRUD、线索池、分配/领取、转化为客户+商机（FR-L01~13）。 */
@Service
public class LeadService {

  private static final String STATUS_NEW = "NEW";
  private static final String STATUS_WORKING = "WORKING";
  private static final String STATUS_QUALIFIED = "QUALIFIED";
  private static final String STATUS_DISQUALIFIED = "DISQUALIFIED";

  private final LeadMapper leadMapper;
  private final CustomerMapper customerMapper;
  private final OpportunityMapper opportunityMapper;
  private final SalesOpportunityMapper salesOpportunityMapper;
  private final FollowUpMapper followUpMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public LeadService(
      LeadMapper leadMapper,
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper,
      SalesOpportunityMapper salesOpportunityMapper,
      FollowUpMapper followUpMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.leadMapper = leadMapper;
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
    this.salesOpportunityMapper = salesOpportunityMapper;
    this.followUpMapper = followUpMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  public PageResult<LeadResponse> page(
      String keyword,
      String status,
      String source,
      Long ownerId,
      boolean poolOnly,
      long page,
      long pageSize) {
    LambdaQueryWrapper<Lead> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(
          w ->
              w.like(Lead::getName, kw)
                  .or()
                  .like(Lead::getCompany, kw)
                  .or()
                  .like(Lead::getTitle, kw)
                  .or()
                  .like(Lead::getPhone, kw)
                  .or()
                  .like(Lead::getEmail, kw));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Lead::getStatus, status.trim());
    }
    if (StringUtils.hasText(source)) {
      qw.eq(Lead::getSource, source.trim());
    }
    if (poolOnly) {
      qw.isNull(Lead::getOwnerId).in(Lead::getStatus, List.of(STATUS_NEW, STATUS_WORKING));
    } else if (ownerId != null) {
      qw.eq(Lead::getOwnerId, ownerId);
    }
    qw.orderByDesc(Lead::getId);
    Page<Lead> p = leadMapper.selectPage(new Page<>(page, pageSize), qw);
    List<LeadResponse> items = toResponses(p.getRecords());
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  public LeadDetailResponse detail(Long id) {
    Lead lead = require(id);
    LeadDetailResponse resp = new LeadDetailResponse();
    copyToResponse(lead, resp);
    List<FollowUpBrief> followUps =
        followUpMapper
            .selectList(
                new LambdaQueryWrapper<FollowUp>()
                    .eq(FollowUp::getLeadId, id)
                    .orderByDesc(FollowUp::getCreatedAt))
            .stream()
            .map(
                f -> {
                  FollowUpBrief b = new FollowUpBrief();
                  b.setId(f.getId());
                  b.setMethod(f.getMethod());
                  b.setContent(f.getContent());
                  b.setCreatedAt(f.getCreatedAt());
                  return b;
                })
            .toList();
    resp.setFollowUps(followUps);
    return resp;
  }

  @Transactional
  public LeadResponse create(LeadRequest req) {
    Lead lead = new Lead();
    apply(req, lead);
    if (!StringUtils.hasText(lead.getStatus())) {
      lead.setStatus(STATUS_NEW);
    }
    if (!StringUtils.hasText(lead.getSource())) {
      lead.setSource("OTHER");
    }
    if (lead.getScore() == null) {
      lead.setScore(0);
    }
    lead.setCreatedBy(SecurityUtil.currentUserId());
    leadMapper.insert(lead);
    auditService.record("CREATE", "LEAD", lead.getId(), "创建线索：" + lead.getName());
    return toResponse(lead);
  }

  @Transactional
  public LeadResponse update(Long id, LeadRequest req) {
    Lead existing = require(id);
    if (STATUS_QUALIFIED.equals(existing.getStatus())
        || STATUS_DISQUALIFIED.equals(existing.getStatus())) {
      throw new BusinessException(ErrorCode.LEAD_INVALID_STATE, "已转化或无效线索不可编辑");
    }
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = leadMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "LEAD", id, "编辑线索：" + existing.getName());
    return toResponse(leadMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    Lead lead = require(id);
    if (STATUS_QUALIFIED.equals(lead.getStatus())) {
      throw new BusinessException(ErrorCode.LEAD_ALREADY_CONVERTED, "已转化线索不可删除");
    }
    leadMapper.deleteById(id);
    auditService.record("DELETE", "LEAD", id, "删除线索：" + lead.getName());
  }

  @Transactional
  public LeadResponse assign(Long id, Long ownerId) {
    Lead lead = require(id);
    User owner = userMapper.selectById(ownerId);
    if (owner == null) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    lead.setOwnerId(ownerId);
    if (STATUS_NEW.equals(lead.getStatus())) {
      lead.setStatus(STATUS_WORKING);
    }
    leadMapper.updateById(lead);
    auditService.record("ASSIGN", "LEAD", id, "分配线索给：" + owner.getUsername());
    return toResponse(leadMapper.selectById(id));
  }

  @Transactional
  public LeadResponse claim(Long id) {
    Lead lead = require(id);
    if (lead.getOwnerId() != null) {
      throw new BusinessException(ErrorCode.LEAD_INVALID_STATE, "该线索已被领取");
    }
    Long currentUserId = SecurityUtil.currentUserId();
    lead.setOwnerId(currentUserId);
    if (STATUS_NEW.equals(lead.getStatus())) {
      lead.setStatus(STATUS_WORKING);
    }
    leadMapper.updateById(lead);
    auditService.record("CLAIM", "LEAD", id, "领取线索：" + lead.getName());
    return toResponse(leadMapper.selectById(id));
  }

  /** 线索转化：查重客户→创建/关联客户→创建商机→更新线索状态为 QUALIFIED。 单事务，任何一步失败全部回滚。 */
  @Transactional
  public LeadDetailResponse convert(Long id, ConvertRequest req) {
    Lead lead = require(id);
    if (!STATUS_WORKING.equals(lead.getStatus()) && !STATUS_NEW.equals(lead.getStatus())) {
      throw new BusinessException(ErrorCode.LEAD_INVALID_STATE, "仅跟进中或新线索可转化");
    }

    // 1. 查重客户（按公司名称），存在则关联，不存在则创建
    Customer customer =
        customerMapper.selectOne(
            new LambdaQueryWrapper<Customer>().eq(Customer::getCompany, lead.getCompany()));
    if (customer == null) {
      customer = new Customer();
      customer.setName(lead.getName());
      customer.setCompany(lead.getCompany());
      customer.setContactPerson(lead.getName());
      customer.setPhone(lead.getPhone());
      customer.setEmail(lead.getEmail());
      customer.setStatus("ACTIVE");
      customer.setCreatedBy(SecurityUtil.currentUserId());
      customerMapper.insert(customer);
      auditService.record(
          "CREATE", "CUSTOMER", customer.getId(), "线索转化创建客户：" + customer.getCompany());
    }

    // 2. 创建商机（父实体）
    Opportunity opportunity = new Opportunity();
    opportunity.setCustomerId(customer.getId());
    opportunity.setName(req.getOpportunityName());
    opportunity.setExpectedAmountMin(req.getExpectedAmount());
    opportunity.setExpectedAmountMax(req.getExpectedAmount());
    opportunity.setRemark(req.getRemark());
    opportunity.setStatus("ACTIVE");
    opportunity.setCreatedBy(SecurityUtil.currentUserId());
    opportunityMapper.insert(opportunity);

    // 3. 创建销售机会（子实体，默认初始阶段）
    SalesOpportunity salesOpp = new SalesOpportunity();
    salesOpp.setOpportunityId(opportunity.getId());
    salesOpp.setAmount(req.getExpectedAmount());
    salesOpp.setStage("INITIAL_CONTACT");
    salesOpp.setCreatedBy(SecurityUtil.currentUserId());
    salesOpportunityMapper.insert(salesOpp);

    // 4. 更新线索状态为已转化
    lead.setStatus(STATUS_QUALIFIED);
    lead.setConvertedCustomerId(customer.getId());
    lead.setConvertedAt(LocalDateTime.now());
    leadMapper.updateById(lead);

    auditService.record(
        "CONVERT",
        "LEAD",
        id,
        "转化线索：" + lead.getName() + " → 客户#" + customer.getId() + " 商机#" + opportunity.getId());

    return detail(id);
  }

  public Lead require(Long id) {
    Lead lead = leadMapper.selectById(id);
    if (lead == null) {
      throw new BusinessException(ErrorCode.LEAD_NOT_FOUND);
    }
    return lead;
  }

  private void apply(LeadRequest req, Lead lead) {
    lead.setName(req.getName().trim());
    lead.setCompany(req.getCompany().trim());
    lead.setTitle(req.getTitle());
    lead.setPhone(req.getPhone());
    lead.setEmail(req.getEmail());
    if (StringUtils.hasText(req.getSource())) {
      lead.setSource(req.getSource().trim().toUpperCase());
    }
    if (StringUtils.hasText(req.getStatus())) {
      lead.setStatus(req.getStatus().trim().toUpperCase());
    }
    if (req.getScore() != null) {
      lead.setScore(Math.max(0, Math.min(100, req.getScore())));
    }
    lead.setOwnerId(req.getOwnerId());
    lead.setRemark(req.getRemark());
  }

  /** 批量装配：一次查询全部 owner 显示名，避免逐行查询（N+1，章程原则五）。 */
  private List<LeadResponse> toResponses(List<Lead> leads) {
    if (leads.isEmpty()) {
      return List.of();
    }
    List<Long> ownerIds =
        leads.stream().map(Lead::getOwnerId).filter(Objects::nonNull).distinct().toList();
    Map<Long, String> ownerNames = new java.util.HashMap<>();
    if (!ownerIds.isEmpty()) {
      for (User u : userMapper.selectBatchIds(ownerIds)) {
        if (u != null && u.getId() != null && u.getDisplayName() != null) {
          ownerNames.put(u.getId(), u.getDisplayName());
        }
      }
    }
    return leads.stream()
        .map(
            lead -> {
              LeadResponse resp = new LeadResponse();
              copyToResponse(lead, resp);
              resp.setOwnerName(ownerNames.get(lead.getOwnerId()));
              return resp;
            })
        .toList();
  }

  private LeadResponse toResponse(Lead lead) {
    LeadResponse resp = new LeadResponse();
    copyToResponse(lead, resp);
    if (lead.getOwnerId() != null) {
      User owner = userMapper.selectById(lead.getOwnerId());
      resp.setOwnerName(owner == null ? null : owner.getDisplayName());
    }
    return resp;
  }

  /** 纯字段映射，不做数据库访问（批量路径避免 N+1）。 */
  private void copyToResponse(Lead lead, LeadResponse resp) {
    resp.setId(lead.getId());
    resp.setName(lead.getName());
    resp.setCompany(lead.getCompany());
    resp.setTitle(lead.getTitle());
    resp.setPhone(lead.getPhone());
    resp.setEmail(lead.getEmail());
    resp.setSource(lead.getSource());
    resp.setStatus(lead.getStatus());
    resp.setScore(lead.getScore());
    resp.setOwnerId(lead.getOwnerId());
    resp.setConvertedCustomerId(lead.getConvertedCustomerId());
    resp.setConvertedAt(lead.getConvertedAt());
    resp.setRemark(lead.getRemark());
    resp.setVersion(lead.getVersion());
    resp.setCreatedAt(lead.getCreatedAt());
  }
}
