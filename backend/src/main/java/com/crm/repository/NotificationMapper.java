package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/** 通知 Mapper。 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {}
