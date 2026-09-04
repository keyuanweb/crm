/** 定时导出执行记录 Repository（079-scheduled-export）。 */
package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.model.entity.ScheduledExportExecution;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduledExportExecutionRepository extends BaseMapper<ScheduledExportExecution> {

  @Select(
      "SELECT * FROM scheduled_export_execution WHERE scheduled_export_id = #{scheduledExportId} ORDER BY executed_at DESC")
  List<ScheduledExportExecution> findByScheduledExportIdOrderByExecutedAtDesc(
      @Param("scheduledExportId") Long scheduledExportId);
}
