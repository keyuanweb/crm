package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.SlaPolicy;
import com.crm.entity.Ticket;
import com.crm.entity.User;
import com.crm.repository.SlaPolicyMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.UserMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SLA 自动升级服务（1.3-sla-escalation）。
 *
 * <p><b>要解决的问题</b>：015 建模了 SLA（策略／工作日历／ticket.sla_*），但全库没有任何扫描作业，{@code slaStatus}
 * 的唯一更新路径是「有人打开工单详情」——工单不会自动升级，SLA 策略形同虚设。
 *
 * <p><b>分级</b>：
 *
 * <ul>
 *   <li>L1（WARNING）→ 通知处理人；无处理人时直接落到 ADMIN，不让通知静默丢失；
 *   <li>L2（OVERDUE）→ 通知处理人 + 全部启用的 ADMIN；
 *   <li>L3+（仍 OVERDUE，且距上次升级又满一个升级间隔）→ 再通知全部 ADMIN 并走 {@link IntegrationChannelService#publish}
 *       广播（企微／钉钉）。
 * </ul>
 *
 * <p><b>幂等</b>：{@code ticket.escalate_level} 单调递增，同一工单同一级<b>绝不重复通知</b>——首轮扫描写入级别，
 * 其后每轮只在「状态对应级别高于当前级别」或「OVERDUE 且又满一个间隔」时才升级，且升级总是把级别 +1（级别因此永不重复）。 状态下降（如已解决）不回退级别，也就不会重新触发低级通知。
 *
 * <p><b>「主管」的口径</b>：数据模型里没有上下级关系（{@code department} 只有 {@code parentId}，没有 {@code managerId}；角色只有
 * ADMIN/SALES/SUPPORT，没有 MANAGER），因此 L2/L3 用「全部启用的 ADMIN」充当主管。待 2.2 区域管理引入 manager 关系后，应在此把收件人从「全部
 * ADMIN」细化为「处理人所属部门的 manager」。
 *
 * <p><b>为什么只扫 OPEN／IN_PROGRESS</b>：迁移里为扫描建的索引按「未关闭」的描述写成 {@code (deleted, status,
 * sla_resolve_deadline)}；但升级是「催办」语义——RESOLVED 工单的 SLA 时钟已经停了（解决时刻已经写下），
 * 再对它升级只会给处理人和管理员发假通知（实测：按时解决的工单若 deadline 未到，会被判 WARNING 而 L1 通知处理人）。 故查询条件收紧为 {@code status IN
 * ('OPEN','IN_PROGRESS')}，索引对 IN 条件同样可用。
 *
 * <p><b>无策略不升级</b>：与工单优先级对应的启用策略若不存在，则整单不升级——SLA 承诺本身不存在时催办没有意义 （典型场景：策略被停用/删除后，已建工单上仍残留 deadline）。
 *
 * <p><b>为什么不用 {@link SlaCalendarService#advanceWorkingTime}</b>：它在内部每次都做一次配置加载，逐工单调用即
 * N+1；而升级间隔只需要「上次升级时刻 + N 小时」这种纯算术。升级间隔取策略的 resolve 时限（缺失时取 {@link
 * #DEFAULT_ESCALATE_INTERVAL_HOURS}），与工作日历无关——若将来要求间隔也只在工作时段内累积，需要 SlaCalendarService
 * 先提供「一次加载、多次推进」的批量入口。
 *
 * <p><b>为什么不加 {@code @Transactional}</b>：一次扫描可能跨成百上千行，用一个事务圈住会长时间持锁；而本作业逐行幂等，
 * 无事务反而让失败可重试（单行失败不影响其余行，下一轮扫描会重试）。每行固定「先把级别落库、再发通知」——顺序反了的话， 通知已发出而落库失败时，下一轮会重复打扰同一批人。
 */
@Service
public class SlaEscalationService {

  private static final Logger log = LoggerFactory.getLogger(SlaEscalationService.class);

  /** 未升级。 */
  public static final int LEVEL_NONE = 0;

  /** L1：即将超时。 */
  public static final int LEVEL_WARNING = 1;

  /** L2：已超时。 */
  public static final int LEVEL_OVERDUE = 2;

  /** L3：持续超时（再升级同时走集成通道广播）。 */
  public static final int LEVEL_BROADCAST = 3;

  /** 策略未配置 resolve 时限时的默认升级间隔（小时）。 */
  public static final long DEFAULT_ESCALATE_INTERVAL_HOURS = 4;

  /** 单轮扫描上限：避免一次扫描把线程占满，剩余部分留给下一轮（cron 默认 10 分钟）。 */
  private static final int SCAN_LIMIT = 500;

  private final TicketMapper ticketMapper;
  private final SlaPolicyMapper slaPolicyMapper;
  private final UserMapper userMapper;
  private final NotificationService notificationService;
  private final IntegrationChannelService integrationChannelService;
  private final AuditService auditService;

  public SlaEscalationService(
      TicketMapper ticketMapper,
      SlaPolicyMapper slaPolicyMapper,
      UserMapper userMapper,
      NotificationService notificationService,
      IntegrationChannelService integrationChannelService,
      AuditService auditService) {
    this.ticketMapper = ticketMapper;
    this.slaPolicyMapper = slaPolicyMapper;
    this.userMapper = userMapper;
    this.notificationService = notificationService;
    this.integrationChannelService = integrationChannelService;
    this.auditService = auditService;
  }

  /** 扫描一轮并按级升级；返回本轮实际升级的工单数（定时作业与手动端点共用）。 */
  public int scanAndEscalate() {
    LocalDateTime now = LocalDateTime.now();
    List<Ticket> candidates = scanCandidates(now);
    if (candidates.isEmpty()) {
      return 0;
    }
    Map<String, SlaPolicy> policies = enabledPolicies();
    List<Long> adminIds = enabledAdminIds();
    int escalated = 0;
    for (Ticket ticket : candidates) {
      if (escalate(ticket, policies, adminIds, now)) {
        escalated++;
      }
    }
    log.info(
        "SLA escalation scan finished: candidates={}, escalated={}", candidates.size(), escalated);
    return escalated;
  }

  /** 候选集：进行中且 deadline 已进入预警窗口（含已超时）的工单。 */
  private List<Ticket> scanCandidates(LocalDateTime now) {
    LocalDateTime horizon = now.plusHours(TicketService.warningWindowHours());
    return ticketMapper.selectList(
        new LambdaQueryWrapper<Ticket>()
            .in(Ticket::getStatus, TicketService.STATUS_OPEN, TicketService.STATUS_IN_PROGRESS)
            .and(
                w ->
                    w.le(Ticket::getSlaResolveDeadline, horizon)
                        .or()
                        .le(Ticket::getSlaRespondDeadline, horizon))
            .orderByAsc(Ticket::getSlaResolveDeadline)
            .last("LIMIT " + SCAN_LIMIT));
  }

  /** 单工单升级；升级了返回 true。 */
  private boolean escalate(
      Ticket ticket, Map<String, SlaPolicy> policies, List<Long> adminIds, LocalDateTime now) {
    String status = TicketService.computeSlaStatus(ticket, now);
    int current = ticket.getEscalateLevel() == null ? LEVEL_NONE : ticket.getEscalateLevel();
    Integer target = targetLevel(ticket, status, current, policies, now);
    if (target == null) {
      return false;
    }
    ticket.setEscalateLevel(target);
    ticket.setLastEscalatedAt(now);
    if (!Objects.equals(status, ticket.getSlaStatus())) {
      // 顺带把刷新后的 SLA 状态落库：升级作业本身也是「有人看着」的时刻
      ticket.setSlaStatus(status);
    }
    // 先落库再通知：级别已持久化后重复扫描不会重复通知；反过来（先通知）在崩溃重试时会重复打扰
    ticketMapper.updateById(ticket);
    notifyRecipients(ticket, target, adminIds);
    auditService.recordAsSystem(
        "SLA_ESCALATE",
        "TICKET",
        ticket.getId(),
        "SLA 升级到 L" + target + "（工单「" + ticket.getTitle() + "」，当前状态 " + status + "）");
    return true;
  }

  /**
   * 计算本轮应升到的级别；无需升级返回 {@code null}。
   *
   * <p>级别单调递增：只有「目标级别高于当前级别」或「OVERDUE 且距上次升级满一个间隔」才返回当前级别 +1， 因此同一工单的同一级别只会被通知一次。
   */
  private Integer targetLevel(
      Ticket ticket,
      String status,
      int current,
      Map<String, SlaPolicy> policies,
      LocalDateTime now) {
    SlaPolicy policy = policyFor(policies, ticket.getPriority());
    // 与优先级对应的启用策略不存在 → 不升级：SLA 承诺本身不存在（如策略被删/被停用后残留的 deadline），
    // 就不该催办；把「有无策略」也当作升级前提，避免对已下线的 SLA 发通知。
    if (policy == null) {
      return null;
    }
    int level;
    if (TicketService.SLA_WARNING.equals(status)) {
      level = LEVEL_WARNING;
    } else if (TicketService.SLA_OVERDUE.equals(status)) {
      level = LEVEL_OVERDUE;
    } else {
      return null; // NORMAL：不升级
    }
    if (current < level) {
      return level;
    }
    // 已在该级或更高：只有持续超时才再升级（WARNING 不会重复通知）
    if (!TicketService.SLA_OVERDUE.equals(status) || ticket.getLastEscalatedAt() == null) {
      return null;
    }
    if (now.isBefore(ticket.getLastEscalatedAt().plusHours(escalateIntervalHours(policy)))) {
      return null;
    }
    return current + 1;
  }

  private SlaPolicy policyFor(Map<String, SlaPolicy> policies, String priority) {
    return priority == null ? null : policies.get(priority);
  }

  /** 升级间隔：优先用策略的 resolve 时限（「再超时」的度量与解决时限同源），无策略时取默认值。 */
  private long escalateIntervalHours(SlaPolicy policy) {
    if (policy != null && policy.getResolveHours() != null && policy.getResolveHours() > 0) {
      return policy.getResolveHours();
    }
    return DEFAULT_ESCALATE_INTERVAL_HOURS;
  }

  /** 收件人按级别确定：L1 处理人（缺失时 ADMIN）／L2 处理人 + ADMIN／L3+ ADMIN + 广播。 */
  private void notifyRecipients(Ticket ticket, int level, List<Long> adminIds) {
    String title = ticket.getTitle();
    Long assigneeId = ticket.getAssigneeId();
    if (level <= LEVEL_WARNING) {
      // 已响应的工单不该被说成「响应时限」将到（computeSlaStatus 的 WARNING 分支仍会因过期的 respond
      // deadline 命中，见 1.3 报告中的异议条目）——按真实可动的时限措辞，避免误导收件人
      String deadlineLabel = ticket.getSlaRespondedAt() == null ? "响应" : "解决";
      String message = "工单「" + title + "」即将超出 SLA " + deadlineLabel + "时限，请尽快处理";
      if (assigneeId != null) {
        notify(assigneeId, NotificationService.TYPE_SLA_WARNING, message, ticket.getId());
      } else {
        notifyAll(adminIds, NotificationService.TYPE_SLA_WARNING, message, ticket.getId());
      }
      return;
    }
    String message = "工单「" + title + "」已超出 SLA 时限（升级 L" + level + "），请立即跟进";
    notifyAll(adminIds, NotificationService.TYPE_SLA_OVERDUE, message, ticket.getId());
    if (level == LEVEL_OVERDUE) {
      // L2：处理人也在收件人之列（已是 ADMIN 时不重复发）
      if (assigneeId != null && !adminIds.contains(assigneeId)) {
        notify(assigneeId, NotificationService.TYPE_SLA_OVERDUE, message, ticket.getId());
      }
      return;
    }
    // L3+：持续超时，除 ADMIN 外再广播到集成通道（企微/钉钉）
    integrationChannelService.publish(
        "TICKET_SLA_ESCALATED", "工单 #" + ticket.getId() + "「" + title + "」持续超时，已升级至 L" + level);
  }

  private void notifyAll(List<Long> userIds, String type, String message, Long ticketId) {
    for (Long userId : userIds) {
      notify(userId, type, message, ticketId);
    }
  }

  private void notify(Long userId, String type, String message, Long ticketId) {
    notificationService.notify(userId, type, message, "TICKET", ticketId);
  }

  /** 一次载入全部启用策略，避免逐工单查库（N+1）。 */
  private Map<String, SlaPolicy> enabledPolicies() {
    List<SlaPolicy> policies =
        slaPolicyMapper.selectList(
            new LambdaQueryWrapper<SlaPolicy>()
                .eq(SlaPolicy::getEnabled, 1)
                .orderByAsc(SlaPolicy::getId));
    Map<String, SlaPolicy> byPriority = new HashMap<>();
    for (SlaPolicy policy : policies) {
      // 同一优先级多条启用策略时取第一条，与 SlaPolicyService#resolvePolicyFor 的 LIMIT 1 口径一致
      byPriority.putIfAbsent(policy.getPriority(), policy);
    }
    return byPriority;
  }

  /** 一次载入全部启用的 ADMIN，避免逐工单查库（N+1）。 */
  private List<Long> enabledAdminIds() {
    return userMapper
        .selectList(
            new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN").eq(User::getEnabled, 1))
        .stream()
        .map(User::getId)
        .toList();
  }
}
