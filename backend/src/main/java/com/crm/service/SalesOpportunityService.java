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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 销售机会服务（子实体，FR-012~014，状态机见 data-model.md）。 */
@Service
public class SalesOpportunityService {

  private final SalesOpportunityMapper salesOpportunityMapper;
  private final OpportunityMapper opportunityMapper;
  private final OpportunityStatsService statsService;
  private final DashboardStatsService dashboardStatsService;
  private final AuditService auditService;
  private final SalesOpportunityAssembler assembler;
  private final WorkflowEventPublisher workflowEventPublisher;
  private final OpportunityStageService stageService;

  public SalesOpportunityService(
      SalesOpportunityMapper salesOpportunityMapper,
      OpportunityMapper opportunityMapper,
      OpportunityStatsService statsService,
      DashboardStatsService dashboardStatsService,
      AuditService auditService,
      SalesOpportunityAssembler assembler,
      WorkflowEventPublisher workflowEventPublisher,
      OpportunityStageService stageService) {
    this.salesOpportunityMapper = salesOpportunityMapper;
    this.opportunityMapper = opportunityMapper;
    this.statsService = statsService;
    this.dashboardStatsService = dashboardStatsService;
    this.auditService = auditService;
    this.assembler = assembler;
    this.workflowEventPublisher = workflowEventPublisher;
    this.stageService = stageService;
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
    validateStage(req.getStage(), null);
    validateAmount(req.getAmount());
    SalesOpportunity so = new SalesOpportunity();
    so.setOpportunityId(req.getOpportunityId());
    so.setAmount(req.getAmount() == null ? 0L : req.getAmount());
    so.setStage(req.getStage().trim());
    so.setExpectedCloseDate(req.getExpectedCloseDate());
    so.setCreatedBy(SecurityUtil.currentUserId());
    salesOpportunityMapper.insert(so);
    statsService.evict();
    dashboardStatsService.evict();
    return assembleOne(so);
  }

  @Transactional
  public SalesOpportunityResponse update(Long id, SalesOpportunityRequest req) {
    SalesOpportunity existing = require(id);
    if (existing.getClosedAt() != null
        || !stageService.activeCodes().contains(existing.getStage())) {
      throw new BusinessException(ErrorCode.ALREADY_CLOSED);
    }
    validateStage(req.getStage(), existing.getStage());
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
    dashboardStatsService.evict();
    // 013：阶段变更触发工作流
    workflowEventPublisher.opportunityStageChanged(
        id, java.util.Map.of("stage", existing.getStage(), "name", "销售机会"));
    return assembleOne(salesOpportunityMapper.selectById(id));
  }

  @Transactional
  public SalesOpportunityResponse close(Long id, CloseRequest req) {
    SalesOpportunity existing = require(id);
    if (existing.getClosedAt() != null
        || !stageService.activeCodes().contains(existing.getStage())) {
      throw new BusinessException(ErrorCode.ALREADY_CLOSED);
    }
    String result = req.getCloseResult() == null ? null : req.getCloseResult().trim().toUpperCase();
    if (!"WON".equals(result) && !"LOST".equals(result)) {
      throw new BusinessException(ErrorCode.CLOSE_RESULT_REQUIRED);
    }
    // 拼接出的两个编码即 OpportunityStageService.BUILT_IN_CODES——被 SalesQuotaRepository 的原始 SQL
    // 按字面量引用（配额达成率统计 stage = 'CLOSED_WON'），故不可改。改阶段字典的终态编码会让配额静默归零。
    existing.setStage("CLOSED_" + result);
    existing.setCloseResult(result);
    existing.setClosedAt(LocalDateTime.now());
    existing.setVersion(req.getVersion());
    int rows = salesOpportunityMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    statsService.evict();
    dashboardStatsService.evict();
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

  /**
   * 阶段合法性校验。取值来自阶段字典（1.2），不再读代码里的常量。只服务「新建 / 编辑」路径。
   *
   * <p>三条规则，依次是：
   *
   * <ol>
   *   <li>必须是字典里存在的编码；
   *   <li>必须是**进行中**阶段——终态不在此列：关单必须走 {@link #close}（它同时写 closedAt / closeResult），绕过它直接设成 CLOSED_WON
   *       会留下一条「阶段是赢单、却没有赢单时间和结果」的商机， 赢单率统计会把它算进去而明细缺字段；
   *   <li>不许**新进入**已停用的阶段。
   * </ol>
   *
   * <p>第 3 条带 {@code currentStage} 例外：目标阶段与当前阶段相同时放行。停用的语义是「不许新进入，不是不许存在」
   * ——存量商机就落在那个阶段里，若一律拒绝，用户连改个金额都做不到，只能先把它挪到别的阶段去。而挪走本身是被允许的 （那才是停用的目的）。这条例外就是「能出去、不能进来」。
   *
   * @param currentStage 编辑前的阶段；新建时传 {@code null}（没有「当前」可言，一律按新进入校验）
   */
  private void validateStage(String stage, String currentStage) {
    if (stage == null || !stageService.allCodes().contains(stage.trim())) {
      throw new BusinessException(ErrorCode.STAGE_INVALID);
    }
    String target = stage.trim();
    if (!stageService.activeCodes().contains(target)) {
      throw new BusinessException(ErrorCode.STAGE_INVALID);
    }
    if (!target.equals(currentStage) && !stageService.selectableCodes().contains(target)) {
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
