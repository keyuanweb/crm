package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/** 评论 Mapper。 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {}
