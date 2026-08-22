package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/** 回款记录 Mapper。 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {}
