package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.knowledge.ArticleRequest;
import com.crm.dto.knowledge.ArticleResponse;
import com.crm.entity.KnowledgeArticle;
import com.crm.entity.User;
import com.crm.repository.KnowledgeArticleMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 知识库文章服务（015，FR-C06~C08）：CRUD/发布/下线/搜索。 */
@Service
public class KnowledgeArticleService {

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_PUBLISHED = "PUBLISHED";

  private final KnowledgeArticleMapper articleMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public KnowledgeArticleService(
      KnowledgeArticleMapper articleMapper, UserMapper userMapper, AuditService auditService) {
    this.articleMapper = articleMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /** 文章分页列表。includeDraft=true（客服/管理员）可见草稿；否则仅 PUBLISHED。 普通查询时也可用 status 过滤（仅管理场景）。 */
  public PageResult<ArticleResponse> page(
      String keyword,
      String category,
      String status,
      boolean includeDraft,
      long page,
      long pageSize) {
    LambdaQueryWrapper<KnowledgeArticle> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(
          w ->
              w.like(KnowledgeArticle::getTitle, kw)
                  .or()
                  .like(KnowledgeArticle::getContent, kw)
                  .or()
                  .like(KnowledgeArticle::getKeywords, kw));
    }
    if (StringUtils.hasText(category)) {
      qw.eq(KnowledgeArticle::getCategory, category.trim());
    }
    if (StringUtils.hasText(status)) {
      qw.eq(KnowledgeArticle::getStatus, status.trim());
    } else if (!includeDraft) {
      qw.eq(KnowledgeArticle::getStatus, STATUS_PUBLISHED);
    }
    qw.orderByDesc(KnowledgeArticle::getId);
    Page<KnowledgeArticle> p = articleMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(toResponses(p.getRecords()), p.getTotal(), page, pageSize);
  }

  @Transactional
  public ArticleResponse create(ArticleRequest req) {
    KnowledgeArticle article = new KnowledgeArticle();
    apply(req, article);
    article.setStatus(STATUS_DRAFT);
    article.setAuthorId(SecurityUtil.currentUserId());
    article.setCreatedBy(SecurityUtil.currentUserId());
    articleMapper.insert(article);
    auditService.record(
        "CREATE", "KNOWLEDGE_ARTICLE", article.getId(), "创建文章：" + article.getTitle());
    return toResponse(articleMapper.selectById(article.getId()));
  }

  @Transactional
  public ArticleResponse update(Long id, ArticleRequest req) {
    KnowledgeArticle existing = require(id);
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = articleMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "KNOWLEDGE_ARTICLE", id, "编辑文章：" + existing.getTitle());
    return toResponse(articleMapper.selectById(id));
  }

  @Transactional
  public ArticleResponse publish(Long id) {
    KnowledgeArticle article = require(id);
    if (STATUS_PUBLISHED.equals(article.getStatus())) {
      throw new BusinessException(ErrorCode.ARTICLE_INVALID_STATE);
    }
    article.setStatus(STATUS_PUBLISHED);
    articleMapper.updateById(article);
    auditService.record("PUBLISH", "KNOWLEDGE_ARTICLE", id, "发布文章：" + article.getTitle());
    return toResponse(articleMapper.selectById(id));
  }

  @Transactional
  public ArticleResponse unpublish(Long id) {
    KnowledgeArticle article = require(id);
    if (STATUS_DRAFT.equals(article.getStatus())) {
      throw new BusinessException(ErrorCode.ARTICLE_INVALID_STATE);
    }
    article.setStatus(STATUS_DRAFT);
    articleMapper.updateById(article);
    auditService.record("UNPUBLISH", "KNOWLEDGE_ARTICLE", id, "下线文章：" + article.getTitle());
    return toResponse(articleMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    KnowledgeArticle article = require(id);
    articleMapper.deleteById(id);
    auditService.record("DELETE", "KNOWLEDGE_ARTICLE", id, "删除文章：" + article.getTitle());
  }

  private void apply(ArticleRequest req, KnowledgeArticle article) {
    article.setCategory(req.getCategory().trim());
    article.setTitle(req.getTitle().trim());
    article.setContent(req.getContent());
    article.setKeywords(req.getKeywords());
  }

  private KnowledgeArticle require(Long id) {
    KnowledgeArticle article = articleMapper.selectById(id);
    if (article == null) {
      throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
    }
    return article;
  }

  private List<ArticleResponse> toResponses(List<KnowledgeArticle> articles) {
    if (articles.isEmpty()) {
      return List.of();
    }
    Set<Long> authorIds =
        articles.stream()
            .map(KnowledgeArticle::getAuthorId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, String> authorNames =
        authorIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
    return articles.stream()
        .map(
            a -> {
              ArticleResponse resp = toResponse(a);
              resp.setAuthorName(a.getAuthorId() == null ? null : authorNames.get(a.getAuthorId()));
              return resp;
            })
        .toList();
  }

  private ArticleResponse toResponse(KnowledgeArticle article) {
    ArticleResponse resp = new ArticleResponse();
    resp.setId(article.getId());
    resp.setCategory(article.getCategory());
    resp.setTitle(article.getTitle());
    resp.setContent(article.getContent());
    resp.setKeywords(article.getKeywords());
    resp.setStatus(article.getStatus());
    resp.setAuthorId(article.getAuthorId());
    resp.setVersion(article.getVersion());
    resp.setCreatedAt(article.getCreatedAt());
    return resp;
  }
}
