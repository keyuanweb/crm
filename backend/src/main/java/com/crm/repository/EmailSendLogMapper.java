package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.EmailSendLog;
import org.apache.ibatis.annotations.Mapper;

/** 邮件发送记录 Mapper。 */
@Mapper
public interface EmailSendLogMapper extends BaseMapper<EmailSendLog> {}
