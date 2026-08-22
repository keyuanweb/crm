package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.ExportJob;
import org.apache.ibatis.annotations.Mapper;

/** 导出任务 Mapper。 */
@Mapper
public interface ExportJobMapper extends BaseMapper<ExportJob> {}
