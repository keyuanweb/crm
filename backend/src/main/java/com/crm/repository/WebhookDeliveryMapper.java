package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.WebhookDelivery;
import org.apache.ibatis.annotations.Mapper;

/** Webhook 推送记录 Mapper。 */
@Mapper
public interface WebhookDeliveryMapper extends BaseMapper<WebhookDelivery> {}
