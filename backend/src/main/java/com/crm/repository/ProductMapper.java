package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/** 产品 Mapper。 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {}
