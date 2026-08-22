package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.opportunity.CloseRequest;
import com.crm.dto.opportunity.SalesOpportunityRequest;
import com.crm.dto.opportunity.SalesOpportunityResponse;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.security.SecurityUtil;
import com.crm.support.SalesOpportunityAssembler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 销售机会服务（子实体，FR-012~014，状态机见 data-model.md）。 */
@Service
public class SalesOpportunityService {

  public static final Set<String> STAGES =
      Set.of("INITIAL_CONTACT", "NEGOTIATING", "CLOSED_WON", "CLOSED_LOST");
  public static final Set<String> ACTIVE_STAGES = Set.of("INITIAL_CONTACT", "NEGOTIATING");

  private final SalesOpportunityMapper salesOpportunityMapper;
  private final OpportunityMapper opportunityMapper;
  private final OpportunityStatsService statsService;
  private final AuditService auditService;
  private final SalesOpportunityAssembler assembler;

  public SalesOpportunityService(
      SalesOpportunityMapper salesOpportunityMapper,
      OpportunityMapper opportunityMapper,
      OpportunityStatsService statsService,
      AuditService auditService,
      SalesOpportunityAssembler assembler) {
    this.salesOpportunityMapper = salesOpportunityMapper;
    this.opportunityMapper = opportunityMapper;
    this.statsService = statsService;
    this.auditService = auditService;
    this.assembler = assembler;
  }

  public PageResult<SalesOpportunityResponse> page(
      String stage, Long opportunityId, Long customerId, long page, long pageSize) {
    LambdaQueryWrapper<SalesOpportunity> qw = new LambdaQueryWrapper<>();
    if (stage != null && !stage.isBlank()) {
      qw.eq(SalesOpportunity::getStage, stage.trim());
    }
    if (opportunityId != null) {
      qw.eq(SalesOpportunity::getOpportunityId, opportunityId);
    }
    if (customerId != null) {
      List<Long> oppIds =
          opportunityMapper
              .selectList(
                  new LambdaQueryWrapper<Opportunity>().eq(Opportunity::getCustomerId, customerId))
              .stream()
              .map(Opportunity::getId)
              .toList();
      if (oppIds.isEmpty()) {
        return PageResult.of(List.of(), 0, page, pageSize);
      }
      qw.in(SalesOpportunity::getOpportunityId, oppIds);
    }
    qw.orderByDesc(SalesOpportunity::getId);
    Page<SalesOpportunity> p = salesOpportunityMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(assembler.assemble(p.getRecords()), p.getTotal(), page, pageSize);
  }

  public SalesOpportunityResponse detail(Long id) {
    SalesOpportunity so = require(id);
    return assembleOne(so);
  }

  @Transactional
  public SalesOpportunityResponse create(SalesOpportunityRequest req) {
    Opportunity parent = opportunityMapper.selectById(req.getOpportunityId());
    if (parent == null) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_NOT_FOUND);
    }
    validateStage(req.getStage(), false);
    validateAmount(req.getAmount());
    SalesOpportunity so = new SalesOpportunity();
    so.setOpportunityId(req.getOpportunityId());
    so.setAmount(req.getAmount() == null ? 0L : req.getAmount());
    so.setStage(req.getStage().trim());
    so.setExpectedCloseDate(req.getExpectedCloseDate());
    so.setCreatedBy(SecurityUtil.currentUserId());
    salesOpportunityMapper.insert(so);
    statsService.evict();
    return assembleOne(so);
  }

  @Transactional
  public SalesOpportunityResponse update(Long id, SalesOpportunityRequest req) {
    SalesOpportunity existing = require(id);
    if (existing.getClosedAt() != null || !ACTIVE_STAGES.contains(existing.getStage())) {
      throw new BusinessException(ErrorCode.ALREADY_CLOSED);
    }
    validateStage(req.getStage(), false);
    validateAmount(req.getAmount());
    existing.setAmount(req.getAmount() == null ? 0L : req.getAmount());
    existing.setStage(req.getStage().trim());
    existing.setExpectedCloseDate(req.getExpectedCloseDate());
    existing.setVersion(req.getVersion());
    int rows = salesOpportunityMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    statsService.evict();
    return assembleOne(salesOpportunityMapper.selectById(id));
  }

  @Transactional
  public SalesOpportunityResponse close(Long id, CloseRequest req) {
    SalesOpportunity existing = require(id);
    if (existing.getClosedAt() != null || !ACTIVE_STAGES.contains(existing.getStage())) {
      throw new BusinessException(ErrorCode.ALREADY_CLOSED);
    }
    String result = req.getCloseResult() == null ? null : req.getCloseResult().trim().toUpperCase();
    if (!"WON".equals(result) && !"LOST".equals(result)) {
      throw new BusinessException(ErrorCode.CLOSE_RESULT_REQUIRED);
    }
    existing.setStage("CLOSED_" + result);
    existing.setCloseResult(result);
    existing.setClosedAt(LocalDateTime.now());
    existing.setVersion(req.getVersion());
    int rows = salesOpportunityMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    statsService.evict();
    auditService.record(
        "CLOSE", "SALES_OPPORTUNITY", id, "关闭销售机会：" + result + "（金额 " + existing.getAmount() + "）");
    return assembleOne(salesOpportunityMapper.selectById(id));
  }

  public SalesOpportunity require(Long id) {
    SalesOpportunity so = salesOpportunityMapper.selectById(id);
    if (so == null) {
      throw new BusinessException(ErrorCode.SALES_OPPORTUNITY_NOT_FOUND);
    }
    return so;
  }

  private void validateStage(String stage, boolean allowTerminal) {
    if (stage == null || !STAGES.contains(stage.trim())) {
      throw new BusinessException(ErrorCode.STAGE_INVALID);
    }
    if (!allowTerminal && !ACTIVE_STAGES.contains(stage.trim())) {
      throw new BusinessException(ErrorCode.STAGE_INVALID);
    }
  }

  private void validateAmount(Long amount) {
    if (amount != null && amount < 0) {
      throw new BusinessException(ErrorCode.AMOUNT_INVALID);
    }
  }

  /** 单条装配：复用批量路径（一次查询父商机与客户），避免额外的逐行查询。 */
  private SalesOpportunityResponse assembleOne(SalesOpportunity so) {
    return assembler.assembleOne(so);
  }
}
