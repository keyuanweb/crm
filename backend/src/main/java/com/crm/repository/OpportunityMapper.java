package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Opportunity;
import org.apache.ibatis.annotations.Mapper;

/** 商机 Mapper。 */
@Mapper
public interface OpportunityMapper extends BaseMapper<Opportunity> {}
