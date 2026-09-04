/** 定时导出任务 Repository（079-scheduled-export）。 */
package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.model.entity.ScheduledExport;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduledExportRepository extends BaseMapper<ScheduledExport> {

  @Select(
      "SELECT * FROM scheduled_export WHERE user_id = #{userId} AND status = #{status} ORDER BY created_at DESC")
  List<ScheduledExport> findByUserIdAndStatus(
      @Param("userId") Long userId, @Param("status") String status);

  @Select(
      "SELECT * FROM scheduled_export WHERE status = #{status} AND next_execution_time <= #{time} ORDER BY next_execution_time ASC")
  List<ScheduledExport> findByStatusAndNextExecutionTimeLessThanEqual(
      @Param("status") String status, @Param("time") LocalDateTime time);
}
