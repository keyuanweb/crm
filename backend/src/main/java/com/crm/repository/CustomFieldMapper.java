package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.CustomField;
import org.apache.ibatis.annotations.Mapper;

/** 自定义字段定义 Mapper。 */
@Mapper
public interface CustomFieldMapper extends BaseMapper<CustomField> {}
