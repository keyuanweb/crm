package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

/** 工单 Mapper。 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {}
