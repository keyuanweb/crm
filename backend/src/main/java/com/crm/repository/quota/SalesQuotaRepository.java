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
          + "COALESCE(SUM(so.amount), 0) / 1000000 as actual_amount, "
          + "CASE WHEN sq.amount > 0 THEN ROUND(COALESCE(SUM(so.amount), 0) / 1000000 / sq.amount * 100, 2) ELSE 0 END as achievement_rate "
          + "FROM sales_quota sq "
          + "LEFT JOIN sales_opportunity so ON so.created_by = sq.user_id "
          + "AND so.stage = 'CLOSED_WON' "
          + "AND so.closed_at BETWEEN sq.period_start AND sq.period_end "
          + "WHERE sq.id = #{quotaId} "
          + "GROUP BY sq.id, sq.amount"
          + "</script>")
  Map<String, Object> getAchievement(@Param("quotaId") Long quotaId);

  /** 查询团队达成率排名。 */
  @Select(
      "<script>"
          + "SELECT sq.team_id as teamId, d.name as teamName, "
          + "sq.amount as quota_amount, "
          + "COALESCE(SUM(so.amount), 0) / 1000000 as actual_amount, "
          + "CASE WHEN sq.amount > 0 THEN ROUND(COALESCE(SUM(so.amount), 0) / 1000000 / sq.amount * 100, 2) ELSE 0 END as achievement_rate "
          + "FROM sales_quota sq "
          + "LEFT JOIN department d ON d.id = sq.team_id "
          + "LEFT JOIN sales_opportunity so ON so.created_by IN (SELECT id FROM user WHERE department_id = sq.team_id) "
          + "AND so.stage = 'CLOSED_WON' "
          + "AND so.closed_at BETWEEN sq.period_start AND sq.period_end "
          + "WHERE sq.year = #{year} AND sq.team_id IS NOT NULL "
          + "GROUP BY sq.team_id, d.name, sq.amount "
          + "ORDER BY achievement_rate DESC"
          + "</script>")
  List<Map<String, Object>> getTeamRanking(@Param("year") Integer year);

  /** 查询年度配额汇总：顶层配额总额 + 年度已成交销售机会总额。 */
  @Select(
      "SELECT "
          + "COALESCE((SELECT SUM(amount) FROM sales_quota WHERE year = #{year} AND parent_id IS NULL), 0) AS total_quota, "
          + "COALESCE((SELECT SUM(amount) / 1000000 FROM sales_opportunity WHERE stage = 'CLOSED_WON' AND YEAR(closed_at) = #{year}), 0) AS total_actual")
  Map<String, Object> getSummary(@Param("year") Integer year);

  /** 批量查询配额达成率（列表页填充实际销售额与达成率）。 */
  @Select(
      "<script>"
          + "SELECT sq.id, "
          + "COALESCE(SUM(so.amount), 0) / 1000000 as actual_amount, "
          + "CASE WHEN sq.amount > 0 THEN ROUND(COALESCE(SUM(so.amount), 0) / 1000000 / sq.amount * 100, 2) ELSE 0 END as achievement_rate "
          + "FROM sales_quota sq "
          + "LEFT JOIN sales_opportunity so ON so.created_by = sq.user_id "
          + "AND so.stage = 'CLOSED_WON' "
          + "AND so.closed_at BETWEEN sq.period_start AND sq.period_end "
          + "WHERE sq.id IN "
          + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
          + "GROUP BY sq.id, sq.amount"
          + "</script>")
  List<Map<String, Object>> getAchievementBatch(@Param("ids") List<Long> ids);
}
