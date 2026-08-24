package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.SignatureRecord;
import org.apache.ibatis.annotations.Mapper;

/** 电子签署记录 Mapper。 */
@Mapper
public interface SignatureRecordMapper extends BaseMapper<SignatureRecord> {}
