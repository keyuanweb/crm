package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.sla.SlaOverviewResponse;
import com.crm.dto.sla.SlaPolicyRequest;
import com.crm.dto.sla.SlaPolicyResponse;
import com.crm.entity.SlaPolicy;
import com.crm.entity.Ticket;
import com.crm.repository.SlaPolicyMapper;
import com.crm.repository.TicketMapper;
import com.crm.security.SecurityUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SLA 策略服务（015，FR-C09/C12）：CRUD/优先级唯一/超时统计。 */
@Service
public class SlaPolicyService {

  public static final String PRIORITY_ORDER = "LOW,MEDIUM,HIGH,URGENT";

  private final SlaPolicyMapper slaPolicyMapper;
  private final TicketMapper ticketMapper;
  private final AuditService auditService;

  public SlaPolicyService(
      SlaPolicyMapper slaPolicyMapper, TicketMapper ticketMapper, AuditService auditService) {
    this.slaPolicyMapper = slaPolicyMapper;
    this.ticketMapper = ticketMapper;
    this.auditService = auditService;
  }

  public PageResult<SlaPolicyResponse> page(long page, long pageSize) {
    LambdaQueryWrapper<SlaPolicy> qw =
        new LambdaQueryWrapper<SlaPolicy>().orderByDesc(SlaPolicy::getId);
    Page<SlaPolicy> p = slaPolicyMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public SlaPolicyResponse create(SlaPolicyRequest req) {
    validate(req);
    Long exists =
        slaPolicyMapper.selectCount(
            new LambdaQueryWrapper<SlaPolicy>().eq(SlaPolicy::getPriority, req.getPriority()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.SLA_POLICY_DUPLICATE);
    }
    SlaPolicy policy = new SlaPolicy();
    apply(req, policy);
    policy.setCreatedBy(SecurityUtil.currentUserId());
    slaPolicyMapper.insert(policy);
    auditService.record(
        "CREATE", "SLA_POLICY", policy.getId(), "创建 SLA 策略：" + policy.getPriority());
    return toResponse(policy);
  }

  @Transactional
  public SlaPolicyResponse update(Long id, SlaPolicyRequest req) {
    SlaPolicy existing = require(id);
    validate(req);
    Long dup =
        slaPolicyMapper.selectCount(
            new LambdaQueryWrapper<SlaPolicy>()
                .eq(SlaPolicy::getPriority, req.getPriority())
                .ne(SlaPolicy::getId, id));
    if (dup != null && dup > 0) {
      throw new BusinessException(ErrorCode.SLA_POLICY_DUPLICATE);
    }
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = slaPolicyMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "SLA_POLICY", id, "编辑 SLA 策略：" + existing.getPriority());
    return toResponse(slaPolicyMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    SlaPolicy policy = require(id);
    slaPolicyMapper.deleteById(id);
    auditService.record("DELETE", "SLA_POLICY", id, "删除 SLA 策略：" + policy.getPriority());
  }

  /** SLA 超时统计：未关闭工单中超时数/占比（FR-C12）。 */
  public SlaOverviewResponse overview() {
    List<Ticket> open =
        ticketMapper.selectList(
            new LambdaQueryWrapper<Ticket>().ne(Ticket::getStatus, TicketService.STATUS_CLOSED));
    long total = open.size();
    long overdue =
        open.stream()
            .filter(
                t ->
                    TicketService.SLA_OVERDUE.equals(
                        TicketService.computeSlaStatus(t, java.time.LocalDateTime.now())))
            .count();
    SlaOverviewResponse resp = new SlaOverviewResponse();
    resp.setTotalOpen(total);
    resp.setOverdue(overdue);
    resp.setOverdueRate(total == 0 ? null : (double) overdue / total);

    Map<String, List<Ticket>> byPriority =
        open.stream().collect(Collectors.groupingBy(Ticket::getPriority));
    List<SlaOverviewResponse.PrioritySla> byPriorityList = new ArrayList<>();
    for (Map.Entry<String, List<Ticket>> e : byPriority.entrySet()) {
      long subTotal = e.getValue().size();
      long subOverdue =
          e.getValue().stream()
              .filter(
                  t ->
                      TicketService.SLA_OVERDUE.equals(
                          TicketService.computeSlaStatus(t, java.time.LocalDateTime.now())))
              .count();
      SlaOverviewResponse.PrioritySla ps = new SlaOverviewResponse.PrioritySla();
      ps.setPriority(e.getKey());
      ps.setTotalOpen(subTotal);
      ps.setOverdue(subOverdue);
      ps.setOverdueRate(subTotal == 0 ? null : (double) subOverdue / subTotal);
      byPriorityList.add(ps);
    }
    byPriorityList.sort(
        Comparator.comparingInt(
            ps -> {
              int idx = PRIORITY_ORDER.indexOf(ps.getPriority());
              return idx < 0 ? 99 : idx;
            }));
    resp.setByPriority(byPriorityList);
    return resp;
  }

  private void validate(SlaPolicyRequest req) {
    if (req.getRespondHours() == null && req.getResolveHours() == null) {
      throw new BusinessException(ErrorCode.SLA_POLICY_INVALID);
    }
  }

  private void apply(SlaPolicyRequest req, SlaPolicy policy) {
    policy.setPriority(req.getPriority().trim());
    policy.setRespondHours(req.getRespondHours());
    policy.setResolveHours(req.getResolveHours());
    policy.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
  }

  private SlaPolicy require(Long id) {
    SlaPolicy policy = slaPolicyMapper.selectById(id);
    if (policy == null) {
      throw new BusinessException(ErrorCode.SLA_POLICY_NOT_FOUND);
    }
    return policy;
  }

  private SlaPolicyResponse toResponse(SlaPolicy policy) {
    SlaPolicyResponse resp = new SlaPolicyResponse();
    resp.setId(policy.getId());
    resp.setPriority(policy.getPriority());
    resp.setRespondHours(policy.getRespondHours());
    resp.setResolveHours(policy.getResolveHours());
    resp.setEnabled(policy.getEnabled());
    resp.setVersion(policy.getVersion());
    resp.setCreatedAt(policy.getCreatedAt());
    return resp;
  }
}
