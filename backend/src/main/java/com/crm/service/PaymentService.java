package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.order.PaymentRecordResponse;
import com.crm.dto.order.PaymentRequest;
import com.crm.dto.order.PlanItemResponse;
import com.crm.entity.PaymentPlan;
import com.crm.entity.PaymentRecord;
import com.crm.entity.SalesOrder;
import com.crm.repository.PaymentPlanMapper;
import com.crm.repository.PaymentRecordMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 回款服务（009，FR-OP05~OP08）：登记/状态驱动/台账/逾期临期提醒。 */
@Service
public class PaymentService {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PARTIAL = "PARTIAL";
  public static final String STATUS_PAID = "PAID";

  /** 临期窗口（天）。 */
  public static final int DUE_SOON_DAYS = 3;

  private final SalesOrderMapper orderMapper;
  private final PaymentPlanMapper planMapper;
  private final PaymentRecordMapper recordMapper;
  private final AuditService auditService;

  public PaymentService(
      SalesOrderMapper orderMapper,
      PaymentPlanMapper planMapper,
      PaymentRecordMapper recordMapper,
      AuditService auditService) {
    this.orderMapper = orderMapper;
    this.planMapper = planMapper;
    this.recordMapper = recordMapper;
    this.auditService = auditService;
  }

  @Transactional
  public void recordPayment(Long orderId, PaymentRequest req) {
    SalesOrder order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
    }
    PaymentPlan plan = planMapper.selectById(req.getPlanId());
    if (plan == null || !orderId.equals(plan.getOrderId())) {
      throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
    }
    if (plan.getStatus().equals(STATUS_PAID)) {
      throw new BusinessException(ErrorCode.PAYMENT_EXCEEDS);
    }

    // 该期已收
    long received = receivedAmount(plan.getId());
    long newReceived = received + req.getAmount();
    if (newReceived > plan.getAmount()) {
      throw new BusinessException(ErrorCode.PAYMENT_EXCEEDS);
    }

    // 插入回款记录
    PaymentRecord record = new PaymentRecord();
    record.setPlanId(plan.getId());
    record.setOrderId(orderId);
    record.setAmount(req.getAmount());
    record.setPaidAt(req.getPaidAt());
    record.setMethod(StringUtils.hasText(req.getMethod()) ? req.getMethod().trim() : "TRANSFER");
    record.setRecordedBy(SecurityUtil.currentUserId());
    recordMapper.insert(record);

    // 重算期次状态
    String planStatus = newReceived >= plan.getAmount() ? STATUS_PAID : STATUS_PARTIAL;
    plan.setStatus(planStatus);
    planMapper.updateById(plan);

    // 重算订单状态
    refreshOrderStatus(orderId);

    auditService.record(
        "PAYMENT",
        "SALES_ORDER",
        orderId,
        "登记回款：" + req.getAmount() + "（期次 " + plan.getSeqNo() + "）");
  }

  /** 重算订单状态：全 PAID → PAID；部分 → PARTIAL；否则 PENDING。 */
  private void refreshOrderStatus(Long orderId) {
    List<PaymentPlan> plans = plansOf(orderId);
    if (plans.isEmpty()) {
      return;
    }
    boolean allPaid = plans.stream().allMatch(p -> STATUS_PAID.equals(p.getStatus()));
    boolean anyPaid = plans.stream().anyMatch(p -> STATUS_PAID.equals(p.getStatus()));
    String status = allPaid ? STATUS_PAID : (anyPaid ? STATUS_PARTIAL : STATUS_PENDING);
    // 用 UpdateWrapper 显式更新 status，避免乐观锁 version 不匹配导致更新失败
    orderMapper.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SalesOrder>()
            .eq(SalesOrder::getId, orderId)
            .set(SalesOrder::getStatus, status));
  }

  /** 组装台账：期次 + 已收/未收 + 提醒标识。 */
  public List<PlanItemResponse> buildPlans(List<PaymentPlan> plans) {
    if (plans.isEmpty()) {
      return List.of();
    }
    Map<Long, Long> receivedByPlan =
        plans.stream()
            .collect(
                Collectors.toMap(PaymentPlan::getId, p -> receivedAmount(p.getId()), (a, b) -> a));
    LocalDate today = LocalDate.now();
    return plans.stream()
        .map(
            p -> {
              PlanItemResponse resp = new PlanItemResponse();
              resp.setId(p.getId());
              resp.setSeqNo(p.getSeqNo());
              resp.setAmount(p.getAmount());
              resp.setDueDate(p.getDueDate());
              resp.setDescription(p.getDescription());
              resp.setStatus(p.getStatus());
              long received = receivedByPlan.getOrDefault(p.getId(), 0L);
              resp.setReceivedAmount(received);
              resp.setUnpaidAmount(p.getAmount() - received);
              boolean settled = p.getStatus().equals(STATUS_PAID);
              if (settled) {
                resp.setReminderStatus("PAID");
              } else if (p.getDueDate().isBefore(today)) {
                resp.setReminderStatus("OVERDUE");
                resp.setOverdueDays(ChronoUnit.DAYS.between(p.getDueDate(), today));
              } else if (!p.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS))) {
                resp.setReminderStatus("DUE_SOON");
              } else {
                resp.setReminderStatus("NORMAL");
              }
              return resp;
            })
        .toList();
  }

  public long receivedAmount(Long planId) {
    List<PaymentRecord> records =
        recordMapper.selectList(
            new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getPlanId, planId));
    return records.stream().mapToLong(r -> r.getAmount() == null ? 0L : r.getAmount()).sum();
  }

  /** 订单已收总额（台账展示用）。 */
  public long paidAmountOf(Long orderId) {
    List<PaymentRecord> records =
        recordMapper.selectList(
            new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOrderId, orderId));
    return records.stream().mapToLong(r -> r.getAmount() == null ? 0L : r.getAmount()).sum();
  }

  public List<PaymentRecord> recordsOf(Long orderId) {
    return recordMapper.selectList(
        new LambdaQueryWrapper<PaymentRecord>()
            .eq(PaymentRecord::getOrderId, orderId)
            .orderByDesc(PaymentRecord::getId));
  }

  public List<PaymentPlan> plansOf(Long orderId) {
    return planMapper.selectList(
        new LambdaQueryWrapper<PaymentPlan>()
            .eq(PaymentPlan::getOrderId, orderId)
            .orderByAsc(PaymentPlan::getSeqNo));
  }

  /** 回款提醒汇总：逾期与临期期次数。 */
  public Map<String, Long> reminderSummary() {
    List<PaymentPlan> all =
        planMapper.selectList(
            new LambdaQueryWrapper<PaymentPlan>()
                .select(
                    PaymentPlan::getId,
                    PaymentPlan::getAmount,
                    PaymentPlan::getDueDate,
                    PaymentPlan::getStatus));
    LocalDate today = LocalDate.now();
    long overdue = 0;
    long dueSoon = 0;
    for (PaymentPlan p : all) {
      if (STATUS_PAID.equals(p.getStatus())) {
        continue;
      }
      if (p.getDueDate().isBefore(today)) {
        overdue++;
      } else if (!p.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS))) {
        dueSoon++;
      }
    }
    return Map.of("overdueCount", overdue, "dueSoonCount", dueSoon);
  }

  public PaymentRecordResponse toRecordResponse(PaymentRecord r) {
    PaymentRecordResponse resp = new PaymentRecordResponse();
    resp.setId(r.getId());
    resp.setPlanId(r.getPlanId());
    resp.setAmount(r.getAmount());
    resp.setPaidAt(r.getPaidAt());
    resp.setMethod(r.getMethod());
    resp.setRecordedBy(r.getRecordedBy());
    resp.setCreatedAt(r.getCreatedAt());
    return resp;
  }
}
