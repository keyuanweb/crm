package com.crm.repository.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.SalesQuotaVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 配额版本历史 Repository（078-sales-quota）。 */
@Mapper
public interface SalesQuotaVersionRepository extends BaseMapper<SalesQuotaVersion> {

  /** 查询配额版本历史（按版本号降序）。 */
  @Select(
      "SELECT * FROM sales_quota_version WHERE quota_id = #{quotaId} ORDER BY version_number DESC")
  List<SalesQuotaVersion> findByQuotaIdOrderByVersionDesc(Long quotaId);
}
