package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.WorkflowExecutionLog;
import org.apache.ibatis.annotations.Mapper;

/** 工作流执行日志 Mapper。 */
@Mapper
public interface WorkflowExecutionLogMapper extends BaseMapper<WorkflowExecutionLog> {}
