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
import com.crm.repository.SlaPolicyMapper;
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

  /** 即将超时阈值：剩余 ≤ min(总时限×25%, 2h)。 */
  private static final long WARNING_MIN_HOURS = 2;

  private static final double WARNING_RATIO = 0.25;

  private final TicketMapper ticketMapper;
  private final TicketReplyMapper replyMapper;
  private final CustomerMapper customerMapper;
  private final SlaPolicyMapper slaPolicyMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public TicketService(
      TicketMapper ticketMapper,
      TicketReplyMapper replyMapper,
      CustomerMapper customerMapper,
      SlaPolicyMapper slaPolicyMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.ticketMapper = ticketMapper;
    this.replyMapper = replyMapper;
    this.customerMapper = customerMapper;
    this.slaPolicyMapper = slaPolicyMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  public PageResult<TicketResponse> page(
      String keyword,
      String status,
      String priority,
      Long assigneeId,
      Long customerId,
      long page,
      long pageSize) {
    LambdaQueryWrapper<Ticket> qw = new LambdaQueryWrapper<>();
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
    return PageResult.of(toResponses(p.getRecords()), p.getTotal(), page, pageSize);
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
    auditService.record("CREATE", "TICKET", ticket.getId(), "创建工单：" + ticket.getTitle());
    return toResponse(ticketMapper.selectById(ticket.getId()));
  }

  @Transactional
  public TicketResponse update(Long id, TicketRequest req) {
    Ticket existing = require(id);
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = ticketMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
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
    auditService.record("REPLY", "TICKET", id, "工单回复");
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

  /** 按 SLA 策略计算到期时间并刷新状态。 */
  private void applySla(Ticket ticket, LocalDateTime now) {
    SlaPolicy policy =
        slaPolicyMapper.selectOne(
            new LambdaQueryWrapper<SlaPolicy>()
                .eq(SlaPolicy::getPriority, ticket.getPriority())
                .eq(SlaPolicy::getEnabled, 1)
                .last("LIMIT 1"));
    if (policy == null) {
      ticket.setSlaRespondDeadline(null);
      ticket.setSlaResolveDeadline(null);
      ticket.setSlaStatus(null);
      return;
    }
    if (policy.getRespondHours() != null) {
      ticket.setSlaRespondDeadline(now.plusHours(policy.getRespondHours()));
    } else {
      ticket.setSlaRespondDeadline(null);
    }
    if (policy.getResolveHours() != null) {
      ticket.setSlaResolveDeadline(now.plusHours(policy.getResolveHours()));
    } else {
      ticket.setSlaResolveDeadline(null);
    }
    ticket.setSlaStatus(computeSlaStatus(ticket, now));
  }

  /** 计算 SLA 状态：已过 resolve 时限 → OVERDUE；剩余 ≤2h → WARNING；否则 NORMAL。 */
  public static String computeSlaStatus(Ticket ticket, LocalDateTime now) {
    LocalDateTime resolve = ticket.getSlaResolveDeadline();
    if (resolve != null && !resolve.isAfter(now)) {
      return SLA_OVERDUE;
    }
    LocalDateTime respond = ticket.getSlaRespondDeadline();
    boolean respondWarning =
        respond != null
            && java.time.Duration.between(now, respond).toMinutes() <= WARNING_MIN_HOURS * 60;
    boolean resolveWarning =
        resolve != null
            && java.time.Duration.between(now, resolve).toMinutes() <= WARNING_MIN_HOURS * 60;
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
