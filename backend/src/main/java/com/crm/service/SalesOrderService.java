package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.order.OrderRequest;
import com.crm.dto.order.OrderResponse;
import com.crm.dto.order.PlanItemResponse;
import com.crm.entity.Contract;
import com.crm.entity.Customer;
import com.crm.entity.PaymentPlan;
import com.crm.entity.PaymentRecord;
import com.crm.entity.SalesOrder;
import com.crm.repository.ContractMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.PaymentPlanMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 订单服务（009，FR-OP01~OP04）：CRUD/基于合同创建/期次维护。 */
@Service
public class SalesOrderService {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PARTIAL = "PARTIAL";
  public static final String STATUS_PAID = "PAID";

  private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final SalesOrderMapper orderMapper;
  private final PaymentPlanMapper planMapper;
  private final CustomerMapper customerMapper;
  private final ContractMapper contractMapper;
  private final PaymentService paymentService;
  private final AuditService auditService;

  public SalesOrderService(
      SalesOrderMapper orderMapper,
      PaymentPlanMapper planMapper,
      CustomerMapper customerMapper,
      ContractMapper contractMapper,
      PaymentService paymentService,
      AuditService auditService) {
    this.orderMapper = orderMapper;
    this.planMapper = planMapper;
    this.customerMapper = customerMapper;
    this.contractMapper = contractMapper;
    this.paymentService = paymentService;
    this.auditService = auditService;
  }

  public PageResult<OrderResponse> page(
      String keyword, String status, Long customerId, long page, long pageSize) {
    LambdaQueryWrapper<SalesOrder> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(w -> w.like(SalesOrder::getOrderNo, kw).or().like(SalesOrder::getTitle, kw));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(SalesOrder::getStatus, status.trim());
    }
    if (customerId != null) {
      qw.eq(SalesOrder::getCustomerId, customerId);
    }
    qw.orderByDesc(SalesOrder::getId);
    Page<SalesOrder> p = orderMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(o -> toResponse(o, false)).toList(),
        p.getTotal(),
        page,
        pageSize);
  }

  public OrderResponse detail(Long id) {
    return toResponse(require(id), true);
  }

  @Transactional
  public OrderResponse create(OrderRequest req) {
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    Long amount = req.getAmount();
    if (req.getContractId() != null) {
      Contract contract = contractMapper.selectById(req.getContractId());
      if (contract == null) {
        throw new BusinessException(ErrorCode.CONTRACT_NOT_FOUND);
      }
      if (!ContractService.STATUS_EFFECTIVE.equals(contract.getStatus())) {
        throw new BusinessException(ErrorCode.CONTRACT_NOT_EFFECTIVE);
      }
      if (!req.getCustomerId().equals(contract.getCustomerId())) {
        throw new BusinessException(ErrorCode.OPPORTUNITY_CUSTOMER_MISMATCH);
      }
      amount = contract.getAmount();
    }
    long orderAmount = amount == null ? 0L : amount;

    SalesOrder order = new SalesOrder();
    order.setOrderNo(nextOrderNo(LocalDate.now()));
    order.setTitle(req.getTitle().trim());
    order.setCustomerId(req.getCustomerId());
    order.setContractId(req.getContractId());
    order.setAmount(orderAmount);
    order.setStatus(STATUS_PENDING);
    order.setDescription(req.getDescription());
    order.setCreatedBy(SecurityUtil.currentUserId());
    orderMapper.insert(order);

    createPlans(order.getId(), req, orderAmount);
    auditService.record("CREATE", "SALES_ORDER", order.getId(), "创建订单：" + order.getOrderNo());
    return toResponse(orderMapper.selectById(order.getId()), true);
  }

  @Transactional
  public OrderResponse update(Long id, OrderRequest req) {
    SalesOrder existing = require(id);
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    long orderAmount = req.getAmount() == null ? 0L : req.getAmount();

    existing.setTitle(req.getTitle().trim());
    existing.setCustomerId(req.getCustomerId());
    existing.setContractId(req.getContractId());
    existing.setAmount(orderAmount);
    existing.setDescription(req.getDescription());
    existing.setVersion(req.getVersion());
    int rows = orderMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }

    rebuildPlans(id, req, orderAmount);
    auditService.record("UPDATE", "SALES_ORDER", id, "编辑订单：" + existing.getOrderNo());
    return toResponse(orderMapper.selectById(id), true);
  }

  @Transactional
  public void delete(Long id) {
    SalesOrder order = require(id);
    List<PaymentRecord> records = paymentService.recordsOf(id);
    if (!records.isEmpty()) {
      throw new BusinessException(ErrorCode.ORDER_HAS_PAYMENTS);
    }
    orderMapper.deleteById(id);
    // 级联删除期次
    planMapper.delete(new LambdaQueryWrapper<PaymentPlan>().eq(PaymentPlan::getOrderId, id));
    auditService.record("DELETE", "SALES_ORDER", id, "逻辑删除订单：" + order.getOrderNo());
  }

  /** 创建期次：请求提供则按请求（合计校验）；否则自动一期。 */
  private void createPlans(Long orderId, OrderRequest req, long orderAmount) {
    List<OrderRequest.PlanItemRequest> plans = req.getPlans();
    if (plans == null || plans.isEmpty()) {
      insertPlan(orderId, 1, orderAmount, LocalDate.now().plusMonths(1), "整单");
      return;
    }
    long sum = plans.stream().mapToLong(p -> p.getAmount() == null ? 0L : p.getAmount()).sum();
    if (sum != orderAmount) {
      throw new BusinessException(ErrorCode.PLAN_AMOUNT_MISMATCH);
    }
    int seq = 1;
    for (OrderRequest.PlanItemRequest p : plans) {
      insertPlan(orderId, seq++, p.getAmount(), p.getDueDate(), p.getDescription());
    }
  }

  /** 重建期次：已回款期次保留（跳过删除），其余按新计划重建。 */
  private void rebuildPlans(Long orderId, OrderRequest req, long orderAmount) {
    List<PaymentPlan> existing = paymentService.plansOf(orderId);
    // 校验已回款期次不会被删除：对比新计划期数（简化：全部已回款期次必须在新计划中存在）
    long paidExisting =
        existing.stream().filter(p -> PaymentService.STATUS_PAID.equals(p.getStatus())).count();
    long newPlans = req.getPlans() == null ? 0 : req.getPlans().size();
    if (req.getPlans() == null || req.getPlans().isEmpty()) {
      // 未提供期次：若存在已回款期次则拒绝（避免隐式删除）
      if (paidExisting > 0) {
        throw new BusinessException(ErrorCode.PAYMENT_EXISTS);
      }
      // 否则删除全部并重建一期
      planMapper.delete(new LambdaQueryWrapper<PaymentPlan>().eq(PaymentPlan::getOrderId, orderId));
      insertPlan(orderId, 1, orderAmount, LocalDate.now().plusMonths(1), "整单");
      return;
    }
    long sum =
        req.getPlans().stream().mapToLong(p -> p.getAmount() == null ? 0L : p.getAmount()).sum();
    if (sum != orderAmount) {
      throw new BusinessException(ErrorCode.PLAN_AMOUNT_MISMATCH);
    }
    if (paidExisting > newPlans) {
      throw new BusinessException(ErrorCode.PAYMENT_EXISTS);
    }
    // 删除无回款的旧期次，保留已回款期次，补充新期次
    for (PaymentPlan p : existing) {
      if (PaymentService.STATUS_PAID.equals(p.getStatus())) {
        continue;
      }
      boolean hasRecord =
          !paymentService.recordsOf(orderId).isEmpty() && hasRecordFor(p.getId(), orderId);
      if (!hasRecord) {
        planMapper.deleteById(p.getId());
      }
    }
    int seq = 1;
    for (OrderRequest.PlanItemRequest p : req.getPlans()) {
      insertPlan(orderId, seq++, p.getAmount(), p.getDueDate(), p.getDescription());
    }
  }

  private boolean hasRecordFor(Long planId, Long orderId) {
    return paymentService.recordsOf(orderId).stream().anyMatch(r -> planId.equals(r.getPlanId()));
  }

  private void insertPlan(
      Long orderId, int seq, Long amount, LocalDate dueDate, String description) {
    PaymentPlan plan = new PaymentPlan();
    plan.setOrderId(orderId);
    plan.setSeqNo(seq);
    plan.setAmount(amount == null ? 0L : amount);
    plan.setDueDate(dueDate);
    plan.setDescription(description);
    plan.setStatus(PaymentService.STATUS_PENDING);
    planMapper.insert(plan);
  }

  private String nextOrderNo(LocalDate date) {
    String day = date.format(NO_FORMAT);
    String prefix = "SO-" + day + "-";
    long maxSeq = 0;
    List<SalesOrder> existing =
        orderMapper.selectList(
            new LambdaQueryWrapper<SalesOrder>().likeRight(SalesOrder::getOrderNo, prefix));
    for (SalesOrder o : existing) {
      String suffix = o.getOrderNo().substring(prefix.length());
      try {
        long seq = Long.parseLong(suffix);
        if (seq > maxSeq) {
          maxSeq = seq;
        }
      } catch (NumberFormatException ignored) {
        // 忽略异常编号
      }
    }
    return prefix + String.format("%04d", maxSeq + 1);
  }

  private OrderResponse toResponse(SalesOrder order, boolean withDetails) {
    OrderResponse resp = new OrderResponse();
    resp.setId(order.getId());
    resp.setOrderNo(order.getOrderNo());
    resp.setTitle(order.getTitle());
    resp.setCustomerId(order.getCustomerId());
    Customer customer = customerMapper.selectById(order.getCustomerId());
    resp.setCustomerName(customer == null ? null : customer.getName());
    resp.setContractId(order.getContractId());
    resp.setAmount(order.getAmount());
    resp.setStatus(order.getStatus());
    resp.setDescription(order.getDescription());
    resp.setVersion(order.getVersion());
    resp.setCreatedAt(order.getCreatedAt());
    if (withDetails) {
      List<PlanItemResponse> plans =
          paymentService.buildPlans(paymentService.plansOf(order.getId()));
      resp.setPlans(plans);
      resp.setPaidAmount(paymentService.paidAmountOf(order.getId()));
      resp.setPayments(
          paymentService.recordsOf(order.getId()).stream()
              .map(paymentService::toRecordResponse)
              .toList());
    }
    return resp;
  }

  public SalesOrder require(Long id) {
    SalesOrder order = orderMapper.selectById(id);
    if (order == null) {
      throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
    }
    return order;
  }
}
