package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.MailAccount;
import org.apache.ibatis.annotations.Mapper;

/** 邮件账户 Mapper。 */
@Mapper
public interface MailAccountMapper extends BaseMapper<MailAccount> {}
