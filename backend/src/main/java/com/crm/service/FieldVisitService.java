package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.visit.CheckInRequest;
import com.crm.dto.visit.VisitRequest;
import com.crm.dto.visit.VisitResponse;
import com.crm.entity.Customer;
import com.crm.entity.FieldVisit;
import com.crm.entity.FollowUp;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FieldVisitMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 外勤拜访服务（035-field-visit，FR-001~005）：计划 CRUD + 签到（防重复 + 坐标/时间）+ 小结自动转跟进（type=拜访）+ 统计（按销售/月份完成率）。
 */
@Service
public class FieldVisitService {

  public static final String STATUS_PLANNED = "PLANNED";
  public static final String STATUS_DONE = "DONE";
  public static final String STATUS_CANCELED = "CANCELED";

  private final FieldVisitMapper visitMapper;
  private final CustomerMapper customerMapper;
  private final FollowUpMapper followUpMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public FieldVisitService(
      FieldVisitMapper visitMapper,
      CustomerMapper customerMapper,
      FollowUpMapper followUpMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.visitMapper = visitMapper;
    this.customerMapper = customerMapper;
    this.followUpMapper = followUpMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /** 拜访列表（按创建人，可按状态/月份筛选）。 */
  public PageResult<VisitResponse> list(String status, String month, long page, long pageSize) {
    LambdaQueryWrapper<FieldVisit> qw =
        new LambdaQueryWrapper<FieldVisit>()
            .eq(FieldVisit::getCreatedBy, SecurityUtil.currentUserId())
            .orderByDesc(FieldVisit::getVisitTime);
    if (StringUtils.hasText(status)) {
      qw.eq(FieldVisit::getStatus, status.trim());
    }
    if (StringUtils.hasText(month)) {
      YearMonth ym = YearMonth.parse(month);
      qw.between(
          FieldVisit::getVisitTime,
          ym.atDay(1).atStartOfDay(),
          ym.atEndOfMonth().atTime(23, 59, 59));
    }
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<FieldVisit> p =
        visitMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  /** 创建拜访计划。 */
  @Transactional
  public VisitResponse create(VisitRequest req) {
    validate(req);
    FieldVisit visit = new FieldVisit();
    visit.setCustomerId(req.getCustomerId());
    visit.setTheme(req.getTheme().trim());
    visit.setVisitTime(req.getVisitTime());
    visit.setDurationMinutes(req.getDurationMinutes());
    visit.setStatus(STATUS_PLANNED);
    visit.setLateFlag(false);
    visit.setCreatedBy(SecurityUtil.currentUserId());
    visitMapper.insert(visit);
    auditService.record("CREATE", "FIELD_VISIT", visit.getId(), "创建拜访：" + visit.getTheme());
    return toResponse(visit);
  }

  /** 编辑计划（仅 PLANNED）。 */
  @Transactional
  public VisitResponse update(Long id, VisitRequest req) {
    FieldVisit visit = require(id);
    checkOwner(visit);
    if (!STATUS_PLANNED.equals(visit.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "仅计划中的拜访可编辑");
    }
    if (req.getTheme() != null) {
      visit.setTheme(req.getTheme().trim());
    }
    if (req.getVisitTime() != null) {
      visit.setVisitTime(req.getVisitTime());
    }
    if (req.getDurationMinutes() != null) {
      visit.setDurationMinutes(req.getDurationMinutes());
    }
    visitMapper.updateById(visit);
    return toResponse(visit);
  }

  /** 取消计划。 */
  @Transactional
  public void cancel(Long id) {
    FieldVisit visit = require(id);
    checkOwner(visit);
    if (!STATUS_PLANNED.equals(visit.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "仅计划中的拜访可取消");
    }
    visitMapper.update(
        null,
        new LambdaUpdateWrapper<FieldVisit>()
            .eq(FieldVisit::getId, id)
            .eq(FieldVisit::getStatus, STATUS_PLANNED)
            .set(FieldVisit::getStatus, STATUS_CANCELED));
    auditService.record("CANCEL", "FIELD_VISIT", id, "取消拜访：" + visit.getTheme());
  }

  /** 签到：防重复（仅 PLANNED）+ 坐标/时间 + 小结转跟进。 */
  @Transactional
  public VisitResponse checkIn(Long id, CheckInRequest req) {
    FieldVisit visit = require(id);
    checkOwner(visit);
    if (!STATUS_PLANNED.equals(visit.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "该拜访已签到或已取消，不能重复签到");
    }
    LocalDateTime now = LocalDateTime.now();
    visit.setStatus(STATUS_DONE);
    visit.setLatitude(req.getLatitude() == null ? null : BigDecimal.valueOf(req.getLatitude()));
    visit.setLongitude(req.getLongitude() == null ? null : BigDecimal.valueOf(req.getLongitude()));
    visit.setLocationText(req.getLocationText());
    visit.setCheckInTime(now);
    visit.setLateFlag(visit.getVisitTime() != null && now.isAfter(visit.getVisitTime()));
    if (StringUtils.hasText(req.getSummary())) {
      visit.setSummary(req.getSummary().trim());
    }
    visitMapper.updateById(visit);

    // 小结自动转跟进（method=拜访）
    if (StringUtils.hasText(visit.getSummary())) {
      FollowUp followUp = new FollowUp();
      followUp.setCustomerId(visit.getCustomerId());
      followUp.setMethod("拜访");
      followUp.setContent("【拜访】" + visit.getTheme() + "：" + visit.getSummary());
      followUp.setFollowUpBy(SecurityUtil.currentUserId());
      followUp.setCreatedAt(LocalDateTime.now());
      followUpMapper.insert(followUp);
    }
    auditService.record(
        "CHECK_IN", "FIELD_VISIT", id, "拜访签到：" + visit.getTheme() + " @" + visit.getLocationText());
    return toResponse(visitMapper.selectById(id));
  }

  /** 拜访统计：按销售/月份计划数/完成数/完成率。 */
  public Map<String, Object> stats(String month) {
    YearMonth ym = StringUtils.hasText(month) ? YearMonth.parse(month) : YearMonth.now();
    LocalDateTime from = ym.atDay(1).atStartOfDay();
    LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);
    List<FieldVisit> visits =
        visitMapper.selectList(
            new LambdaQueryWrapper<FieldVisit>().between(FieldVisit::getVisitTime, from, to));
    Map<Long, long[]> byUser = new LinkedHashMap<>(); // userId -> [planned, done, canceled]
    for (FieldVisit v : visits) {
      long[] stat = byUser.computeIfAbsent(v.getCreatedBy(), k -> new long[3]);
      stat[0]++;
      if (STATUS_DONE.equals(v.getStatus())) {
        stat[1]++;
      } else if (STATUS_CANCELED.equals(v.getStatus())) {
        stat[2]++;
      }
    }
    List<Map<String, Object>> items = new ArrayList<>();
    long totalPlanned = 0;
    long totalDone = 0;
    for (Map.Entry<Long, long[]> e : byUser.entrySet()) {
      long[] stat = e.getValue();
      totalPlanned += stat[0];
      totalDone += stat[1];
      User u = userMapper.selectById(e.getKey());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("userId", e.getKey());
      item.put(
          "userName",
          u == null
              ? ("用户#" + e.getKey())
              : (u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
      item.put("planned", stat[0]);
      item.put("done", stat[1]);
      item.put("canceled", stat[2]);
      item.put("completionRate", stat[0] == 0 ? 0 : Math.round(stat[1] * 100.0 / stat[0]) / 100.0);
      items.add(item);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("items", items);
    result.put("totalPlanned", totalPlanned);
    result.put("totalDone", totalDone);
    result.put("month", ym.toString());
    return result;
  }

  private void validate(VisitRequest req) {
    if (req.getCustomerId() == null
        || !StringUtils.hasText(req.getTheme())
        || req.getVisitTime() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "客户/主题/拜访时间不能为空");
    }
    Customer c = customerMapper.selectById(req.getCustomerId());
    if (c == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
  }

  private FieldVisit require(Long id) {
    FieldVisit visit = visitMapper.selectById(id);
    if (visit == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "拜访不存在");
    }
    return visit;
  }

  private void checkOwner(FieldVisit visit) {
    if (!SecurityUtil.currentUserId().equals(visit.getCreatedBy())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }

  private VisitResponse toResponse(FieldVisit v) {
    VisitResponse r = new VisitResponse();
    r.setId(v.getId());
    r.setCustomerId(v.getCustomerId());
    Customer c = customerMapper.selectById(v.getCustomerId());
    r.setCustomerName(c == null ? "-" : c.getName());
    r.setTheme(v.getTheme());
    r.setVisitTime(v.getVisitTime());
    r.setDurationMinutes(v.getDurationMinutes());
    r.setStatus(v.getStatus());
    r.setLatitude(v.getLatitude() == null ? null : v.getLatitude().doubleValue());
    r.setLongitude(v.getLongitude() == null ? null : v.getLongitude().doubleValue());
    r.setLocationText(v.getLocationText());
    r.setCheckInTime(v.getCheckInTime());
    r.setSummary(v.getSummary());
    r.setLateFlag(v.getLateFlag());
    return r;
  }
}
