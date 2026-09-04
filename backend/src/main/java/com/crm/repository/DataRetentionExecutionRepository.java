/** 数据保留执行记录 Repository（080-data-retention）。 */
package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.model.entity.DataRetentionExecution;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DataRetentionExecutionRepository extends BaseMapper<DataRetentionExecution> {

  @Select(
      "SELECT * FROM data_retention_execution WHERE policy_id = #{policyId} ORDER BY executed_at DESC")
  List<DataRetentionExecution> findByPolicyIdOrderByExecutedAtDesc(
      @Param("policyId") Long policyId);
}
