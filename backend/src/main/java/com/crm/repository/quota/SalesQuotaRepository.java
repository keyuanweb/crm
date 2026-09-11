package com.crm.repository.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.SalesQuota;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 销售配额 Repository（078-sales-quota）。 */
@Mapper
public interface SalesQuotaRepository extends BaseMapper<SalesQuota> {

  /** 查询各层级达成率（聚合实际销售额）。 */
  @Select(
      "<script>"
          + "SELECT sq.id, sq.amount as quota_amount, "
          + "COALESCE(SUM(opp.amount), 0) as actual_amount, "
          + "CASE WHEN sq.amount > 0 THEN ROUND(COALESCE(SUM(opp.amount), 0) / sq.amount * 100, 2) ELSE 0 END as achievement_rate "
          + "FROM sales_quota sq "
          + "LEFT JOIN opportunity opp ON opp.user_id = sq.user_id "
          + "AND opp.status = 'CLOSED_WON' "
          + "AND opp.close_date BETWEEN sq.period_start AND sq.period_end "
          + "WHERE sq.id = #{quotaId} "
          + "GROUP BY sq.id, sq.amount"
          + "</script>")
  Map<String, Object> getAchievement(@Param("quotaId") Long quotaId);

  /** 查询团队达成率排名。 */
  @Select(
      "<script>"
          + "SELECT sq.team_id as teamId, d.name as teamName, "
          + "sq.amount as quota_amount, "
          + "COALESCE(SUM(opp.amount), 0) as actual_amount, "
          + "CASE WHEN sq.amount > 0 THEN ROUND(COALESCE(SUM(opp.amount), 0) / sq.amount * 100, 2) ELSE 0 END as achievement_rate "
          + "FROM sales_quota sq "
          + "LEFT JOIN department d ON d.id = sq.team_id "
          + "LEFT JOIN opportunity opp ON opp.user_id IN (SELECT id FROM user WHERE dept_id = sq.team_id) "
          + "AND opp.status = 'CLOSED_WON' "
          + "AND opp.close_date BETWEEN sq.period_start AND sq.period_end "
          + "WHERE sq.year = #{year} AND sq.team_id IS NOT NULL "
          + "GROUP BY sq.team_id, d.name, sq.amount "
          + "ORDER BY achievement_rate DESC"
          + "</script>")
  List<Map<String, Object>> getTeamRanking(@Param("year") Integer year);

  /** 查询年度配额汇总：顶层配额总额 + 年度已成交商机总额。 */
  @Select(
      "SELECT "
          + "COALESCE((SELECT SUM(amount) FROM sales_quota WHERE year = #{year} AND parent_id IS NULL), 0) AS total_quota, "
          + "COALESCE((SELECT SUM(amount) FROM opportunity WHERE status = 'CLOSED_WON' AND close_date BETWEEN CONCAT(#{year}, '-01-01') AND CONCAT(#{year}, '-12-31')), 0) AS total_actual")
  Map<String, Object> getSummary(@Param("year") Integer year);
}
