package com.crm.service.quota;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.dto.quota.SalesQuotaAchievementResponse;
import com.crm.dto.quota.SalesQuotaBreakdownRequest;
import com.crm.dto.quota.SalesQuotaRequest;
import com.crm.dto.quota.SalesQuotaResponse;
import com.crm.dto.quota.SalesQuotaSummaryResponse;
import com.crm.entity.Department;
import com.crm.entity.SalesQuota;
import com.crm.entity.SalesQuotaBreakdown;
import com.crm.entity.SalesQuotaVersion;
import com.crm.entity.User;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.UserMapper;
import com.crm.repository.quota.SalesQuotaAchievementRepository;
import com.crm.repository.quota.SalesQuotaBreakdownRepository;
import com.crm.repository.quota.SalesQuotaRepository;
import com.crm.repository.quota.SalesQuotaVersionRepository;
import com.crm.service.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 销售配额 Service 实现（078-sales-quota）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesQuotaServiceImpl implements SalesQuotaService {

  private final SalesQuotaRepository salesQuotaRepository;
  private final SalesQuotaBreakdownRepository salesQuotaBreakdownRepository;
  private final SalesQuotaVersionRepository salesQuotaVersionRepository;
  private final SalesQuotaAchievementRepository salesQuotaAchievementRepository;
  private final AuditService auditService;
  private final DepartmentMapper departmentMapper;
  private final UserMapper userMapper;

  @Override
  @Transactional
  public SalesQuotaResponse createQuota(SalesQuotaRequest request) {
    SalesQuota quota = new SalesQuota();
    BeanUtils.copyProperties(request, quota);
    quota.setStatus("DRAFT");
    salesQuotaRepository.insert(quota);
    log.info(
        "Created sales quota: id={}, year={}, amount={}",
        quota.getId(),
        quota.getYear(),
        quota.getAmount());
    auditService.record(
        "CREATE",
        "SALES_QUOTA",
        quota.getId(),
        "Create quota: year=" + quota.getYear() + ", amount=" + quota.getAmount());
    return toResponse(quota);
  }

  @Override
  public IPage<SalesQuotaResponse> getQuotas(
      int page, int size, Integer year, Long teamId, Long userId, String status) {
    Page<SalesQuota> pageParam = new Page<>(page, size);
    LambdaQueryWrapper<SalesQuota> wrapper = new LambdaQueryWrapper<>();
    if (year != null) wrapper.eq(SalesQuota::getYear, year);
    if (teamId != null) wrapper.eq(SalesQuota::getTeamId, teamId);
    if (userId != null) wrapper.eq(SalesQuota::getUserId, userId);
    if (StringUtils.hasText(status)) wrapper.eq(SalesQuota::getStatus, status);
    wrapper.orderByDesc(SalesQuota::getCreatedAt);
    IPage<SalesQuota> result = salesQuotaRepository.selectPage(pageParam, wrapper);
    IPage<SalesQuotaResponse> responsePage = result.convert(this::toResponse);
    fillAchievement(responsePage.getRecords());
    return responsePage;
  }

  private void fillAchievement(List<SalesQuotaResponse> records) {
    if (records == null || records.isEmpty()) {
      return;
    }
    List<Long> ids = records.stream().map(SalesQuotaResponse::getId).collect(Collectors.toList());
    List<Map<String, Object>> achievements = salesQuotaRepository.getAchievementBatch(ids);
    Map<Long, Map<String, Object>> byId =
        achievements.stream()
            .collect(
                Collectors.toMap(m -> ((Number) m.get("id")).longValue(), m -> m, (a, b) -> a));
    for (SalesQuotaResponse record : records) {
      Map<String, Object> a = byId.get(record.getId());
      if (a != null) {
        record.setActualAmount(toBigDecimal(a.get("actual_amount")));
        record.setAchievementRate(toBigDecimal(a.get("achievement_rate")));
      }
    }
  }

  @Override
  public SalesQuotaResponse getQuota(Long id) {
    SalesQuota quota = salesQuotaRepository.selectById(id);
    if (quota == null) {
      throw new RuntimeException("Quota not found: " + id);
    }
    return toResponse(quota);
  }

  @Override
  @Transactional
  public SalesQuotaResponse updateQuota(Long id, SalesQuotaRequest request) {
    SalesQuota quota = salesQuotaRepository.selectById(id);
    if (quota == null) {
      throw new RuntimeException("Quota not found: " + id);
    }
    if ("CLOSED".equals(quota.getStatus())) {
      throw new RuntimeException("Closed quota cannot be updated");
    }
    BigDecimal oldAmount = quota.getAmount();
    quota.setAmount(request.getAmount());
    salesQuotaRepository.updateById(quota);

    auditService.record(
        "UPDATE",
        "SALES_QUOTA",
        id,
        "Update quota: "
            + oldAmount
            + " -> "
            + request.getAmount()
            + ", reason="
            + request.getChangeReason());

    // Create version record
    SalesQuotaVersion version = new SalesQuotaVersion();
    version.setQuotaId(id);
    version.setOldAmount(oldAmount);
    version.setNewAmount(request.getAmount());
    version.setChangedBy(request.getUserId() != null ? request.getUserId() : 1L);
    version.setChangedAt(LocalDateTime.now());
    version.setChangeReason(request.getChangeReason());
    version.setVersionNumber(
        salesQuotaVersionRepository
                .selectCount(
                    new LambdaQueryWrapper<SalesQuotaVersion>()
                        .eq(SalesQuotaVersion::getQuotaId, id))
                .intValue()
            + 1);
    salesQuotaVersionRepository.insert(version);

    log.info(
        "Updated sales quota: id={}, oldAmount={}, newAmount={}",
        id,
        oldAmount,
        request.getAmount());
    return toResponse(quota);
  }

  @Override
  @Transactional
  public void breakdownQuota(Long quotaId, List<SalesQuotaBreakdownRequest> breakdowns) {
    SalesQuota parent = salesQuotaRepository.selectById(quotaId);
    if (parent == null) {
      throw new RuntimeException("Quota not found: " + quotaId);
    }

    // Calculate total breakdown amount
    BigDecimal totalBreakdown =
        breakdowns.stream()
            .map(SalesQuotaBreakdownRequest::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Validate total matches parent amount (tolerance 0.01)
    if (totalBreakdown.subtract(parent.getAmount()).abs().compareTo(new BigDecimal("0.01")) > 0) {
      throw new RuntimeException(
          "Breakdown total ("
              + totalBreakdown
              + ") does not match parent amount ("
              + parent.getAmount()
              + ")");
    }

    // Create breakdown records
    for (SalesQuotaBreakdownRequest req : breakdowns) {
      SalesQuota child = new SalesQuota();
      child.setParentId(quotaId);
      child.setQuarter(req.getQuarter());
      child.setYear(parent.getYear());
      child.setTeamId(req.getTeamId());
      child.setUserId(req.getUserId());
      child.setAmount(req.getAmount());
      child.setStatus("DRAFT");
      child.setPeriodStart(parent.getPeriodStart());
      child.setPeriodEnd(parent.getPeriodEnd());
      salesQuotaRepository.insert(child);

      SalesQuotaBreakdown breakdown = new SalesQuotaBreakdown();
      breakdown.setParentQuotaId(quotaId);
      breakdown.setChildQuotaId(child.getId());
      breakdown.setAmount(req.getAmount());
      salesQuotaBreakdownRepository.insert(breakdown);
    }

    log.info("Breakdown quota: parentId={}, breakdowns={}", quotaId, breakdowns.size());
    auditService.record(
        "CREATE",
        "SALES_QUOTA_BREAKDOWN",
        quotaId,
        "Breakdown quota: " + breakdowns.size() + " items");
  }

  @Override
  public List<Map<String, Object>> getBreakdown(Long quotaId) {
    List<SalesQuotaBreakdown> breakdowns =
        salesQuotaBreakdownRepository.findByParentQuotaId(quotaId);
    List<Map<String, Object>> result = new ArrayList<>();
    for (SalesQuotaBreakdown bd : breakdowns) {
      Map<String, Object> map = new HashMap<>();
      map.put("id", bd.getId());
      map.put("parentQuotaId", bd.getParentQuotaId());
      map.put("childQuotaId", bd.getChildQuotaId());
      map.put("amount", bd.getAmount());
      SalesQuota child = salesQuotaRepository.selectById(bd.getChildQuotaId());
      if (child != null) {
        map.put("childQuota", toResponse(child));
      }
      result.add(map);
    }
    return result;
  }

  @Override
  public SalesQuotaAchievementResponse getAchievement(Long quotaId) {
    SalesQuota quota = salesQuotaRepository.selectById(quotaId);
    if (quota == null) {
      throw new RuntimeException("Quota not found: " + quotaId);
    }
    Map<String, Object> achievement = salesQuotaRepository.getAchievement(quotaId);
    SalesQuotaAchievementResponse response = new SalesQuotaAchievementResponse();
    response.setQuotaId(quotaId);
    response.setQuotaAmount(toBigDecimal(achievement.get("quota_amount")));
    response.setActualAmount(toBigDecimal(achievement.get("actual_amount")));
    response.setAchievementRate(toBigDecimal(achievement.get("achievement_rate")));
    response.setCalculatedAt(LocalDateTime.now());

    // Determine status
    BigDecimal rate = response.getAchievementRate();
    if (rate.compareTo(new BigDecimal("80")) >= 0) {
      response.setStatus("ON_TRACK");
    } else if (rate.compareTo(new BigDecimal("60")) >= 0) {
      response.setStatus("AT_RISK");
    } else {
      response.setStatus("BELOW_TARGET");
    }
    return response;
  }

  @Override
  public List<Map<String, Object>> getVersions(Long quotaId) {
    List<SalesQuotaVersion> versions =
        salesQuotaVersionRepository.findByQuotaIdOrderByVersionDesc(quotaId);
    List<Map<String, Object>> result = new ArrayList<>();
    for (SalesQuotaVersion v : versions) {
      Map<String, Object> map = new HashMap<>();
      map.put("id", v.getId());
      map.put("quotaId", v.getQuotaId());
      map.put("oldAmount", v.getOldAmount());
      map.put("newAmount", v.getNewAmount());
      map.put("changedBy", v.getChangedBy());
      map.put("changedAt", v.getChangedAt());
      map.put("changeReason", v.getChangeReason());
      map.put("versionNumber", v.getVersionNumber());
      result.add(map);
    }
    return result;
  }

  @Override
  public List<Map<String, Object>> getTeamRanking(Integer year) {
    return salesQuotaRepository.getTeamRanking(year);
  }

  @Override
  public SalesQuotaSummaryResponse getSummary(Integer year) {
    Map<String, Object> summary = salesQuotaRepository.getSummary(year);
    BigDecimal totalQuota = toBigDecimal(summary.get("total_quota"));
    BigDecimal totalActual = toBigDecimal(summary.get("total_actual"));
    SalesQuotaSummaryResponse response = new SalesQuotaSummaryResponse();
    response.setTotalQuota(totalQuota);
    response.setTotalActual(totalActual);
    if (totalQuota.compareTo(BigDecimal.ZERO) > 0) {
      response.setAchievementRate(
          totalActual.multiply(new BigDecimal("100")).divide(totalQuota, 2, RoundingMode.HALF_UP));
    } else {
      response.setAchievementRate(BigDecimal.ZERO);
    }
    return response;
  }

  private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    }
    return new BigDecimal(value.toString());
  }

  private SalesQuotaResponse toResponse(SalesQuota quota) {
    SalesQuotaResponse response = new SalesQuotaResponse();
    BeanUtils.copyProperties(quota, response);
    // 关联查询团队名称
    if (quota.getTeamId() != null) {
      Department dept = departmentMapper.selectById(quota.getTeamId());
      if (dept != null) {
        response.setTeamName(dept.getName());
      }
    }
    // 关联查询用户名称
    if (quota.getUserId() != null) {
      User user = userMapper.selectById(quota.getUserId());
      if (user != null) {
        response.setUserName(user.getUsername());
      }
    }
    return response;
  }
}
