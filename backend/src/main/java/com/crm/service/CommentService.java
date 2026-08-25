package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.announcement.CommentRequest;
import com.crm.dto.announcement.CommentResponse;
import com.crm.entity.Comment;
import com.crm.entity.User;
import com.crm.repository.CommentMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 评论服务（037-announcements，FR-003/004/006）：多实体评论 CRUD + @提及解析 → 026 通知。 */
@Service
public class CommentService {

  private static final Pattern MENTION = Pattern.compile("@([\\u4e00-\\u9fa5A-Za-z0-9_]{2,30})");
  private static final Set<String> ENTITY_TYPES =
      Set.of("CUSTOMER", "LEAD", "OPPORTUNITY", "TICKET");

  private final CommentMapper commentMapper;
  private final UserMapper userMapper;
  private final NotificationService notificationService;
  private final EntityAccessService entityAccessService;

  public CommentService(
      CommentMapper commentMapper,
      UserMapper userMapper,
      NotificationService notificationService,
      EntityAccessService entityAccessService) {
    this.commentMapper = commentMapper;
    this.userMapper = userMapper;
    this.notificationService = notificationService;
    this.entityAccessService = entityAccessService;
  }

  /** 063(安全加固)：评论关联实体须对当前用户可见（TICKET 由工单体系管控，跳过行级）。 */
  private void checkEntityVisible(String entityType, Long entityId) {
    Long userId = SecurityUtil.currentUserId();
    if (userId == null) {
      return;
    }
    var principal = SecurityUtil.currentPrincipal();
    if (principal != null && "ADMIN".equals(principal.role())) {
      return;
    }
    boolean visible =
        switch (entityType) {
          case "CUSTOMER" -> entityAccessService.canViewCustomer(userId, entityId);
          case "LEAD" -> entityAccessService.canViewLead(userId, entityId);
          case "OPPORTUNITY" -> entityAccessService.canViewOpportunity(userId, entityId);
          default -> true; // TICKET 等
        };
    if (!visible) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }

  /** 评论列表（时间正序）。 */
  public PageResult<CommentResponse> list(
      String entityType, Long entityId, long page, long pageSize) {
    checkEntityVisible(entityType, entityId);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Comment> p =
        commentMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
            new LambdaQueryWrapper<Comment>()
                .eq(Comment::getEntityType, entityType)
                .eq(Comment::getEntityId, entityId)
                .orderByAsc(Comment::getCreatedAt));
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  /** 发表评论：@提及 → 通知被提及人。 */
  @Transactional
  public CommentResponse create(CommentRequest req) {
    if (!ENTITY_TYPES.contains(req.getEntityType())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的评论实体");
    }
    if (!StringUtils.hasText(req.getContent())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "评论内容不能为空");
    }
    if (req.getContent().length() > 1000) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "评论过长");
    }
    checkEntityVisible(req.getEntityType(), req.getEntityId());
    Long author = SecurityUtil.currentUserId();
    Comment comment = new Comment();
    comment.setEntityType(req.getEntityType());
    comment.setEntityId(req.getEntityId());
    comment.setContent(req.getContent().trim());
    comment.setAuthorId(author);
    commentMapper.insert(comment);
    notifyMentions(comment);
    return toResponse(comment);
  }

  /** 删除（作者/管理员）。 */
  @Transactional
  public void delete(Long id) {
    Comment comment = commentMapper.selectById(id);
    if (comment == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "评论不存在");
    }
    Long current = SecurityUtil.currentUserId();
    boolean isAdmin =
        SecurityUtil.currentPrincipal() != null
            && "ADMIN".equals(SecurityUtil.currentPrincipal().role());
    if (!comment.getAuthorId().equals(current) && !isAdmin) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    commentMapper.deleteById(id);
  }

  /**
   * @提及解析：@用户名 → 启用用户 → 026 通知。
   */
  private void notifyMentions(Comment comment) {
    Matcher m = MENTION.matcher(comment.getContent());
    Map<String, Long> users =
        userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getEnabled, 1)).stream()
            .collect(
                Collectors.toMap(
                    u -> u.getUsername() != null ? u.getUsername() : "", User::getId, (a, b) -> a));
    while (m.find()) {
      String name = m.group(1);
      Long userId = users.get(name);
      if (userId != null && !userId.equals(comment.getAuthorId())) {
        try {
          notificationService.notify(
              userId,
              NotificationService.TYPE_WORKFLOW,
              "你在「"
                  + comment.getEntityType()
                  + " #"
                  + comment.getEntityId()
                  + "」中被 @提及："
                  + comment.getContent(),
              "COMMENT",
              comment.getEntityId());
        } catch (Exception ex) {
          // 通知失败不影响评论
        }
      }
    }
  }

  private CommentResponse toResponse(Comment c) {
    CommentResponse r = new CommentResponse();
    r.setId(c.getId());
    r.setEntityType(c.getEntityType());
    r.setEntityId(c.getEntityId());
    r.setContent(c.getContent());
    r.setAuthorId(c.getAuthorId());
    User u = userMapper.selectById(c.getAuthorId());
    r.setAuthorName(
        u == null
            ? ("用户#" + c.getAuthorId())
            : (u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
    r.setCreatedAt(c.getCreatedAt());
    return r;
  }
}
