package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Department;
import org.apache.ibatis.annotations.Mapper;

/** 部门 Mapper。 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {}
