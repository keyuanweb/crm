package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.tag.SegmentRequest;
import com.crm.dto.tag.SegmentResponse;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.SalesOrder;
import com.crm.entity.Segment;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.repository.SegmentMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 动态细分服务（031-customer-tags，FR-003/004/005）：细分 CRUD + JSON 条件解析 + 成员实时计算（tag / 订单金额 / 最近跟进天数，AND/OR
 * 组合，按数据权限过滤）。
 */
@Service
public class SegmentService {

  private static final Logger log = LoggerFactory.getLogger(SegmentService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> FIELDS = Set.of("tag", "amount", "lastFollowUpDays");
  private static final Set<String> OPS = Set.of("IN", "GT", "GTE", "LT", "LTE");

  private final SegmentMapper segmentMapper;
  private final CustomerMapper customerMapper;
  private final TagService tagService;
  private final SalesOrderMapper orderMapper;
  private final FollowUpMapper followUpMapper;
  private final AuditService auditService;

  public SegmentService(
      SegmentMapper segmentMapper,
      CustomerMapper customerMapper,
      TagService tagService,
      SalesOrderMapper orderMapper,
      FollowUpMapper followUpMapper,
      AuditService auditService) {
    this.segmentMapper = segmentMapper;
    this.customerMapper = customerMapper;
    this.tagService = tagService;
    this.orderMapper = orderMapper;
    this.followUpMapper = followUpMapper;
    this.auditService = auditService;
  }

  /** 细分成员数。 */
  public long countMembers(Long id) {
    return members(require(id)).size();
  }

  /** 细分列表（含成员数）。 */
  public List<SegmentResponse> list() {
    return segmentMapper
        .selectList(new LambdaQueryWrapper<Segment>().orderByAsc(Segment::getId))
        .stream()
        .map(s -> toResponse(s, members(s).size()))
        .toList();
  }

  /** 创建细分。 */
  @Transactional
  public SegmentResponse create(SegmentRequest req) {
    validate(req);
    Long exists =
        segmentMapper.selectCount(
            new LambdaQueryWrapper<Segment>().eq(Segment::getName, req.getName().trim()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "细分名称已存在");
    }
    Segment seg = new Segment();
    seg.setName(req.getName().trim());
    seg.setDescription(req.getDescription());
    seg.setConditions(req.getConditions());
    seg.setCreatedBy(SecurityUtil.currentUserId());
    segmentMapper.insert(seg);
    auditService.record("CREATE", "SEGMENT", seg.getId(), "创建细分：" + seg.getName());
    return toResponse(seg, members(seg).size());
  }

  /** 编辑细分。 */
  @Transactional
  public SegmentResponse update(Long id, SegmentRequest req) {
    Segment seg = require(id);
    validate(req);
    seg.setName(req.getName().trim());
    seg.setDescription(req.getDescription());
    seg.setConditions(req.getConditions());
    segmentMapper.updateById(seg);
    return toResponse(seg, members(seg).size());
  }

  /** 删除细分。 */
  @Transactional
  public void delete(Long id) {
    Segment seg = require(id);
    segmentMapper.deleteById(id);
    auditService.record("DELETE", "SEGMENT", id, "删除细分：" + seg.getName());
  }

  /** 细分成员（分页，按数据权限）。 */
  public PageResult<CustomerResponse> members(Long id, long page, long pageSize) {
    Segment seg = require(id);
    List<Long> ids = members(seg);
    List<CustomerResponse> items = new ArrayList<>();
    int from = (int) Math.min(ids.size(), (page - 1) * pageSize);
    int to = (int) Math.min(ids.size(), page * pageSize);
    if (from < to) {
      items =
          customerMapper.selectBatchIds(ids.subList(from, to)).stream()
              .map(this::toCustomerResponse)
              .toList();
    }
    return PageResult.of(items, ids.size(), page, pageSize);
  }

  /** 成员 id 列表（实时计算，按数据权限）。 */
  public List<Long> members(Segment seg) {
    // 数据权限：可见客户集（ADMIN 全量 / 其他 本人创建）
    List<Long> visibleIds = visibleCustomerIds();
    if (visibleIds.isEmpty()) {
      return List.of();
    }
    Set<Long> visible = new HashSet<>(visibleIds);

    Conditions cond = parse(seg.getConditions());
    if (cond == null || cond.getFilters() == null || cond.getFilters().isEmpty()) {
      return new ArrayList<>(visible);
    }

    List<Long> result = null;
    for (Map<String, Object> filter : cond.getFilters()) {
      Set<Long> matched = matchFilter(filter, visible);
      if ("OR".equalsIgnoreCase(cond.getLogic())) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.addAll(matched);
      } else {
        if (result == null) {
          result = new ArrayList<>(matched);
        } else {
          result.retainAll(matched);
        }
      }
    }
    return result == null ? new ArrayList<>() : result.stream().distinct().toList();
  }

  /** 单条件匹配。 */
  private Set<Long> matchFilter(Map<String, Object> filter, Set<Long> visible) {
    String field = (String) filter.get("field");
    String op = (String) filter.get("op");
    if (field == null || op == null || !FIELDS.contains(field) || !OPS.contains(op)) {
      return new HashSet<>();
    }
    switch (field) {
      case "tag" -> {
        List<String> names = castList(filter.get("values"));
        return new HashSet<>(tagService.customerIdsByTagNames(names));
      }
      case "amount" -> {
        double threshold = num(filter.get("value"));
        return amountMatch(visible, op, threshold);
      }
      case "lastFollowUpDays" -> {
        double threshold = num(filter.get("value"));
        return followUpMatch(visible, op, threshold);
      }
      default -> {
        return new HashSet<>();
      }
    }
  }

  private Set<Long> amountMatch(Set<Long> visible, String op, double threshold) {
    Map<Long, Long> sumByCustomer =
        orderMapper
            .selectList(
                new LambdaQueryWrapper<SalesOrder>()
                    .select(SalesOrder::getCustomerId, SalesOrder::getAmount))
            .stream()
            .filter(o -> o.getAmount() != null)
            .collect(
                Collectors.groupingBy(
                    SalesOrder::getCustomerId, Collectors.summingLong(SalesOrder::getAmount)));
    Set<Long> result = new HashSet<>();
    for (Long id : visible) {
      long sum = sumByCustomer.getOrDefault(id, 0L);
      if (compare(sum, op, threshold)) {
        result.add(id);
      }
    }
    return result;
  }

  private Set<Long> followUpMatch(Set<Long> visible, String op, double threshold) {
    LocalDate today = LocalDate.now();
    List<FollowUp> followUps =
        followUpMapper.selectList(
            new LambdaQueryWrapper<FollowUp>()
                .select(FollowUp::getCustomerId, FollowUp::getCreatedAt)
                .in(FollowUp::getCustomerId, visible)
                .orderByDesc(FollowUp::getCreatedAt));
    Map<Long, Long> lastDays =
        followUps.stream()
            .collect(
                Collectors.toMap(
                    FollowUp::getCustomerId,
                    f -> {
                      long days =
                          f.getCreatedAt() == null
                              ? 0
                              : Math.max(
                                  0,
                                  ChronoUnit.DAYS.between(f.getCreatedAt().toLocalDate(), today));
                      return days;
                    },
                    Math::min));
    Set<Long> result = new HashSet<>();
    for (Long id : visible) {
      long days = lastDays.getOrDefault(id, 999L); // 无跟进视为 999 天
      if (compare(days, op, threshold)) {
        result.add(id);
      }
    }
    return result;
  }

  private boolean compare(long v, String op, double threshold) {
    return switch (op) {
      case "GT" -> v > threshold;
      case "GTE" -> v >= threshold;
      case "LT" -> v < threshold;
      case "LTE" -> v <= threshold;
      default -> false;
    };
  }

  /** 可见客户集（ADMIN 全量 / 其他 本人创建）。 */
  private List<Long> visibleCustomerIds() {
    String role =
        SecurityUtil.currentPrincipal() == null ? "" : SecurityUtil.currentPrincipal().role();
    if ("ADMIN".equals(role)) {
      return customerMapper
          .selectList(new LambdaQueryWrapper<Customer>().select(Customer::getId))
          .stream()
          .map(Customer::getId)
          .toList();
    }
    Long userId = SecurityUtil.currentUserId();
    return customerMapper
        .selectList(
            new LambdaQueryWrapper<Customer>()
                .select(Customer::getId)
                .eq(Customer::getCreatedBy, userId))
        .stream()
        .map(Customer::getId)
        .toList();
  }

  private void validate(SegmentRequest req) {
    if (!StringUtils.hasText(req.getName()) || !StringUtils.hasText(req.getConditions())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "细分名称与条件不能为空");
    }
    parse(req.getConditions()); // 校验 JSON 结构
  }

  private Conditions parse(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<Conditions>() {});
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "细分条件格式不合法");
    }
  }

  private Segment require(Long id) {
    Segment seg = segmentMapper.selectById(id);
    if (seg == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "细分不存在");
    }
    return seg;
  }

  /** 公开：细分实体（供邮件群发等复用）。 */
  public Segment requirePublic(Long id) {
    return require(id);
  }

  @SuppressWarnings("unchecked")
  private List<String> castList(Object v) {
    if (v instanceof List<?> l) {
      return l.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  private double num(Object v) {
    try {
      return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "条件数值不合法");
    }
  }

  private SegmentResponse toResponse(Segment seg, long count) {
    SegmentResponse resp = new SegmentResponse();
    resp.setId(seg.getId());
    resp.setName(seg.getName());
    resp.setDescription(seg.getDescription());
    resp.setConditions(seg.getConditions());
    resp.setMemberCount(count);
    return resp;
  }

  private CustomerResponse toCustomerResponse(Customer c) {
    CustomerResponse r = new CustomerResponse();
    r.setId(c.getId());
    r.setName(c.getName());
    r.setCompany(c.getCompany());
    r.setStatus(c.getStatus());
    return r;
  }

  /** 条件容器（JSON 反序列化）。 */
  public static class Conditions {
    private String logic;
    private List<Map<String, Object>> filters;

    public String getLogic() {
      return logic;
    }

    public void setLogic(String logic) {
      this.logic = logic;
    }

    public List<Map<String, Object>> getFilters() {
      return filters;
    }

    public void setFilters(List<Map<String, Object>> filters) {
      this.filters = filters;
    }
  }
}
