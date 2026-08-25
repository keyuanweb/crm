package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.EmailUnsubscribe;
import org.apache.ibatis.annotations.Mapper;

/** 邮件退订 Mapper。 */
@Mapper
public interface EmailUnsubscribeMapper extends BaseMapper<EmailUnsubscribe> {}
