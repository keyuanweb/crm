package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.ProductPrice;
import org.apache.ibatis.annotations.Mapper;

/** 产品多币种价格 Mapper。 */
@Mapper
public interface ProductPriceMapper extends BaseMapper<ProductPrice> {}
