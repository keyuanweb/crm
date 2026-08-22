package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Contact;
import org.apache.ibatis.annotations.Mapper;

/** 联系人 Mapper。 */
@Mapper
public interface ContactMapper extends BaseMapper<Contact> {}
