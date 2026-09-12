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
import com.crm.entity.User;
import com.crm.repository.SlaPolicyMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SLA 策略服务（015，FR-C09/C12）：CRUD/优先级唯一/超时统计。 */
@Service
public class SlaPolicyService {

  public static final String PRIORITY_ORDER = "LOW,MEDIUM,HIGH,URGENT";

  /** 未分配处理人的分组键（overview 的按处理人维度）：与真实用户 id 不可能碰撞。 */
  private static final long UNASSIGNED_KEY = -1L;

  private final SlaPolicyMapper slaPolicyMapper;
  private final TicketMapper ticketMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public SlaPolicyService(
      SlaPolicyMapper slaPolicyMapper,
      TicketMapper ticketMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.slaPolicyMapper = slaPolicyMapper;
    this.ticketMapper = ticketMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /**
   * 按优先级取启用的 SLA 策略；无策略返回 {@code null}。
   *
   * <p>1.3-sla-escalation：本方法从 {@code TicketService.applySla} 的内联查询抽出，成为唯一取策略入口， 避免两处各写一份条件而漂移（与
   * 015 建单时的行为等价：同优先级多条启用策略取第一条）。
   */
  public SlaPolicy resolvePolicyFor(String priority) {
    if (priority == null) {
      return null;
    }
    return slaPolicyMapper.selectOne(
        new LambdaQueryWrapper<SlaPolicy>()
            .eq(SlaPolicy::getPriority, priority)
            .eq(SlaPolicy::getEnabled, 1)
            .last("LIMIT 1"));
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

  /**
   * SLA 超时统计：未关闭工单中超时数/占比（FR-C12）+ 响应/解决达成率与按处理人维度（1.3 扩展）。
   *
   * <p>达成率口径（1.3 新增，与既有 overdueRate 同一总体——<b>未关闭工单</b>）：
   *
   * <ul>
   *   <li>响应违约：已响应但晚于 respond deadline，或尚未响应且 respond deadline 已过；
   *   <li>解决违约：resolve deadline 已过（未关闭工单仍未解决）；
   *   <li>达成率 = 1 - 违约数 / 未关闭工单数；未关闭工单为 0 时为 {@code null}（不臆造 0%）。
   * </ul>
   */
  public SlaOverviewResponse overview() {
    List<Ticket> open =
        ticketMapper.selectList(
            new LambdaQueryWrapper<Ticket>().ne(Ticket::getStatus, TicketService.STATUS_CLOSED));
    LocalDateTime now = LocalDateTime.now();
    long total = open.size();
    long overdue =
        open.stream()
            .filter(t -> TicketService.SLA_OVERDUE.equals(TicketService.computeSlaStatus(t, now)))
            .count();
    SlaOverviewResponse resp = new SlaOverviewResponse();
    resp.setTotalOpen(total);
    resp.setOverdue(overdue);
    resp.setOverdueRate(rate(overdue, total));

    long respondBreached = open.stream().filter(t -> respondBreached(t, now)).count();
    long resolveBreached = open.stream().filter(t -> resolveBreached(t, now)).count();
    resp.setRespondComplianceRate(rate(total - respondBreached, total));
    resp.setResolveComplianceRate(rate(total - resolveBreached, total));

    Map<String, List<Ticket>> byPriority =
        open.stream().collect(Collectors.groupingBy(Ticket::getPriority));
    List<SlaOverviewResponse.PrioritySla> byPriorityList = new ArrayList<>();
    for (Map.Entry<String, List<Ticket>> e : byPriority.entrySet()) {
      long subTotal = e.getValue().size();
      long subOverdue =
          e.getValue().stream()
              .filter(t -> TicketService.SLA_OVERDUE.equals(TicketService.computeSlaStatus(t, now)))
              .count();
      SlaOverviewResponse.PrioritySla ps = new SlaOverviewResponse.PrioritySla();
      ps.setPriority(e.getKey());
      ps.setTotalOpen(subTotal);
      ps.setOverdue(subOverdue);
      ps.setOverdueRate(rate(subOverdue, subTotal));
      byPriorityList.add(ps);
    }
    byPriorityList.sort(
        Comparator.comparingInt(
            ps -> {
              int idx = PRIORITY_ORDER.indexOf(ps.getPriority());
              return idx < 0 ? 99 : idx;
            }));
    resp.setByPriority(byPriorityList);
    resp.setByAssignee(byAssignee(open, now));
    return resp;
  }

  /** 按处理人维度（1.3）：未分配归一组，组内口径与总体一致。 */
  private List<SlaOverviewResponse.AssigneeSla> byAssignee(List<Ticket> open, LocalDateTime now) {
    // HashMap 允许 null 键，故分组键用哨兵值避免 Collectors.groupingBy 对 null 键抛 NPE
    Map<Long, List<Ticket>> grouped = new HashMap<>();
    for (Ticket t : open) {
      grouped
          .computeIfAbsent(
              t.getAssigneeId() == null ? UNASSIGNED_KEY : t.getAssigneeId(),
              k -> new ArrayList<>())
          .add(t);
    }
    Map<Long, String> names = namesOf(grouped.keySet());
    List<SlaOverviewResponse.AssigneeSla> list = new ArrayList<>();
    for (Map.Entry<Long, List<Ticket>> e : grouped.entrySet()) {
      long subTotal = e.getValue().size();
      long subOverdue =
          e.getValue().stream()
              .filter(t -> TicketService.SLA_OVERDUE.equals(TicketService.computeSlaStatus(t, now)))
              .count();
      SlaOverviewResponse.AssigneeSla as = new SlaOverviewResponse.AssigneeSla();
      boolean unassigned = e.getKey() == UNASSIGNED_KEY;
      as.setAssigneeId(unassigned ? null : e.getKey());
      as.setAssigneeName(unassigned ? "未分配" : names.get(e.getKey()));
      as.setTotalOpen(subTotal);
      as.setOverdue(subOverdue);
      as.setOverdueRate(rate(subOverdue, subTotal));
      list.add(as);
    }
    // 超时多的排前面；同数时未分配在前，便于先看到没人认领的积压
    list.sort(
        Comparator.comparingLong(SlaOverviewResponse.AssigneeSla::getOverdue)
            .reversed()
            .thenComparing(as -> as.getAssigneeId() == null ? -1L : as.getAssigneeId()));
    return list;
  }

  private Map<Long, String> namesOf(Set<Long> ids) {
    List<Long> real = ids.stream().filter(id -> id != UNASSIGNED_KEY).toList();
    if (real.isEmpty()) {
      return Map.of();
    }
    return userMapper.selectBatchIds(real).stream()
        .collect(Collectors.toMap(User::getId, User::getDisplayName));
  }

  /** 响应是否违约：已响应但晚于 respond deadline，或未响应且 respond deadline 已过。 */
  private static boolean respondBreached(Ticket t, LocalDateTime now) {
    LocalDateTime deadline = t.getSlaRespondDeadline();
    if (deadline == null) {
      return false;
    }
    LocalDateTime responded = t.getSlaRespondedAt();
    return responded == null ? !deadline.isAfter(now) : responded.isAfter(deadline);
  }

  /** 解决是否违约：resolve deadline 已过（未关闭工单仍未解决）。 */
  private static boolean resolveBreached(Ticket t, LocalDateTime now) {
    LocalDateTime deadline = t.getSlaResolveDeadline();
    return deadline != null && !deadline.isAfter(now);
  }

  private static Double rate(long part, long total) {
    return total == 0 ? null : (double) part / total;
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
