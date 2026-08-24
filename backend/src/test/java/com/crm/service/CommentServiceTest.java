package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.dto.announcement.CommentRequest;
import com.crm.dto.announcement.CommentResponse;
import com.crm.entity.Comment;
import com.crm.entity.User;
import com.crm.repository.CommentMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** CommentService 单元测试（037 T005）：评论 CRUD + @提及通知。 */
class CommentServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Comment.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  private CommentMapper commentMapper;
  private UserMapper userMapper;
  private NotificationService notificationService;
  private CommentService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    commentMapper = mock(CommentMapper.class);
    userMapper = mock(UserMapper.class);
    notificationService = mock(NotificationService.class);
    service = new CommentService(commentMapper, userMapper, notificationService);
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

  @Test
  @DisplayName("发表评论成功")
  void createSuccess() {
    CommentRequest req = new CommentRequest();
    req.setEntityType("CUSTOMER");
    req.setEntityId(1L);
    req.setContent("请跟进");

    CommentResponse resp = service.create(req);

    assertThat(resp.getEntityType()).isEqualTo("CUSTOMER");
    verify(commentMapper).insert(any(Comment.class));
  }

  @Test
  @DisplayName("@提及解析并通知被提及人")
  void mentionNotifies() {
    User zhangsan = new User();
    zhangsan.setId(2L);
    zhangsan.setUsername("zhangsan");
    when(userMapper.selectList(any())).thenReturn(List.of(zhangsan));

    CommentRequest req = new CommentRequest();
    req.setEntityType("CUSTOMER");
    req.setEntityId(1L);
    req.setContent("@zhangsan 请确认");
    service.create(req);

    verify(notificationService)
        .notify(
            Mockito.eq(2L),
            Mockito.anyString(),
            Mockito.contains("@zhangsan"),
            Mockito.eq("COMMENT"),
            Mockito.eq(1L));
  }

  @Test
  @DisplayName("非法实体类型拒绝")
  void invalidEntityRejected() {
    CommentRequest req = new CommentRequest();
    req.setEntityType("UNKNOWN");
    req.setEntityId(1L);
    req.setContent("x");

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("非作者删除拒绝（非管理员）")
  void deleteByOtherRejected() {
    // 当前用户为 SALES（非管理员）
    securityUtilMock.close();
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "sales", "SALES"));

    Comment comment = new Comment();
    comment.setId(1L);
    comment.setAuthorId(99L);
    when(commentMapper.selectById(1L)).thenReturn(comment);

    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
  }
}
