package com.crm.service.quota;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crm.dto.quota.SalesQuotaAchievementResponse;
import com.crm.dto.quota.SalesQuotaBreakdownRequest;
import com.crm.dto.quota.SalesQuotaRequest;
import com.crm.dto.quota.SalesQuotaResponse;
import com.crm.dto.quota.SalesQuotaSummaryResponse;
import java.util.List;
import java.util.Map;

/** 销售配额 Service 接口（078-sales-quota）。 */
public interface SalesQuotaService {

  /** 创建配额。 */
  SalesQuotaResponse createQuota(SalesQuotaRequest request);

  /** 获取配额列表。 */
  IPage<SalesQuotaResponse> getQuotas(
      int page, int size, Integer year, Long teamId, Long userId, String status);

  /** 获取配额详情。 */
  SalesQuotaResponse getQuota(Long id);

  /** 更新配额。 */
  SalesQuotaResponse updateQuota(Long id, SalesQuotaRequest request);

  /** 分解配额。 */
  void breakdownQuota(Long quotaId, List<SalesQuotaBreakdownRequest> breakdowns);

  /** 获取配额分解。 */
  List<Map<String, Object>> getBreakdown(Long quotaId);

  /** 获取配额达成率。 */
  SalesQuotaAchievementResponse getAchievement(Long quotaId);

  /** 获取配额版本历史。 */
  List<Map<String, Object>> getVersions(Long quotaId);

  /** 获取团队排名。 */
  List<Map<String, Object>> getTeamRanking(Integer year);

  /** 获取年度配额汇总。 */
  SalesQuotaSummaryResponse getSummary(Integer year);
}
