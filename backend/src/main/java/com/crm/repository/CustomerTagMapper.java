package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.CustomerTag;
import org.apache.ibatis.annotations.Mapper;

/** 客户-标签关联 Mapper。 */
@Mapper
public interface CustomerTagMapper extends BaseMapper<CustomerTag> {}
