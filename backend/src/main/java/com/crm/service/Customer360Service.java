package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.customer.Customer360Response;
import com.crm.dto.customer.Customer360Response.AmountSummary;
import com.crm.dto.customer.Customer360Response.ContractBrief;
import com.crm.dto.customer.Customer360Response.OrderBrief;
import com.crm.dto.customer.Customer360Response.PaymentSummary;
import com.crm.dto.customer.Customer360Response.TicketBrief;
import com.crm.entity.Contract;
import com.crm.entity.FollowUp;
import com.crm.entity.HealthScoreConfig;
import com.crm.entity.PaymentPlan;
import com.crm.entity.PaymentRecord;
import com.crm.entity.SalesOrder;
import com.crm.entity.Ticket;
import com.crm.repository.ContractMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.HealthScoreConfigMapper;
import com.crm.repository.PaymentPlanMapper;
import com.crm.repository.PaymentRecordMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.repository.TicketMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 客户 360 聚合服务（018-customer-360，FR-001/002）：按 customerId 聚合订单/回款/合同/工单、金额汇总 与健康度评分。批量装配避免
 * N+1（章程原则五）。
 */
@Service
public class Customer360Service {

  private final SalesOrderMapper orderMapper;
  private final PaymentPlanMapper planMapper;
  private final PaymentRecordMapper recordMapper;
  private final ContractMapper contractMapper;
  private final TicketMapper ticketMapper;
  private final FollowUpMapper followUpMapper;
  private final HealthScoreConfigMapper configMapper;
  private final HealthScoreService healthScoreService;

  public Customer360Service(
      SalesOrderMapper orderMapper,
      PaymentPlanMapper planMapper,
      PaymentRecordMapper recordMapper,
      ContractMapper contractMapper,
      TicketMapper ticketMapper,
      FollowUpMapper followUpMapper,
      HealthScoreConfigMapper configMapper,
      HealthScoreService healthScoreService) {
    this.orderMapper = orderMapper;
    this.planMapper = planMapper;
    this.recordMapper = recordMapper;
    this.contractMapper = contractMapper;
    this.ticketMapper = ticketMapper;
    this.followUpMapper = followUpMapper;
    this.configMapper = configMapper;
    this.healthScoreService = healthScoreService;
  }

  /** 聚合客户 360 数据（含健康度评分）。 */
  public Customer360Response aggregate(Long customerId) {
    Customer360Response resp = new Customer360Response();
    LocalDateTime now = LocalDateTime.now();

    // 订单
    List<SalesOrder> orders =
        orderMapper.selectList(
            new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerId, customerId)
                .orderByDesc(SalesOrder::getId));
    resp.setOrders(
        orders.stream()
            .map(
                o -> {
                  OrderBrief b = new OrderBrief();
                  b.setId(o.getId());
                  b.setOrderNo(o.getOrderNo());
                  b.setTitle(o.getTitle());
                  b.setAmount(o.getAmount());
                  b.setStatus(o.getStatus());
                  return b;
                })
            .toList());

    // 回款（按订单批量装配）
    long totalOrder = 0;
    long totalPaid = 0;
    long totalOverdue = 0;
    int overduePaymentCount = 0;
    if (!orders.isEmpty()) {
      List<Long> orderIds = orders.stream().map(SalesOrder::getId).toList();
      List<PaymentPlan> plans =
          planMapper.selectList(
              new LambdaQueryWrapper<PaymentPlan>()
                  .in(PaymentPlan::getOrderId, orderIds)
                  .orderByAsc(PaymentPlan::getDueDate));
      Map<Long, List<PaymentRecord>> recordsByOrder =
          plans.isEmpty()
              ? Map.of()
              : recordMapper
                  .selectList(
                      new LambdaQueryWrapper<PaymentRecord>()
                          .in(PaymentRecord::getOrderId, orderIds))
                  .stream()
                  .collect(Collectors.groupingBy(PaymentRecord::getOrderId));
      LocalDate today = LocalDate.now();

      List<PaymentSummary> summaries = new java.util.ArrayList<>();
      for (SalesOrder o : orders) {
        PaymentSummary ps = new PaymentSummary();
        ps.setOrderId(o.getId());
        ps.setOrderNo(o.getOrderNo());
        long orderPaid =
            recordsByOrder.getOrDefault(o.getId(), List.of()).stream()
                .mapToLong(PaymentRecord::getAmount)
                .sum();
        long planTotal =
            plans.stream()
                .filter(p -> p.getOrderId().equals(o.getId()))
                .mapToLong(PaymentPlan::getAmount)
                .sum();
        long overdue =
            plans.stream()
                .filter(p -> p.getOrderId().equals(o.getId()))
                .filter(p -> !"PAID".equals(p.getStatus()))
                .filter(p -> p.getDueDate() != null && p.getDueDate().isBefore(today))
                .mapToLong(PaymentPlan::getAmount)
                .sum();
        ps.setTotalPlan(planTotal);
        ps.setPaid(orderPaid);
        ps.setOverdue(overdue);
        summaries.add(ps);

        totalOrder += o.getAmount() == null ? 0 : o.getAmount();
        totalPaid += orderPaid;
        totalOverdue += overdue;
        if (overdue > 0) {
          overduePaymentCount++;
        }
      }
      resp.setPaymentSummaries(summaries);
    }

    AmountSummary summary = new AmountSummary();
    summary.setTotalOrder(totalOrder);
    summary.setPaid(totalPaid);
    summary.setDueOverdue(totalOverdue);
    resp.setAmountSummary(summary);

    // 合同
    List<Contract> contracts =
        contractMapper.selectList(
            new LambdaQueryWrapper<Contract>()
                .eq(Contract::getCustomerId, customerId)
                .orderByDesc(Contract::getId));
    resp.setContracts(
        contracts.stream()
            .map(
                c -> {
                  ContractBrief b = new ContractBrief();
                  b.setId(c.getId());
                  b.setContractNo(c.getContractNo());
                  b.setTitle(c.getTitle());
                  b.setAmount(c.getAmount());
                  b.setStatus(c.getStatus());
                  b.setStartDate(c.getStartDate());
                  b.setEndDate(c.getEndDate());
                  return b;
                })
            .toList());

    // 工单
    List<Ticket> tickets =
        ticketMapper.selectList(
            new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getCustomerId, customerId)
                .orderByDesc(Ticket::getId));
    resp.setTickets(
        tickets.stream()
            .map(
                t -> {
                  TicketBrief b = new TicketBrief();
                  b.setId(t.getId());
                  b.setTitle(t.getTitle());
                  b.setPriority(t.getPriority());
                  b.setStatus(t.getStatus());
                  b.setSlaStatus(t.getSlaStatus());
                  return b;
                })
            .toList());

    // 健康度评分输入（近期跟进/未解决工单）
    List<FollowUp> followUps =
        followUpMapper.selectList(
            new LambdaQueryWrapper<FollowUp>()
                .eq(FollowUp::getCustomerId, customerId)
                .orderByDesc(FollowUp::getCreatedAt));
    Integer lastFollowUpDays =
        followUps.isEmpty() ? null : daysBetween(followUps.get(0).getCreatedAt(), now);
    int openTicketCount =
        (int)
            tickets.stream()
                .filter(t -> "OPEN".equals(t.getStatus()) || "IN_PROGRESS".equals(t.getStatus()))
                .count();
    // 近期互动：跟进/订单/工单中最近一次
    Integer lastActivityDays = null;
    LocalDateTime latest = null;
    if (!followUps.isEmpty()) {
      latest = followUps.get(0).getCreatedAt();
    }
    if (!orders.isEmpty() && orders.get(0).getUpdatedAt() != null) {
      if (latest == null || orders.get(0).getUpdatedAt().isAfter(latest)) {
        latest = orders.get(0).getUpdatedAt();
      }
    }
    if (!tickets.isEmpty() && tickets.get(0).getUpdatedAt() != null) {
      if (latest == null || tickets.get(0).getUpdatedAt().isAfter(latest)) {
        latest = tickets.get(0).getUpdatedAt();
      }
    }
    if (latest != null) {
      lastActivityDays = daysBetween(latest, now);
    }

    List<HealthScoreConfig> configs =
        configMapper.selectList(
            new LambdaQueryWrapper<HealthScoreConfig>()
                .eq(HealthScoreConfig::getEnabled, 1)
                .orderByAsc(HealthScoreConfig::getSortOrder));
    HealthScoreService.HealthInput input =
        HealthScoreService.HealthInput.builder()
            .lastFollowUpDays(lastFollowUpDays)
            .overduePaymentCount(overduePaymentCount)
            .openTicketCount(openTicketCount)
            .totalOrderAmount(totalOrder)
            .lastActivityDays(lastActivityDays)
            .build();
    resp.setHealth(healthScoreService.score(input, configs));

    return resp;
  }

  /**
   * 批量健康度等级（KPI 大屏优化，023）：按 IN 查询各关联表 + 内存分组，一次装配多个客户的健康度， 避免逐客户 aggregate 的 N+1 查询。返回 customerId →
   * health.level。
   */
  public Map<Long, String> healthLevelsBatch(List<Long> customerIds) {
    if (customerIds == null || customerIds.isEmpty()) {
      return Map.of();
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDate today = LocalDate.now();

    // 订单（客户 → 订单列表）
    List<SalesOrder> allOrders =
        orderMapper.selectList(
            new LambdaQueryWrapper<SalesOrder>()
                .in(SalesOrder::getCustomerId, customerIds)
                .orderByDesc(SalesOrder::getId));
    Map<Long, List<SalesOrder>> ordersByCustomer =
        allOrders.stream().collect(Collectors.groupingBy(SalesOrder::getCustomerId));
    List<Long> allOrderIds = allOrders.stream().map(SalesOrder::getId).toList();

    // 回款计划（订单 → 计划列表）
    Map<Long, List<PaymentPlan>> plansByOrder =
        allOrderIds.isEmpty()
            ? Map.of()
            : planMapper
                .selectList(
                    new LambdaQueryWrapper<PaymentPlan>()
                        .in(PaymentPlan::getOrderId, allOrderIds)
                        .orderByAsc(PaymentPlan::getDueDate))
                .stream()
                .collect(Collectors.groupingBy(PaymentPlan::getOrderId));

    // 回款记录（订单 → 记录列表）
    Map<Long, List<PaymentRecord>> recordsByOrder =
        allOrderIds.isEmpty()
            ? Map.of()
            : recordMapper
                .selectList(
                    new LambdaQueryWrapper<PaymentRecord>()
                        .in(PaymentRecord::getOrderId, allOrderIds))
                .stream()
                .collect(Collectors.groupingBy(PaymentRecord::getOrderId));

    // 工单（客户 → 工单列表）
    Map<Long, List<Ticket>> ticketsByCustomer =
        ticketMapper
            .selectList(
                new LambdaQueryWrapper<Ticket>()
                    .in(Ticket::getCustomerId, customerIds)
                    .orderByDesc(Ticket::getId))
            .stream()
            .collect(Collectors.groupingBy(Ticket::getCustomerId));

    // 跟进（客户 → 跟进列表，已按时间倒序，首条即最近）
    Map<Long, List<FollowUp>> followUpsByCustomer =
        followUpMapper
            .selectList(
                new LambdaQueryWrapper<FollowUp>()
                    .in(FollowUp::getCustomerId, customerIds)
                    .orderByDesc(FollowUp::getCreatedAt))
            .stream()
            .collect(Collectors.groupingBy(FollowUp::getCustomerId));

    // 健康配置一次加载
    List<HealthScoreConfig> configs =
        configMapper.selectList(
            new LambdaQueryWrapper<HealthScoreConfig>()
                .eq(HealthScoreConfig::getEnabled, 1)
                .orderByAsc(HealthScoreConfig::getSortOrder));

    Map<Long, String> result = new java.util.HashMap<>();
    for (Long customerId : customerIds) {
      List<SalesOrder> orders = ordersByCustomer.getOrDefault(customerId, List.of());
      List<Ticket> tickets = ticketsByCustomer.getOrDefault(customerId, List.of());
      List<FollowUp> followUps = followUpsByCustomer.getOrDefault(customerId, List.of());

      long totalOrder = 0;
      long totalOverdue = 0;
      int overduePaymentCount = 0;
      for (SalesOrder o : orders) {
        long orderPaid =
            recordsByOrder.getOrDefault(o.getId(), List.of()).stream()
                .mapToLong(PaymentRecord::getAmount)
                .sum();
        long overdue =
            plansByOrder.getOrDefault(o.getId(), List.of()).stream()
                .filter(p -> !"PAID".equals(p.getStatus()))
                .filter(p -> p.getDueDate() != null && p.getDueDate().isBefore(today))
                .mapToLong(PaymentPlan::getAmount)
                .sum();
        totalOrder += o.getAmount() == null ? 0 : o.getAmount();
        totalOverdue += overdue;
        if (overdue > 0) {
          overduePaymentCount++;
        }
      }

      Integer lastFollowUpDays =
          followUps.isEmpty() ? null : daysBetween(followUps.get(0).getCreatedAt(), now);
      int openTicketCount =
          (int)
              tickets.stream()
                  .filter(t -> "OPEN".equals(t.getStatus()) || "IN_PROGRESS".equals(t.getStatus()))
                  .count();

      // 近期互动：跟进/订单/工单中最近一次
      Integer lastActivityDays = null;
      LocalDateTime latest = followUps.isEmpty() ? null : followUps.get(0).getCreatedAt();
      if (!orders.isEmpty() && orders.get(0).getUpdatedAt() != null) {
        if (latest == null || orders.get(0).getUpdatedAt().isAfter(latest)) {
          latest = orders.get(0).getUpdatedAt();
        }
      }
      if (!tickets.isEmpty() && tickets.get(0).getUpdatedAt() != null) {
        if (latest == null || tickets.get(0).getUpdatedAt().isAfter(latest)) {
          latest = tickets.get(0).getUpdatedAt();
        }
      }
      if (latest != null) {
        lastActivityDays = daysBetween(latest, now);
      }

      HealthScoreService.HealthInput input =
          HealthScoreService.HealthInput.builder()
              .lastFollowUpDays(lastFollowUpDays)
              .overduePaymentCount(overduePaymentCount)
              .openTicketCount(openTicketCount)
              .totalOrderAmount(totalOrder)
              .lastActivityDays(lastActivityDays)
              .build();
      result.put(customerId, healthScoreService.score(input, configs).getLevel());
    }
    return result;
  }

  private Integer daysBetween(LocalDateTime from, LocalDateTime to) {
    if (from == null) {
      return null;
    }
    return (int) Math.max(0, ChronoUnit.DAYS.between(from, to));
  }
}
