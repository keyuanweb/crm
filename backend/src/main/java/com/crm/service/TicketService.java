package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.ticket.TicketReplyRequest;
import com.crm.dto.ticket.TicketReplyResponse;
import com.crm.dto.ticket.TicketRequest;
import com.crm.dto.ticket.TicketResponse;
import com.crm.entity.Customer;
import com.crm.entity.SlaPolicy;
import com.crm.entity.Ticket;
import com.crm.entity.TicketReply;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.TicketReplyMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 工单服务（015，FR-C01~C05/C10/C11）：CRUD/状态流转/回复/分配/SLA/行级过滤。 */
@Service
public class TicketService {

  public static final String STATUS_OPEN = "OPEN";
  public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
  public static final String STATUS_RESOLVED = "RESOLVED";
  public static final String STATUS_CLOSED = "CLOSED";

  public static final String SLA_NORMAL = "NORMAL";
  public static final String SLA_WARNING = "WARNING";
  public static final String SLA_OVERDUE = "OVERDUE";

  /**
   * 即将超时阈值（小时）：剩余 ≤ 该值 → WARNING。可经 {@code crm.sla.warning-min-hours} 覆盖（1.3）。
   *
   * <p>⚠️ 本字段是<b>静态可变</b>的：{@link #computeSlaStatus} 是 public static（既有契约，SlaPolicyService
   * 与多个测试直接调用）， 静态方法读不到实例字段，故配置值只能经下面的 setter 落到静态字段。默认值 2 与 {@code @Value} 冒号后的默认值一致， 因此未经 Spring
   * 注入（如单测直接 new）时行为与改造前完全相同。
   */
  private static long warningMinHours = 2;

  private final TicketMapper ticketMapper;
  private final TicketReplyMapper replyMapper;
  private final CustomerMapper customerMapper;
  private final SlaPolicyService slaPolicyService;
  private final UserMapper userMapper;
  private final AuditService auditService;
  private final CustomFieldService customFieldService;
  private final NotificationService notificationService;
  private final SlaCalendarService slaCalendarService;
  private final IntegrationChannelService integrationChannelService;

  public TicketService(
      TicketMapper ticketMapper,
      TicketReplyMapper replyMapper,
      CustomerMapper customerMapper,
      SlaPolicyService slaPolicyService,
      UserMapper userMapper,
      AuditService auditService,
      CustomFieldService customFieldService,
      NotificationService notificationService,
      SlaCalendarService slaCalendarService,
      IntegrationChannelService integrationChannelService) {
    this.ticketMapper = ticketMapper;
    this.replyMapper = replyMapper;
    this.customerMapper = customerMapper;
    this.slaPolicyService = slaPolicyService;
    this.userMapper = userMapper;
    this.auditService = auditService;
    this.customFieldService = customFieldService;
    this.notificationService = notificationService;
    this.slaCalendarService = slaCalendarService;
    this.integrationChannelService = integrationChannelService;
  }

  /** 配置注入：见 {@link #warningMinHours} 的说明（静态方法是既有契约，故落到静态字段）。 */
  @Value("${crm.sla.warning-min-hours:2}")
  void setWarningMinHours(long hours) {
    warningMinHours = hours;
  }

  /** 即将超时阈值（小时）；供 SLA 升级扫描复用同一配置值，避免两处各读一次配置而漂移（1.3）。 */
  public static long warningWindowHours() {
    return warningMinHours;
  }

  @Transactional(readOnly = true)
  public PageResult<TicketResponse> page(
      String keyword,
      String status,
      String priority,
      Long assigneeId,
      Long customerId,
      List<Long> customFieldMatchedIds,
      long page,
      long pageSize) {
    LambdaQueryWrapper<Ticket> qw = new LambdaQueryWrapper<>();
    // 自定义字段筛选（FR-S03）
    if (customFieldMatchedIds != null) {
      if (customFieldMatchedIds.isEmpty()) {
        return PageResult.of(List.of(), 0, page, pageSize);
      }
      qw.in(Ticket::getId, customFieldMatchedIds);
    }
    if (StringUtils.hasText(keyword)) {
      qw.and(
          w ->
              w.like(Ticket::getTitle, keyword.trim())
                  .or()
                  .like(Ticket::getDescription, keyword.trim()));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Ticket::getStatus, status.trim());
    }
    if (StringUtils.hasText(priority)) {
      qw.eq(Ticket::getPriority, priority.trim());
    }
    if (assigneeId != null) {
      qw.eq(Ticket::getAssigneeId, assigneeId);
    }
    if (customerId != null) {
      qw.eq(Ticket::getCustomerId, customerId);
    }
    // SALES 行级过滤：仅看关联自身客户的工单（FR-C13）
    CrmPrincipal principal = SecurityUtil.currentPrincipal();
    if (principal != null && "SALES".equals(principal.role())) {
      Set<Long> myCustomerIds =
          customerMapper
              .selectList(
                  new LambdaQueryWrapper<Customer>()
                      .select(Customer::getId)
                      .eq(Customer::getOwnerId, principal.userId()))
              .stream()
              .map(Customer::getId)
              .collect(Collectors.toSet());
      if (myCustomerIds.isEmpty()) {
        return PageResult.of(List.of(), 0, page, pageSize);
      }
      qw.in(Ticket::getCustomerId, myCustomerIds);
    }
    qw.orderByDesc(Ticket::getId);
    Page<Ticket> p = ticketMapper.selectPage(new Page<>(page, pageSize), qw);
    List<TicketResponse> items = toResponses(p.getRecords());
    // 016：批量回填自定义字段值
    List<Long> ids = p.getRecords().stream().map(Ticket::getId).toList();
    if (!ids.isEmpty()) {
      Map<Long, List<com.crm.dto.customfield.CustomFieldValueDTO>> values =
          customFieldService.readValuesBatch("TICKET", ids);
      items.forEach(i -> i.setCustomFieldValues(values.getOrDefault(i.getId(), List.of())));
    }
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  @Transactional
  public TicketResponse create(TicketRequest req) {
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    Ticket ticket = new Ticket();
    apply(req, ticket);
    ticket.setStatus(STATUS_OPEN);
    ticket.setCreatedBy(SecurityUtil.currentUserId());
    // SLA：按优先级查启用策略计算到期时间
    applySla(ticket, LocalDateTime.now());
    ticketMapper.insert(ticket);
    if (req.getCustomFieldValues() != null && !req.getCustomFieldValues().isEmpty()) {
      customFieldService.saveValues("TICKET", ticket.getId(), req.getCustomFieldValues());
    }
    auditService.record("CREATE", "TICKET", ticket.getId(), "创建工单：" + ticket.getTitle());
    return toResponse(ticketMapper.selectById(ticket.getId()));
  }

  @Transactional
  public TicketResponse update(Long id, TicketRequest req) {
    Ticket existing = require(id);
    String previousPriority = existing.getPriority();
    apply(req, existing);
    // 1.3：改优先级后按新策略重算到期时间与状态（改造前改优先级不重算，SLA 停留旧策略口径）
    if (!java.util.Objects.equals(previousPriority, existing.getPriority())) {
      applySla(existing, LocalDateTime.now());
    }
    existing.setVersion(req.getVersion());
    int rows = ticketMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    if (req.getCustomFieldValues() != null && !req.getCustomFieldValues().isEmpty()) {
      customFieldService.saveValues("TICKET", id, req.getCustomFieldValues());
    }
    auditService.record("UPDATE", "TICKET", id, "编辑工单：" + existing.getTitle());
    return toResponse(ticketMapper.selectById(id));
  }

  @Transactional
  public TicketResponse assign(Long id, Long assigneeId) {
    Ticket ticket = require(id);
    User user = userMapper.selectById(assigneeId);
    if (user == null) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    ticket.setAssigneeId(assigneeId);
    ticketMapper.updateById(ticket);
    auditService.record("ASSIGN", "TICKET", id, "工单分配给用户 " + user.getDisplayName());
    // 016：通知新处理人（本人除外）
    Long current = SecurityUtil.currentUserId();
    if (!java.util.Objects.equals(current, assigneeId)) {
      notificationService.notify(
          assigneeId,
          NotificationService.TYPE_TICKET_ASSIGN,
          "工单「" + ticket.getTitle() + "」已分配给你",
          "TICKET",
          id);
    }
    // 058：集成通道推送（工单分配）
    integrationChannelService.publish(
        "TICKET_ASSIGNED",
        "工单 #" + id + "「" + ticket.getTitle() + "」已分配给 " + user.getDisplayName());
    return toResponse(ticketMapper.selectById(id));
  }

  @Transactional
  public TicketReplyResponse reply(Long id, TicketReplyRequest req) {
    Ticket ticket = require(id);
    if (STATUS_CLOSED.equals(ticket.getStatus())) {
      throw new BusinessException(ErrorCode.TICKET_INVALID_STATE);
    }
    TicketReply reply = new TicketReply();
    reply.setTicketId(id);
    reply.setReplierId(SecurityUtil.currentUserId());
    reply.setContent(req.getContent().trim());
    reply.setCreatedAt(LocalDateTime.now());
    replyMapper.insert(reply);
    // 1.3：首条回复即视为「已响应」，写入 slaRespondedAt 并同步刷新 SLA 状态。
    // 必须先于 computeSlaStatus 的 respond 侧 OVERDUE 判定生效——否则已答复工单会被误判为响应超时。
    if (ticket.getSlaRespondedAt() == null) {
      ticket.setSlaRespondedAt(LocalDateTime.now());
      if (!STATUS_CLOSED.equals(ticket.getStatus())) {
        ticket.setSlaStatus(computeSlaStatus(ticket, LocalDateTime.now()));
      }
      ticketMapper.updateById(ticket);
    }
    auditService.record("REPLY", "TICKET", id, "工单回复");
    // 016：通知处理人（本人除外）
    Long current = SecurityUtil.currentUserId();
    if (ticket.getAssigneeId() != null
        && !java.util.Objects.equals(current, ticket.getAssigneeId())) {
      notificationService.notify(
          ticket.getAssigneeId(),
          NotificationService.TYPE_TICKET_REPLY,
          "工单「" + ticket.getTitle() + "」有新回复",
          "TICKET",
          id);
    }
    TicketReplyResponse resp = toReplyResponse(reply);
    resp.setReplierName(
        SecurityUtil.currentPrincipal() == null
            ? null
            : SecurityUtil.currentPrincipal().username());
    return resp;
  }

  /** 状态流转：OPEN→IN_PROGRESS→RESOLVED→CLOSED（单向，CLOSED 不可回退）。 */
  @Transactional
  public TicketResponse transition(Long id, String targetStatus) {
    Ticket ticket = require(id);
    String from = ticket.getStatus();
    boolean valid =
        (STATUS_OPEN.equals(from) && STATUS_IN_PROGRESS.equals(targetStatus))
            || (STATUS_IN_PROGRESS.equals(from)
                && (STATUS_RESOLVED.equals(targetStatus)
                    || STATUS_IN_PROGRESS.equals(targetStatus)))
            || (STATUS_RESOLVED.equals(from) && STATUS_CLOSED.equals(targetStatus));
    if (!valid) {
      throw new BusinessException(ErrorCode.TICKET_INVALID_STATE);
    }
    ticket.setStatus(targetStatus);
    // 1.3：解决时刻（SLA 解决达成率的数据基础）；只写一次，CLOSED 不覆盖 RESOLVED 的时刻
    if ((STATUS_RESOLVED.equals(targetStatus) || STATUS_CLOSED.equals(targetStatus))
        && ticket.getResolvedAt() == null) {
      ticket.setResolvedAt(LocalDateTime.now());
    }
    ticketMapper.updateById(ticket);
    auditService.record("TRANSITION", "TICKET", id, from + " → " + targetStatus);
    return toResponse(ticketMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    Ticket ticket = require(id);
    ticketMapper.deleteById(id);
    auditService.record("DELETE", "TICKET", id, "删除工单：" + ticket.getTitle());
  }

  /** 工单详情（含回复时间线，SLA 状态落库刷新一次）。 */
  @Transactional(readOnly = true)
  public TicketResponse detail(Long id) {
    Ticket ticket = require(id);
    refreshSla(ticket);
    TicketResponse resp = toResponse(ticketMapper.selectById(id));
    resp.setCustomerName(
        customerMapper.selectById(ticket.getCustomerId()) == null
            ? null
            : customerMapper.selectById(ticket.getCustomerId()).getName());
    if (ticket.getAssigneeId() != null) {
      User user = userMapper.selectById(ticket.getAssigneeId());
      resp.setAssigneeName(user == null ? null : user.getDisplayName());
    }
    resp.setReplyCount(countReplies(id));
    resp.setCustomFieldValues(customFieldService.readValues("TICKET", id));
    return resp;
  }

  /** 工单回复时间线（分页）。 */
  public PageResult<TicketReplyResponse> replies(Long ticketId, long page, long pageSize) {
    require(ticketId);
    LambdaQueryWrapper<TicketReply> qw =
        new LambdaQueryWrapper<TicketReply>().eq(TicketReply::getTicketId, ticketId);
    qw.orderByAsc(TicketReply::getId);
    Page<TicketReply> p = replyMapper.selectPage(new Page<>(page, pageSize), qw);
    List<TicketReplyResponse> items = p.getRecords().stream().map(this::toReplyResponse).toList();
    // 批量装配回复人姓名（避免 N+1）
    Set<Long> userIds =
        p.getRecords().stream().map(TicketReply::getReplierId).collect(Collectors.toSet());
    if (!userIds.isEmpty()) {
      Map<Long, String> names =
          userMapper.selectBatchIds(userIds).stream()
              .collect(Collectors.toMap(User::getId, User::getDisplayName));
      items.forEach(r -> r.setReplierName(names.get(r.getReplierId())));
    }
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  /**
   * 按 SLA 策略计算到期时间并刷新状态。
   *
   * <p>1.3：由 private 提为 public（SLA 升级路径与优先级变更后的重算需要复用），取策略改为经 {@link
   * SlaPolicyService#resolvePolicyFor} —— 条件与原内联查询完全一致，行为等价。
   */
  public void applySla(Ticket ticket, LocalDateTime now) {
    SlaPolicy policy = slaPolicyService.resolvePolicyFor(ticket.getPriority());
    if (policy == null) {
      ticket.setSlaRespondDeadline(null);
      ticket.setSlaResolveDeadline(null);
      ticket.setSlaStatus(null);
      return;
    }
    if (policy.getRespondHours() != null) {
      // 054：按工作日历计算（无配置回退旧行为）
      ticket.setSlaRespondDeadline(
          slaCalendarService.advanceWorkingTime(now, policy.getRespondHours()));
    } else {
      ticket.setSlaRespondDeadline(null);
    }
    if (policy.getResolveHours() != null) {
      ticket.setSlaResolveDeadline(
          slaCalendarService.advanceWorkingTime(now, policy.getResolveHours()));
    } else {
      ticket.setSlaResolveDeadline(null);
    }
    ticket.setSlaStatus(computeSlaStatus(ticket, now));
  }

  /**
   * 计算 SLA 状态：已过 resolve 时限 → OVERDUE；未响应且已过 respond 时限 → OVERDUE；剩余 ≤ {@link #warningMinHours} 小时
   * → WARNING；否则 NORMAL。
   *
   * <p>1.3 变更（响应侧超时判定）：原实现只认 resolve deadline，respond deadline 仅参与 WARNING，因此「响应超时」在数据上 不可判定。现在
   * respond 一侧<b>仅在尚未响应时</b>有意义——已响应（{@code slaRespondedAt != null}）的工单绝不因 respond deadline 判
   * OVERDUE，否则存量已答复工单会被批量误判（V78 的回填就是为此）。
   *
   * <p>同一前置条件也适用于 WARNING：已响应的工单只能因 <b>resolve</b> deadline 进入预警窗口。改造前 WARNING 分支对 已过期的 respond
   * deadline 会取到负的剩余时长，恒 ≤ 阈值，于是「已答复、解决时限还早」的工单会一直停在 WARNING， 并让升级作业发出一封假的「即将超时」通知。
   */
  public static String computeSlaStatus(Ticket ticket, LocalDateTime now) {
    LocalDateTime resolve = ticket.getSlaResolveDeadline();
    if (resolve != null && !resolve.isAfter(now)) {
      return SLA_OVERDUE;
    }
    LocalDateTime respond = ticket.getSlaRespondDeadline();
    if (ticket.getSlaRespondedAt() == null && respond != null && !respond.isAfter(now)) {
      return SLA_OVERDUE;
    }
    // respond 一侧同样只在「尚未响应」时有意义：respond deadline 一旦过期，Duration 为负必然 ≤ 阈值，
    // 不加这个前置条件的话，任何「已按时答复、但解决时限还早」的工单都会恒判 WARNING——升级作业会据此
    // 发出一封假的「即将超时」通知（每张已答复工单一封）。与上面的 OVERDUE 判定同一个道理。
    boolean respondWarning =
        ticket.getSlaRespondedAt() == null
            && respond != null
            && java.time.Duration.between(now, respond).toMinutes() <= warningMinHours * 60;
    boolean resolveWarning =
        resolve != null
            && java.time.Duration.between(now, resolve).toMinutes() <= warningMinHours * 60;
    if (respondWarning || resolveWarning) {
      return SLA_WARNING;
    }
    return SLA_NORMAL;
  }

  /** 读取时按需刷新 SLA 状态并落库（仅详情；列表用 computeSlaStatus 展示避免 N+1 写）。 */
  public void refreshSla(Ticket ticket) {
    if (STATUS_CLOSED.equals(ticket.getStatus())) {
      return;
    }
    String status = computeSlaStatus(ticket, LocalDateTime.now());
    if (!java.util.Objects.equals(status, ticket.getSlaStatus())) {
      ticket.setSlaStatus(status);
      ticketMapper.updateById(ticket);
    }
  }

  private void apply(TicketRequest req, Ticket ticket) {
    ticket.setCustomerId(req.getCustomerId());
    ticket.setContactId(req.getContactId());
    ticket.setTitle(req.getTitle().trim());
    ticket.setDescription(req.getDescription());
    ticket.setPriority(req.getPriority().trim());
    ticket.setAssigneeId(req.getAssigneeId());
    ticket.setRemark(req.getRemark());
  }

  private Ticket require(Long id) {
    Ticket ticket = ticketMapper.selectById(id);
    if (ticket == null) {
      throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
    }
    return ticket;
  }

  private List<TicketResponse> toResponses(List<Ticket> tickets) {
    if (tickets.isEmpty()) {
      return List.of();
    }
    // 批量装配客户名/处理人名/回复数（避免 N+1）
    Set<Long> customerIds = tickets.stream().map(Ticket::getCustomerId).collect(Collectors.toSet());
    Set<Long> userIds =
        tickets.stream()
            .map(Ticket::getAssigneeId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, String> customerNames =
        customerIds.isEmpty()
            ? Map.of()
            : customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
    Map<Long, String> userNames =
        userIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
    Set<Long> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
    Map<Long, Long> replyCounts =
        replyMapper
            .selectList(
                new LambdaQueryWrapper<TicketReply>().in(TicketReply::getTicketId, ticketIds))
            .stream()
            .collect(Collectors.groupingBy(TicketReply::getTicketId, Collectors.counting()));
    return tickets.stream()
        .map(
            t -> {
              // 列表仅计算最新 SLA 状态用于展示（不落库，避免 N+1 写）
              if (!STATUS_CLOSED.equals(t.getStatus()) && t.getSlaResolveDeadline() != null) {
                t.setSlaStatus(computeSlaStatus(t, LocalDateTime.now()));
              }
              TicketResponse resp = toResponse(t);
              resp.setCustomerName(customerNames.get(t.getCustomerId()));
              resp.setAssigneeName(
                  t.getAssigneeId() == null ? null : userNames.get(t.getAssigneeId()));
              resp.setReplyCount(replyCounts.getOrDefault(t.getId(), 0L));
              return resp;
            })
        .toList();
  }

  private TicketResponse toResponse(Ticket ticket) {
    TicketResponse resp = new TicketResponse();
    resp.setId(ticket.getId());
    resp.setCustomerId(ticket.getCustomerId());
    resp.setContactId(ticket.getContactId());
    resp.setTitle(ticket.getTitle());
    resp.setDescription(ticket.getDescription());
    resp.setPriority(ticket.getPriority());
    resp.setStatus(ticket.getStatus());
    resp.setAssigneeId(ticket.getAssigneeId());
    resp.setSlaRespondDeadline(ticket.getSlaRespondDeadline());
    resp.setSlaResolveDeadline(ticket.getSlaResolveDeadline());
    resp.setSlaStatus(ticket.getSlaStatus());
    resp.setSlaRespondedAt(ticket.getSlaRespondedAt());
    resp.setResolvedAt(ticket.getResolvedAt());
    resp.setEscalateLevel(ticket.getEscalateLevel());
    resp.setLastEscalatedAt(ticket.getLastEscalatedAt());
    resp.setRemark(ticket.getRemark());
    resp.setVersion(ticket.getVersion());
    resp.setCreatedAt(ticket.getCreatedAt());
    return resp;
  }

  private TicketReplyResponse toReplyResponse(TicketReply reply) {
    TicketReplyResponse resp = new TicketReplyResponse();
    resp.setId(reply.getId());
    resp.setTicketId(reply.getTicketId());
    resp.setReplierId(reply.getReplierId());
    resp.setContent(reply.getContent());
    resp.setCreatedAt(reply.getCreatedAt());
    return resp;
  }

  private long countReplies(Long ticketId) {
    Long count =
        replyMapper.selectCount(
            new LambdaQueryWrapper<TicketReply>().eq(TicketReply::getTicketId, ticketId));
    return count == null ? 0 : count;
  }
}
