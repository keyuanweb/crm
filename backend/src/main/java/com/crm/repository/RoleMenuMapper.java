package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.RoleMenu;
import org.apache.ibatis.annotations.Mapper;

/** 角色-菜单关联 Mapper。 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenu> {}
