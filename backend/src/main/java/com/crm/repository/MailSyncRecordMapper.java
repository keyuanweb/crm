package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.MailSyncRecord;
import org.apache.ibatis.annotations.Mapper;

/** 邮件同步记录 Mapper。 */
@Mapper
public interface MailSyncRecordMapper extends BaseMapper<MailSyncRecord> {}
