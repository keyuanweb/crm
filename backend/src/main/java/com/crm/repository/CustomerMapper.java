package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

/** 客户 Mapper。 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {}
