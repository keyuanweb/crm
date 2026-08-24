package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Opportunity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 商机 Mapper。 */
@Mapper
public interface OpportunityMapper extends BaseMapper<Opportunity> {

  /** 回收站（025）：查已逻辑删除记录（绕过 @TableLogic）。 */
  @Select("SELECT * FROM opportunity WHERE deleted = 1 ORDER BY updated_at DESC")
  List<Opportunity> selectDeletedAll();

  @Select(
      "SELECT * FROM opportunity WHERE deleted = 1 AND created_by = #{userId} ORDER BY updated_at DESC")
  List<Opportunity> selectDeletedByUser(@Param("userId") Long userId);

  @Update(
      "UPDATE opportunity SET deleted = 0, version = version + 1, updated_at = NOW() WHERE id = #{id}")
  int restoreById(@Param("id") Long id);

  @Delete("DELETE FROM opportunity WHERE id = #{id}")
  int purgeById(@Param("id") Long id);
}
