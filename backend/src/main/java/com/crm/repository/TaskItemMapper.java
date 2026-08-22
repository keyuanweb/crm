package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.TaskItem;
import org.apache.ibatis.annotations.Mapper;

/** 任务 Mapper。 */
@Mapper
public interface TaskItemMapper extends BaseMapper<TaskItem> {}
