package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.report.ReportQuery;
import com.crm.dto.report.ReportResult;
import com.crm.dto.report.ReportRow;
import com.crm.entity.Lead;
import com.crm.entity.QuoteItem;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.User;
import com.crm.repository.LeadMapper;
import com.crm.repository.QuoteItemMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.UserMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 自定义报表服务（021-custom-reports，FR-001~004/006/007）：按维度/指标/时间范围聚合。 内存分组实现（万级数据）；SALES 用户仅本人数据。 */
@Service
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

  private final SalesOpportunityMapper soMapper;
  private final QuoteItemMapper quoteItemMapper;
  private final LeadMapper leadMapper;
  private final UserMapper userMapper;

  public ReportService(
      SalesOpportunityMapper soMapper,
      QuoteItemMapper quoteItemMapper,
      LeadMapper leadMapper,
      UserMapper userMapper) {
    this.soMapper = soMapper;
    this.quoteItemMapper = quoteItemMapper;
    this.leadMapper = leadMapper;
    this.userMapper = userMapper;
  }

  public ReportResult query(ReportQuery q) {
    LocalDateTime start = parseDate(q.getStartDate());
    LocalDateTime end = parseDate(q.getEndDate());
    if (start != null && end != null && start.isAfter(end)) {
      throw new BusinessException(ErrorCode.EXPORT_TYPE_INVALID, "时间范围不合法：结束早于开始");
    }
    switch (q.getDimension()) {
      case ReportQuery.DIMENSION_SALES:
        return salesReport(q, start, end);
      case ReportQuery.DIMENSION_PRODUCT:
        return productReport(q, start, end);
      case ReportQuery.DIMENSION_SOURCE:
        return sourceReport(q, start, end);
      case ReportQuery.DIMENSION_STAGE:
        return stageReport(q, start, end);
      case ReportQuery.DIMENSION_TIME:
        return timeReport(q, start, end);
      default:
        throw new BusinessException(ErrorCode.EXPORT_TYPE_INVALID, "不支持的报表维度：" + q.getDimension());
    }
  }

  /** 按销售（sales_opportunity.created_by）聚合。 */
  private ReportResult salesReport(ReportQuery q, LocalDateTime start, LocalDateTime end) {
    List<SalesOpportunity> list =
        soMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .ge(start != null, SalesOpportunity::getCreatedAt, start)
                .lt(end != null, SalesOpportunity::getCreatedAt, end.plusDays(1))
                .eq(
                    q.getStageFilter() != null && !q.getStageFilter().isBlank(),
                    SalesOpportunity::getStage,
                    q.getStageFilter()));
    // 数据权限：SALES 仅本人
    Long currentUserId = com.crm.security.SecurityUtil.currentUserId();
    String role = com.crm.security.SecurityUtil.currentPrincipal().role();
    if (!"ADMIN".equals(role)) {
      list = list.stream().filter(s -> Objects.equals(s.getCreatedBy(), currentUserId)).toList();
    }
    Map<Long, long[]> acc = new LinkedHashMap<>();
    for (SalesOpportunity s : list) {
      if (s.getCreatedBy() == null) {
        continue;
      }
      long[] cur = acc.computeIfAbsent(s.getCreatedBy(), k -> new long[2]);
      cur[0] += 1;
      cur[1] += s.getAmount() == null ? 0L : s.getAmount();
    }
    Map<Long, User> users =
        acc.keySet().isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(new ArrayList<>(acc.keySet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    List<ReportRow> rows = new ArrayList<>();
    for (Map.Entry<Long, long[]> e : acc.entrySet()) {
      User u = users.get(e.getKey());
      ReportRow r = new ReportRow();
      r.setDimensionValue(u == null ? ("用户#" + e.getKey()) : u.getDisplayName());
      r.setCount(e.getValue()[0]);
      r.setAmount(e.getValue()[1]);
      rows.add(r);
    }
    return finish(rows, q);
  }

  /** 按产品（quote_item）聚合。 */
  private ReportResult productReport(ReportQuery q, LocalDateTime start, LocalDateTime end) {
    List<QuoteItem> list =
        quoteItemMapper.selectList(
            new LambdaQueryWrapper<QuoteItem>()
                .ge(start != null, QuoteItem::getCreatedAt, start)
                .lt(end != null, QuoteItem::getCreatedAt, end.plusDays(1)));
    Map<String, long[]> acc = new LinkedHashMap<>();
    for (QuoteItem i : list) {
      String key = i.getProductName() == null ? ("产品#" + i.getProductId()) : i.getProductName();
      long[] cur = acc.computeIfAbsent(key, k -> new long[2]);
      cur[0] += 1;
      cur[1] += i.getLineTotal() == null ? 0L : i.getLineTotal();
    }
    List<ReportRow> rows = new ArrayList<>();
    for (Map.Entry<String, long[]> e : acc.entrySet()) {
      ReportRow r = new ReportRow();
      r.setDimensionValue(e.getKey());
      r.setCount(e.getValue()[0]);
      r.setAmount(e.getValue()[1]);
      rows.add(r);
    }
    return finish(rows, q);
  }

  /** 按线索来源聚合。 */
  private ReportResult sourceReport(ReportQuery q, LocalDateTime start, LocalDateTime end) {
    List<Lead> list =
        leadMapper.selectList(
            new LambdaQueryWrapper<Lead>()
                .ge(start != null, Lead::getCreatedAt, start)
                .lt(end != null, Lead::getCreatedAt, end.plusDays(1)));
    Long currentUserId = com.crm.security.SecurityUtil.currentUserId();
    String role = com.crm.security.SecurityUtil.currentPrincipal().role();
    if (!"ADMIN".equals(role)) {
      list = list.stream().filter(l -> Objects.equals(l.getOwnerId(), currentUserId)).toList();
    }
    Map<String, long[]> acc = new LinkedHashMap<>();
    for (Lead l : list) {
      String key = l.getSource() == null ? "OTHER" : l.getSource();
      long[] cur = acc.computeIfAbsent(key, k -> new long[2]);
      cur[0] += 1;
      cur[1] += 0; // 线索无金额
    }
    List<ReportRow> rows = new ArrayList<>();
    for (Map.Entry<String, long[]> e : acc.entrySet()) {
      ReportRow r = new ReportRow();
      r.setDimensionValue(e.getKey());
      r.setCount(e.getValue()[0]);
      r.setAmount(0L);
      rows.add(r);
    }
    return finish(rows, q);
  }

  /** 按商机阶段聚合。 */
  private ReportResult stageReport(ReportQuery q, LocalDateTime start, LocalDateTime end) {
    List<SalesOpportunity> list =
        soMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .ge(start != null, SalesOpportunity::getCreatedAt, start)
                .lt(end != null, SalesOpportunity::getCreatedAt, end.plusDays(1)));
    Map<String, long[]> acc = new LinkedHashMap<>();
    for (SalesOpportunity s : list) {
      String key = s.getStage() == null ? "UNKNOWN" : s.getStage();
      long[] cur = acc.computeIfAbsent(key, k -> new long[2]);
      cur[0] += 1;
      cur[1] += s.getAmount() == null ? 0L : s.getAmount();
    }
    List<ReportRow> rows = new ArrayList<>();
    for (Map.Entry<String, long[]> e : acc.entrySet()) {
      ReportRow r = new ReportRow();
      r.setDimensionValue(e.getKey());
      r.setCount(e.getValue()[0]);
      r.setAmount(e.getValue()[1]);
      rows.add(r);
    }
    return finish(rows, q);
  }

  /** 按时间（日/月）聚合：维度值=日期，指标=数量/金额（销售额机会）。 */
  private ReportResult timeReport(ReportQuery q, LocalDateTime start, LocalDateTime end) {
    boolean byMonth = "MONTH".equalsIgnoreCase(q.getGranularity());
    DateTimeFormatter fmt = byMonth ? MONTH_FMT : DAY_FMT;
    List<SalesOpportunity> list =
        soMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .ge(start != null, SalesOpportunity::getCreatedAt, start)
                .lt(end != null, SalesOpportunity::getCreatedAt, end.plusDays(1))
                .eq(
                    q.getStageFilter() != null && !q.getStageFilter().isBlank(),
                    SalesOpportunity::getStage,
                    q.getStageFilter()));
    Map<String, long[]> acc = new LinkedHashMap<>();
    for (SalesOpportunity s : list) {
      if (s.getCreatedAt() == null) {
        continue;
      }
      String key = s.getCreatedAt().format(fmt);
      long[] cur = acc.computeIfAbsent(key, k -> new long[2]);
      cur[0] += 1;
      cur[1] += s.getAmount() == null ? 0L : s.getAmount();
    }
    List<ReportRow> rows = new ArrayList<>();
    for (Map.Entry<String, long[]> e : acc.entrySet()) {
      ReportRow r = new ReportRow();
      r.setDimensionValue(e.getKey());
      r.setCount(e.getValue()[0]);
      r.setAmount(e.getValue()[1]);
      rows.add(r);
    }
    return finish(rows, q);
  }

  /** 统一收尾：按指标降序、计算占比与合计。 */
  private ReportResult finish(List<ReportRow> rows, ReportQuery q) {
    long totalCount = rows.stream().mapToLong(ReportRow::getCount).sum();
    long totalAmount = rows.stream().mapToLong(ReportRow::getAmount).sum();
    for (ReportRow r : rows) {
      r.setRatio(totalAmount == 0 ? 0d : (double) r.getAmount() / totalAmount);
    }
    if (ReportQuery.METRIC_AMOUNT.equalsIgnoreCase(q.getMetric())) {
      rows.sort(
          Comparator.comparingLong(ReportRow::getAmount)
              .reversed()
              .thenComparing(Comparator.comparingLong(ReportRow::getCount).reversed()));
    } else {
      rows.sort(Comparator.comparingLong(ReportRow::getCount).reversed());
    }
    ReportResult result = new ReportResult();
    result.setRows(rows);
    result.setTotalCount(totalCount);
    result.setTotalAmount(totalAmount);
    result.setDimension(q.getDimension());
    result.setMetric(q.getMetric());
    result.setGranularity(q.getGranularity());
    log.debug("Report {} {} generated: {} rows", q.getDimension(), q.getMetric(), rows.size());
    return result;
  }

  private LocalDateTime parseDate(String date) {
    if (date == null || date.isBlank()) {
      return null;
    }
    return LocalDate.parse(date).atStartOfDay();
  }
}
