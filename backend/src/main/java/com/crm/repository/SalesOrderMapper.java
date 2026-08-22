package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;

/** 订单 Mapper。 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {}
