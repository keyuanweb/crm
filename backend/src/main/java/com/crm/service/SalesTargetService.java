package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.stats.SalesTargetRequest;
import com.crm.dto.stats.SalesTargetResponse;
import com.crm.entity.SalesTarget;
import com.crm.repository.SalesTargetMapper;
import com.crm.security.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 销售目标服务（006-sales-dashboard，FR-D04/D05）：按月 upsert，审计记录。 */
@Service
public class SalesTargetService {

  private final SalesTargetMapper salesTargetMapper;
  private final AuditService auditService;
  private final DashboardStatsService dashboardStatsService;

  public SalesTargetService(
      SalesTargetMapper salesTargetMapper,
      AuditService auditService,
      DashboardStatsService dashboardStatsService) {
    this.salesTargetMapper = salesTargetMapper;
    this.auditService = auditService;
    this.dashboardStatsService = dashboardStatsService;
  }

  /** 查询某月目标（userId 为空=全局目标）；未设置时返回 targetAmount=null（非 404）。 */
  public SalesTargetResponse get(String month, Long userId) {
    SalesTarget target = findByMonth(month, userId);
    if (target == null) {
      return new SalesTargetResponse(month, null, userId, null, null);
    }
    return new SalesTargetResponse(
        target.getTargetMonth(),
        target.getTargetAmount(),
        target.getUserId(),
        target.getCreatedBy(),
        target.getUpdatedAt());
  }

  /** 设置/更新某月目标（upsert：存在则更新金额，否则插入）。userId 为空=全局目标。 */
  @Transactional
  public SalesTargetResponse set(SalesTargetRequest request) {
    String month = request.getMonth();
    Long userId = request.getUserId();
    SalesTarget existing = findByMonth(month, userId);
    if (existing != null) {
      existing.setTargetAmount(request.getTargetAmount());
      existing.setVersion(existing.getVersion() == null ? 0 : existing.getVersion());
      int rows = salesTargetMapper.updateById(existing);
      if (rows == 0) {
        // 乐观锁冲突：重查一次并覆盖（目标为配置型数据，容忍并发覆盖）
        SalesTarget fresh = findByMonth(month, userId);
        if (fresh != null) {
          fresh.setTargetAmount(request.getTargetAmount());
          salesTargetMapper.updateById(fresh);
        }
      }
      auditService.record(
          "UPDATE",
          "SALES_TARGET",
          existing.getId(),
          "更新目标："
              + month
              + "="
              + request.getTargetAmount()
              + (userId == null ? "" : "(用户" + userId + ")"));
      dashboardStatsService.evict();
      return get(month, userId);
    }
    SalesTarget target = new SalesTarget();
    target.setTargetMonth(month);
    target.setTargetAmount(request.getTargetAmount());
    target.setUserId(userId);
    target.setCreatedBy(SecurityUtil.currentUserId());
    salesTargetMapper.insert(target);
    auditService.record(
        "CREATE",
        "SALES_TARGET",
        target.getId(),
        "设置目标："
            + month
            + "="
            + request.getTargetAmount()
            + (userId == null ? "" : "(用户" + userId + ")"));
    dashboardStatsService.evict();
    return get(month, userId);
  }

  private SalesTarget findByMonth(String month, Long userId) {
    LambdaQueryWrapper<SalesTarget> qw =
        new LambdaQueryWrapper<SalesTarget>().eq(SalesTarget::getTargetMonth, month);
    if (userId == null) {
      qw.isNull(SalesTarget::getUserId);
    } else {
      qw.eq(SalesTarget::getUserId, userId);
    }
    return salesTargetMapper.selectOne(qw);
  }
}
