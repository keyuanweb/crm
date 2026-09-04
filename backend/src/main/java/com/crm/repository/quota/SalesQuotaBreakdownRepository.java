package com.crm.repository.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.SalesQuotaBreakdown;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 配额分解关系 Repository（078-sales-quota）。 */
@Mapper
public interface SalesQuotaBreakdownRepository extends BaseMapper<SalesQuotaBreakdown> {

  /** 查询下级配额分解列表。 */
  @Select("SELECT * FROM sales_quota_breakdown WHERE parent_quota_id = #{parentQuotaId}")
  List<SalesQuotaBreakdown> findByParentQuotaId(Long parentQuotaId);

  /** 查询分解总和。 */
  @Select(
      "SELECT COALESCE(SUM(amount), 0) FROM sales_quota_breakdown WHERE parent_quota_id = #{parentQuotaId}")
  BigDecimal sumBreakdownAmount(@Param("parentQuotaId") Long parentQuotaId);
}
