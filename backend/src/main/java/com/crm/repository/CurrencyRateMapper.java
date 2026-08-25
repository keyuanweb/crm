package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.CurrencyRate;
import org.apache.ibatis.annotations.Mapper;

/** 币种汇率 Mapper。 */
@Mapper
public interface CurrencyRateMapper extends BaseMapper<CurrencyRate> {}
