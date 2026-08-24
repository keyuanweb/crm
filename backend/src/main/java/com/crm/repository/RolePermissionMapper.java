package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/** 角色-操作权限关联 Mapper。 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {}
