package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Contact;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 联系人 Mapper。 */
@Mapper
public interface ContactMapper extends BaseMapper<Contact> {

  /** 回收站（025）：查已逻辑删除记录（绕过 @TableLogic）。 */
  @Select("SELECT * FROM contact WHERE deleted = 1 ORDER BY updated_at DESC")
  List<Contact> selectDeletedAll();

  @Select(
      "SELECT * FROM contact WHERE deleted = 1 AND created_by = #{userId} ORDER BY updated_at DESC")
  List<Contact> selectDeletedByUser(@Param("userId") Long userId);

  @Update(
      "UPDATE contact SET deleted = 0, version = version + 1, updated_at = NOW() WHERE id = #{id}")
  int restoreById(@Param("id") Long id);

  @Delete("DELETE FROM contact WHERE id = #{id}")
  int purgeById(@Param("id") Long id);
}
