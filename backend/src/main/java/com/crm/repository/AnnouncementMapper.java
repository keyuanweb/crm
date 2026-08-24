package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;

/** 公告 Mapper。 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {}
