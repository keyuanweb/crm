package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Lead;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadMapper extends BaseMapper<Lead> {}
