package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.quote.QuoteItemResponse;
import com.crm.dto.quote.QuoteRequest;
import com.crm.dto.quote.QuoteResponse;
import com.crm.entity.Customer;
import com.crm.entity.Opportunity;
import com.crm.entity.Product;
import com.crm.entity.Quote;
import com.crm.entity.QuoteItem;
import com.crm.repository.CustomerMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.ProductMapper;
import com.crm.repository.QuoteItemMapper;
import com.crm.repository.QuoteMapper;
import com.crm.security.SecurityUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 报价单服务（007-product-cpq，FR-P05~P10）：行快照算额/状态机/审批。 */
@Service
public class QuoteService {

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_PENDING = "PENDING_APPROVAL";
  public static final String STATUS_APPROVED = "APPROVED";
  public static final String STATUS_SIGNED = "SIGNED";
  public static final String STATUS_REJECTED = "REJECTED";

  private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final QuoteMapper quoteMapper;
  private final QuoteItemMapper quoteItemMapper;
  private final CustomerMapper customerMapper;
  private final OpportunityMapper opportunityMapper;
  private final ProductMapper productMapper;
  private final AuditService auditService;

  public QuoteService(
      QuoteMapper quoteMapper,
      QuoteItemMapper quoteItemMapper,
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper,
      ProductMapper productMapper,
      AuditService auditService) {
    this.quoteMapper = quoteMapper;
    this.quoteItemMapper = quoteItemMapper;
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
    this.productMapper = productMapper;
    this.auditService = auditService;
  }

  public PageResult<QuoteResponse> page(
      String keyword, String status, Long customerId, long page, long pageSize) {
    LambdaQueryWrapper<Quote> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.like(Quote::getQuoteNo, keyword.trim());
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Quote::getStatus, status.trim());
    }
    if (customerId != null) {
      qw.eq(Quote::getCustomerId, customerId);
    }
    qw.orderByDesc(Quote::getId);
    Page<Quote> p = quoteMapper.selectPage(new Page<>(page, pageSize), qw);
    List<QuoteResponse> items = p.getRecords().stream().map(q -> toResponse(q, false)).toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  public QuoteResponse detail(Long id) {
    return toResponse(require(id), true);
  }

  @Transactional
  public QuoteResponse create(QuoteRequest req) {
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    validateOpportunity(req.getCustomerId(), req.getOpportunityId());

    Quote quote = new Quote();
    quote.setQuoteNo(nextQuoteNo(LocalDate.now()));
    quote.setCustomerId(req.getCustomerId());
    quote.setOpportunityId(req.getOpportunityId());
    quote.setValidUntil(req.getValidUntil());
    quote.setStatus(STATUS_DRAFT);
    quote.setRemark(req.getRemark());
    quote.setCreatedBy(SecurityUtil.currentUserId());
    // 产品**只查一次**，算额与建行共用（改造前各查一遍：4 行明细 = 8 次查询）
    Map<Long, Product> products = resolveProducts(req);
    long total = calcTotal(req, products);
    quote.setTotalAmount(total);
    quoteMapper.insert(quote);

    List<QuoteItem> items = buildItems(req, quote.getId(), products);
    for (QuoteItem item : items) {
      quoteItemMapper.insert(item);
    }
    auditService.record("CREATE", "QUOTE", quote.getId(), "创建报价单：" + quote.getQuoteNo());
    return toResponse(quote, true);
  }

  @Transactional
  public QuoteResponse update(Long id, QuoteRequest req) {
    Quote existing = require(id);
    if (!STATUS_DRAFT.equals(existing.getStatus())
        && !STATUS_REJECTED.equals(existing.getStatus())) {
      throw new BusinessException(ErrorCode.QUOTE_INVALID_STATE);
    }
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    validateOpportunity(req.getCustomerId(), req.getOpportunityId());

    existing.setCustomerId(req.getCustomerId());
    existing.setOpportunityId(req.getOpportunityId());
    existing.setValidUntil(req.getValidUntil());
    existing.setRemark(req.getRemark());
    Map<Long, Product> products = resolveProducts(req);
    long total = calcTotal(req, products);
    existing.setTotalAmount(total);
    existing.setVersion(req.getVersion());
    int rows = quoteMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    // 删除旧行，重建新行（简化一致性）
    quoteItemMapper.delete(new LambdaQueryWrapper<QuoteItem>().eq(QuoteItem::getQuoteId, id));
    for (QuoteItem item : buildItems(req, id, products)) {
      quoteItemMapper.insert(item);
    }
    auditService.record("UPDATE", "QUOTE", id, "编辑报价单：" + existing.getQuoteNo());
    return toResponse(quoteMapper.selectById(id), true);
  }

  @Transactional
  public QuoteResponse submit(Long id) {
    Quote quote = require(id);
    if (!STATUS_DRAFT.equals(quote.getStatus()) && !STATUS_REJECTED.equals(quote.getStatus())) {
      throw new BusinessException(ErrorCode.QUOTE_INVALID_STATE);
    }
    // updateById 忽略 null 字段，须用 UpdateWrapper 显式清空拒绝意见
    quoteMapper.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Quote>()
            .eq(Quote::getId, id)
            .set(Quote::getStatus, STATUS_PENDING)
            .set(Quote::getRejectReason, null));
    auditService.record("SUBMIT", "QUOTE", id, "提交审批：" + quote.getQuoteNo());
    return toResponse(quoteMapper.selectById(id), true);
  }

  @Transactional
  public QuoteResponse approve(Long id) {
    Quote quote = require(id);
    if (!STATUS_PENDING.equals(quote.getStatus())) {
      throw new BusinessException(ErrorCode.QUOTE_INVALID_STATE);
    }
    quote.setStatus(STATUS_APPROVED);
    quote.setApproverId(SecurityUtil.currentUserId());
    quote.setApprovedAt(java.time.LocalDateTime.now());
    quoteMapper.updateById(quote);
    auditService.record("APPROVE", "QUOTE", id, "审批通过：" + quote.getQuoteNo());
    return toResponse(quoteMapper.selectById(id), true);
  }

  @Transactional
  public QuoteResponse reject(Long id, String reason) {
    if (!StringUtils.hasText(reason)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    Quote quote = require(id);
    if (!STATUS_PENDING.equals(quote.getStatus())) {
      throw new BusinessException(ErrorCode.QUOTE_INVALID_STATE);
    }
    quote.setStatus(STATUS_REJECTED);
    quote.setRejectReason(reason.trim());
    quoteMapper.updateById(quote);
    auditService.record(
        "REJECT", "QUOTE", id, "拒绝报价：" + quote.getQuoteNo() + "（" + reason.trim() + "）");
    return toResponse(quoteMapper.selectById(id), true);
  }

  public Quote require(Long id) {
    Quote quote = quoteMapper.selectById(id);
    if (quote == null) {
      throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND);
    }
    return quote;
  }

  private void validateOpportunity(Long customerId, Long opportunityId) {
    if (opportunityId == null) {
      return;
    }
    Opportunity opp = opportunityMapper.selectById(opportunityId);
    if (opp == null) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_NOT_FOUND);
    }
    if (!customerId.equals(opp.getCustomerId())) {
      throw new BusinessException(ErrorCode.OPPORTUNITY_CUSTOMER_MISMATCH);
    }
  }

  /**
   * 一次性取齐本次请求涉及的全部产品（FR-G24）。
   *
   * <p><b>为什么必须由调用方先取、再传给 {@link #calcTotal} 与 {@link #buildItems}</b>：这两个方法本来各自对同一批 productId
   * 查一遍库，N 行明细即 2N 次往返（实测 4 行 → 8 次查询）。把"取产品"提到循环之外是本修复的**全部**内容； 两个方法各自的算法未变。
   *
   * <p><b>产品存在性/启用状态的校验也放在这里</b>：原先该校验只存在于 {@code calcTotal} 里，而 {@code buildItems} 直接解引用 {@code
   * product.getName()}——即它**依赖调用顺序**才不出空指针。校验前置后，两个方法各自都是安全的， 不再需要"calcTotal 一定先跑"这一隐含约定。
   *
   * <p>失败语义保持不变：任一产品不存在或非 ACTIVE 即抛 {@code PRODUCT_NOT_FOUND}（原为遇到第一个就抛，
   * 改批量后仍在遍历时按同样顺序抛出——遍历的是请求里的行，不是查询结果，故哪一行报错与改造前一致）。
   */
  private Map<Long, Product> resolveProducts(QuoteRequest req) {
    Set<Long> productIds = new LinkedHashSet<>();
    for (QuoteRequest.QuoteItemRequest item : req.getItems()) {
      productIds.add(item.getProductId());
    }
    Map<Long, Product> byId = new HashMap<>();
    if (productIds.isEmpty()) {
      return byId;
    }
    for (Product product : productMapper.selectBatchIds(productIds)) {
      byId.put(product.getId(), product);
    }
    return byId;
  }

  /** 取该行对应的、已校验为可用的产品；产品缺失或非启用状态即拒绝。 */
  private Product requireActiveProduct(Map<Long, Product> products, Long productId) {
    Product product = products.get(productId);
    if (product == null || !"ACTIVE".equals(product.getStatus())) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
    }
    return product;
  }

  /** 行小计：单价 × 数量 × 折扣，分位四舍五入到分。 */
  private long lineTotal(long unitPrice, Integer quantity, BigDecimal discount) {
    return BigDecimal.valueOf(unitPrice)
        .multiply(BigDecimal.valueOf(quantity))
        .multiply(discount == null ? BigDecimal.ONE : discount)
        .setScale(0, RoundingMode.HALF_UP)
        .longValue();
  }

  /** 计算总额（行小计仍由 {@link #buildItems} 落库，此处只求和不回填）。 */
  private long calcTotal(QuoteRequest req, Map<Long, Product> products) {
    long total = 0;
    for (QuoteRequest.QuoteItemRequest item : req.getItems()) {
      Product product = requireActiveProduct(products, item.getProductId());
      long unitPrice = product.getStandardPrice() == null ? 0L : product.getStandardPrice();
      total += lineTotal(unitPrice, item.getQuantity(), item.getDiscount());
    }
    return total;
  }

  private List<QuoteItem> buildItems(QuoteRequest req, Long quoteId, Map<Long, Product> products) {
    return req.getItems().stream()
        .map(
            item -> {
              Product product = requireActiveProduct(products, item.getProductId());
              QuoteItem qi = new QuoteItem();
              qi.setQuoteId(quoteId);
              qi.setProductId(item.getProductId());
              qi.setProductName(product.getName());
              long unitPrice = product.getStandardPrice() == null ? 0L : product.getStandardPrice();
              qi.setUnitPrice(unitPrice);
              qi.setQuantity(item.getQuantity());
              qi.setDiscount(item.getDiscount() == null ? BigDecimal.ONE : item.getDiscount());
              // 与 calcTotal 共用同一个 lineTotal：两处若各写一份，一次只改一处的舍入修正会让
              // 落库的 line_total 与 total_amount 静默不一致
              qi.setLineTotal(lineTotal(unitPrice, item.getQuantity(), item.getDiscount()));
              return qi;
            })
        .toList();
  }

  /** 生成报价单号：Q-yyyyMMdd-当日4位序号（含已删除占用防重，冲突则继续递增）。 */
  private String nextQuoteNo(LocalDate date) {
    String day = date.format(NO_FORMAT);
    String prefix = "Q-" + day + "-";
    long maxSeq = 0;
    List<Quote> existing =
        quoteMapper.selectList(
            new LambdaQueryWrapper<Quote>().likeRight(Quote::getQuoteNo, prefix));
    for (Quote q : existing) {
      String suffix = q.getQuoteNo().substring(prefix.length());
      try {
        long seq = Long.parseLong(suffix);
        if (seq > maxSeq) {
          maxSeq = seq;
        }
      } catch (NumberFormatException ignored) {
        // 忽略异常单号
      }
    }
    return prefix + String.format("%04d", maxSeq + 1);
  }

  /** 单条/列表装配（详情含行明细；列表不含以控制体积）。 */
  private QuoteResponse toResponse(Quote quote, boolean withItems) {
    QuoteResponse resp = new QuoteResponse();
    resp.setId(quote.getId());
    resp.setQuoteNo(quote.getQuoteNo());
    resp.setCustomerId(quote.getCustomerId());
    Customer customer = customerMapper.selectById(quote.getCustomerId());
    resp.setCustomerName(customer == null ? null : customer.getName());
    resp.setOpportunityId(quote.getOpportunityId());
    resp.setValidUntil(quote.getValidUntil());
    resp.setStatus(quote.getStatus());
    resp.setTotalAmount(quote.getTotalAmount());
    resp.setRemark(quote.getRemark());
    resp.setApproverId(quote.getApproverId());
    resp.setApprovedAt(quote.getApprovedAt());
    resp.setRejectReason(quote.getRejectReason());
    resp.setVersion(quote.getVersion());
    resp.setCreatedAt(quote.getCreatedAt());
    if (withItems) {
      List<QuoteItem> items =
          quoteItemMapper.selectList(
              new LambdaQueryWrapper<QuoteItem>()
                  .eq(QuoteItem::getQuoteId, quote.getId())
                  .orderByAsc(QuoteItem::getId));
      resp.setItems(items.stream().map(this::toItemResponse).toList());
    }
    return resp;
  }

  private QuoteItemResponse toItemResponse(QuoteItem item) {
    QuoteItemResponse resp = new QuoteItemResponse();
    resp.setId(item.getId());
    resp.setProductId(item.getProductId());
    resp.setProductName(item.getProductName());
    resp.setUnitPrice(item.getUnitPrice());
    resp.setQuantity(item.getQuantity());
    resp.setDiscount(item.getDiscount());
    resp.setLineTotal(item.getLineTotal());
    return resp;
  }
}
