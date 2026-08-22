package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.SlaPolicy;
import org.apache.ibatis.annotations.Mapper;

/** SLA 策略 Mapper。 */
@Mapper
public interface SlaPolicyMapper extends BaseMapper<SlaPolicy> {}
