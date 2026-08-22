package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Quote;
import org.apache.ibatis.annotations.Mapper;

/** 报价单 Mapper。 */
@Mapper
public interface QuoteMapper extends BaseMapper<Quote> {}
