package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.WebhookSubscription;
import org.apache.ibatis.annotations.Mapper;

/** Webhook 订阅 Mapper。 */
@Mapper
public interface WebhookSubscriptionMapper extends BaseMapper<WebhookSubscription> {}
