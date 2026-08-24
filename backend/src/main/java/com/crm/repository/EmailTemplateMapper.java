package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.EmailTemplate;
import org.apache.ibatis.annotations.Mapper;

/** 邮件模板 Mapper。 */
@Mapper
public interface EmailTemplateMapper extends BaseMapper<EmailTemplate> {}
