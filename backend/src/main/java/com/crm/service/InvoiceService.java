package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.invoice.InvoiceRequest;
import com.crm.dto.invoice.InvoiceResponse;
import com.crm.dto.invoice.InvoiceStatsResponse;
import com.crm.entity.Customer;
import com.crm.entity.Invoice;
import com.crm.entity.SalesOrder;
import com.crm.repository.CustomerMapper;
import com.crm.repository.InvoiceMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 发票服务（038-invoice，FR-001~006）：开票（编号 + 可开额校验）+ 作废（原因 + 审计）+ 列表筛选 + 开票率统计。 */
@Service
public class InvoiceService {

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_ISSUED = "ISSUED";
  public static final String STATUS_VOID = "VOID";

  private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

  private final InvoiceMapper invoiceMapper;
  private final SalesOrderMapper orderMapper;
  private final CustomerMapper customerMapper;
  private final AuditService auditService;

  public InvoiceService(
      InvoiceMapper invoiceMapper,
      SalesOrderMapper orderMapper,
      CustomerMapper customerMapper,
      AuditService auditService) {
    this.invoiceMapper = invoiceMapper;
    this.orderMapper = orderMapper;
    this.customerMapper = customerMapper;
    this.auditService = auditService;
  }

  /** 发票列表（按订单/状态/类型筛选）。 */
  public PageResult<InvoiceResponse> list(
      Long orderId, String status, String invoiceType, long page, long pageSize) {
    LambdaQueryWrapper<Invoice> qw = new LambdaQueryWrapper<Invoice>().orderByDesc(Invoice::getId);
    if (orderId != null) {
      qw.eq(Invoice::getOrderId, orderId);
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Invoice::getStatus, status.trim());
    }
    if (StringUtils.hasText(invoiceType)) {
      qw.eq(Invoice::getInvoiceType, invoiceType.trim());
    }
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Invoice> p =
        invoiceMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  /** 开票：校验订单 + 剩余可开额 → 编号 → ISSUED。 */
  @Transactional
  public InvoiceResponse create(InvoiceRequest req) {
    if (req.getOrderId() == null
        || !StringUtils.hasText(req.getTitle())
        || req.getAmount() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "订单/抬头/金额不能为空");
    }
    SalesOrder order = orderMapper.selectById(req.getOrderId());
    if (order == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "订单不存在");
    }
    if (req.getAmount() <= 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "开票金额必须大于 0");
    }
    long invoiced = sumIssued(req.getOrderId());
    long remaining = order.getAmount() == null ? 0 : order.getAmount() - invoiced;
    if (req.getAmount() > remaining) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "开票金额超过订单剩余可开额（剩余 " + remaining + " 分）");
    }
    Invoice invoice = new Invoice();
    invoice.setOrderId(req.getOrderId());
    invoice.setInvoiceNo(nextInvoiceNo());
    invoice.setTitle(req.getTitle().trim());
    invoice.setTaxNo(req.getTaxNo());
    invoice.setAmount(req.getAmount());
    invoice.setInvoiceType(
        StringUtils.hasText(req.getInvoiceType()) ? req.getInvoiceType() : "GENERAL");
    invoice.setStatus(STATUS_ISSUED);
    invoice.setIssuedAt(LocalDateTime.now());
    invoice.setCreatedBy(SecurityUtil.currentUserId());
    invoiceMapper.insert(invoice);
    auditService.record(
        "ISSUE",
        "INVOICE",
        invoice.getId(),
        "开票："
            + invoice.getInvoiceNo()
            + " 金额 "
            + invoice.getAmount()
            + " 分（订单 "
            + order.getOrderNo()
            + "）");
    return toResponse(invoiceMapper.selectById(invoice.getId()));
  }

  /** 作废：原因必填 + 释放可开额。 */
  @Transactional
  public InvoiceResponse voidInvoice(Long id, String reason) {
    Invoice invoice = require(id);
    if (!STATUS_ISSUED.equals(invoice.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已开票状态可作废");
    }
    if (!StringUtils.hasText(reason)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "作废原因不能为空");
    }
    invoice.setStatus(STATUS_VOID);
    invoice.setVoidReason(reason.trim());
    invoice.setVoidedAt(LocalDateTime.now());
    invoiceMapper.updateById(invoice);
    auditService.record("VOID", "INVOICE", id, "作废发票：" + invoice.getInvoiceNo() + " 原因：" + reason);
    return toResponse(invoice);
  }

  /** 开票统计（按订单开票率）。 */
  public InvoiceStatsResponse stats() {
    List<SalesOrder> orders = orderMapper.selectList(null);
    List<Invoice> invoices =
        invoiceMapper.selectList(
            new LambdaQueryWrapper<Invoice>().ne(Invoice::getStatus, STATUS_VOID));
    Map<Long, Long> invoicedByOrder =
        invoices.stream()
            .collect(
                Collectors.groupingBy(
                    Invoice::getOrderId, Collectors.summingLong(Invoice::getAmount)));
    long totalInvoice = invoices.stream().mapToLong(Invoice::getAmount).sum();
    long totalOrder =
        orders.stream().filter(o -> o.getAmount() != null).mapToLong(SalesOrder::getAmount).sum();

    InvoiceStatsResponse resp = new InvoiceStatsResponse();
    resp.setTotalInvoiceAmount(totalInvoice);
    resp.setTotalOrderAmount(totalOrder);
    resp.setInvoiceRate(
        totalOrder == 0 ? 0 : Math.round(totalInvoice * 10000.0 / totalOrder) / 100.0);

    List<InvoiceStatsResponse.ByOrder> byOrder = new ArrayList<>();
    for (SalesOrder o : orders) {
      if (o.getAmount() == null || o.getAmount() == 0) {
        continue;
      }
      long invoiced = invoicedByOrder.getOrDefault(o.getId(), 0L);
      InvoiceStatsResponse.ByOrder item = new InvoiceStatsResponse.ByOrder();
      item.setOrderId(o.getId());
      item.setOrderNo(o.getOrderNo());
      item.setInvoiced(invoiced);
      item.setOrderAmount(o.getAmount());
      item.setRate(Math.round(invoiced * 10000.0 / o.getAmount()) / 100.0);
      byOrder.add(item);
    }
    resp.setByOrder(byOrder);
    return resp;
  }

  private long sumIssued(Long orderId) {
    List<Invoice> issued =
        invoiceMapper.selectList(
            new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getOrderId, orderId)
                .ne(Invoice::getStatus, STATUS_VOID));
    return issued.stream().mapToLong(i -> i.getAmount() == null ? 0 : i.getAmount()).sum();
  }

  /** 编号：INV-{yyyyMM}-{seq}（当日序号）。 */
  private String nextInvoiceNo() {
    String prefix = "INV-" + LocalDate.now().format(NO_FORMAT) + "-";
    List<Invoice> today =
        invoiceMapper.selectList(
            new LambdaQueryWrapper<Invoice>().likeRight(Invoice::getInvoiceNo, prefix));
    int maxSeq =
        today.stream()
            .mapToInt(
                i -> {
                  try {
                    return Integer.parseInt(i.getInvoiceNo().substring(prefix.length()));
                  } catch (Exception ex) {
                    return 0;
                  }
                })
            .max()
            .orElse(0);
    return prefix + String.format("%03d", maxSeq + 1);
  }

  private Invoice require(Long id) {
    Invoice invoice = invoiceMapper.selectById(id);
    if (invoice == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "发票不存在");
    }
    return invoice;
  }

  private InvoiceResponse toResponse(Invoice i) {
    InvoiceResponse r = new InvoiceResponse();
    r.setId(i.getId());
    r.setOrderId(i.getOrderId());
    SalesOrder o = orderMapper.selectById(i.getOrderId());
    r.setOrderNo(o == null ? null : o.getOrderNo());
    if (o != null) {
      Customer c = customerMapper.selectById(o.getCustomerId());
      r.setCustomerName(c == null ? null : c.getName());
    }
    r.setInvoiceNo(i.getInvoiceNo());
    r.setTitle(i.getTitle());
    r.setTaxNo(i.getTaxNo());
    r.setAmount(i.getAmount());
    r.setInvoiceType(i.getInvoiceType());
    r.setStatus(i.getStatus());
    r.setVoidReason(i.getVoidReason());
    r.setIssuedAt(i.getIssuedAt());
    r.setVoidedAt(i.getVoidedAt());
    return r;
  }
}
