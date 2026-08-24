package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.EmailCampaign;
import org.apache.ibatis.annotations.Mapper;

/** 邮件群发批次 Mapper。 */
@Mapper
public interface EmailCampaignMapper extends BaseMapper<EmailCampaign> {}
