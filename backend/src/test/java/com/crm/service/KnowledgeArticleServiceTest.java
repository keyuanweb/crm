package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.knowledge.ArticleRequest;
import com.crm.entity.KnowledgeArticle;
import com.crm.entity.User;
import com.crm.repository.KnowledgeArticleMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** KnowledgeArticleService 单元测试（015 T019）：CRUD/发布状态/搜索。 */
class KnowledgeArticleServiceTest {

  private KnowledgeArticleMapper articleMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private KnowledgeArticleService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, KnowledgeArticle.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  void setUp() {
    articleMapper = mock(KnowledgeArticleMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service = new KnowledgeArticleService(articleMapper, userMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private ArticleRequest request() {
    ArticleRequest req = new ArticleRequest();
    req.setCategory("FAULT_TROUBLESHOOTING");
    req.setTitle("如何重置密码");
    req.setContent("步骤...");
    req.setKeywords("密码,重置");
    return req;
  }

  private KnowledgeArticle article(Long id, String status) {
    KnowledgeArticle a = new KnowledgeArticle();
    a.setId(id);
    a.setCategory("FAULT_TROUBLESHOOTING");
    a.setTitle("如何重置密码");
    a.setStatus(status);
    a.setVersion(0);
    return a;
  }

  @Test
  @DisplayName("创建文章：默认 DRAFT，审计记录")
  void createDefaultsDraft() {
    when(articleMapper.insert(any(KnowledgeArticle.class)))
        .thenAnswer(
            invocation -> {
              KnowledgeArticle a = invocation.getArgument(0);
              a.setId(1L);
              return 1;
            });
    when(articleMapper.selectById(1L)).thenReturn(article(1L, "DRAFT"));

    var resp = service.create(request());

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("DRAFT");
    verify(articleMapper).insert(any(KnowledgeArticle.class));
    verify(auditService).record("CREATE", "KNOWLEDGE_ARTICLE", 1L, "创建文章：如何重置密码");
  }

  @Test
  @DisplayName("发布：DRAFT→PUBLISHED")
  void publishSucceeds() {
    KnowledgeArticle mutable = article(1L, "DRAFT");
    when(articleMapper.selectById(1L)).thenReturn(mutable);
    when(articleMapper.updateById(any(KnowledgeArticle.class))).thenReturn(1);

    var resp = service.publish(1L);

    assertThat(resp.getStatus()).isEqualTo("PUBLISHED");
    verify(auditService).record("PUBLISH", "KNOWLEDGE_ARTICLE", 1L, "发布文章：如何重置密码");
  }

  @Test
  @DisplayName("重复发布 → 409 ARTICLE_INVALID_STATE")
  void publishAlreadyPublishedThrows() {
    when(articleMapper.selectById(1L)).thenReturn(article(1L, "PUBLISHED"));

    assertThatThrownBy(() -> service.publish(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ARTICLE_INVALID_STATE);
    verify(articleMapper, never()).updateById(any(KnowledgeArticle.class));
  }

  @Test
  @DisplayName("下线：PUBLISHED→DRAFT")
  void unpublishSucceeds() {
    KnowledgeArticle mutable = article(1L, "PUBLISHED");
    when(articleMapper.selectById(1L)).thenReturn(mutable);
    when(articleMapper.updateById(any(KnowledgeArticle.class))).thenReturn(1);

    var resp = service.unpublish(1L);

    assertThat(resp.getStatus()).isEqualTo("DRAFT");
    verify(auditService).record("UNPUBLISH", "KNOWLEDGE_ARTICLE", 1L, "下线文章：如何重置密码");
  }

  @Test
  @DisplayName("删除文章：逻辑删除 + 审计")
  void deleteSucceeds() {
    when(articleMapper.selectById(1L)).thenReturn(article(1L, "PUBLISHED"));

    service.delete(1L);

    verify(articleMapper).deleteById(org.mockito.ArgumentMatchers.<Long>any());
    verify(auditService).record("DELETE", "KNOWLEDGE_ARTICLE", 1L, "删除文章：如何重置密码");
  }
}
