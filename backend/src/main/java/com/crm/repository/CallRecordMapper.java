package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.CallRecord;
import org.apache.ibatis.annotations.Mapper;

/** 通话记录 Mapper。 */
@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {}
