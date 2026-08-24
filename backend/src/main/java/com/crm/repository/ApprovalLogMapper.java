package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.ApprovalLog;
import org.apache.ibatis.annotations.Mapper;

/** 审批日志 Mapper。 */
@Mapper
public interface ApprovalLogMapper extends BaseMapper<ApprovalLog> {}
