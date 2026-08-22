package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.PaymentPlan;
import org.apache.ibatis.annotations.Mapper;

/** 回款计划 Mapper。 */
@Mapper
public interface PaymentPlanMapper extends BaseMapper<PaymentPlan> {}
