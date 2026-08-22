package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.CustomFieldValue;
import org.apache.ibatis.annotations.Mapper;

/** 自定义字段值 Mapper。 */
@Mapper
public interface CustomFieldValueMapper extends BaseMapper<CustomFieldValue> {}
