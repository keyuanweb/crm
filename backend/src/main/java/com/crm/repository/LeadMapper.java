package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Lead;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LeadMapper extends BaseMapper<Lead> {

  /** 回收站（025）：查已逻辑删除记录（绕过 @TableLogic）。 */
  @Select("SELECT * FROM `lead` WHERE deleted = 1 ORDER BY updated_at DESC")
  List<Lead> selectDeletedAll();

  @Select(
      "SELECT * FROM `lead` WHERE deleted = 1 AND created_by = #{userId} ORDER BY updated_at DESC")
  List<Lead> selectDeletedByUser(@Param("userId") Long userId);

  @Update(
      "UPDATE `lead` SET deleted = 0, version = version + 1, updated_at = NOW() WHERE id = #{id}")
  int restoreById(@Param("id") Long id);

  @Delete("DELETE FROM `lead` WHERE id = #{id}")
  int purgeById(@Param("id") Long id);
}
