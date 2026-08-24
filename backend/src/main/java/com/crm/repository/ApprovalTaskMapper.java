package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.ApprovalTask;
import org.apache.ibatis.annotations.Mapper;

/** 审批任务 Mapper。 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {}
