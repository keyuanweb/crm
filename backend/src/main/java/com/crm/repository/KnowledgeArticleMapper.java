package com.crm.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.entity.KnowledgeArticle;
import org.apache.ibatis.annotations.Mapper;

/** 知识库文章 Mapper。 */
@Mapper
public interface KnowledgeArticleMapper extends BaseMapper<KnowledgeArticle> {}
