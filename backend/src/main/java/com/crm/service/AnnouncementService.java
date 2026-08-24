package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.announcement.AnnouncementRequest;
import com.crm.dto.announcement.AnnouncementResponse;
import com.crm.entity.Announcement;
import com.crm.entity.AnnouncementRead;
import com.crm.repository.AnnouncementMapper;
import com.crm.repository.AnnouncementReadMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 公告服务（037-announcements，FR-001/002/005）：公告 CRUD + 置顶/过期过滤 + 已读（幂等）+ 未读计数。 */
@Service
public class AnnouncementService {

  private final AnnouncementMapper announcementMapper;
  private final AnnouncementReadMapper readMapper;
  private final AuditService auditService;

  public AnnouncementService(
      AnnouncementMapper announcementMapper,
      AnnouncementReadMapper readMapper,
      AuditService auditService) {
    this.announcementMapper = announcementMapper;
    this.readMapper = readMapper;
    this.auditService = auditService;
  }

  /** 公告列表（未过期 + 置顶优先 + 已读状态）。 */
  public PageResult<AnnouncementResponse> list(long page, long pageSize) {
    LambdaQueryWrapper<Announcement> qw =
        new LambdaQueryWrapper<Announcement>()
            .and(
                w ->
                    w.isNull(Announcement::getExpiresAt)
                        .or()
                        .gt(Announcement::getExpiresAt, LocalDateTime.now()))
            .orderByDesc(Announcement::getPinned)
            .orderByDesc(Announcement::getCreatedAt);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Announcement> p =
        announcementMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), qw);
    List<AnnouncementResponse> items =
        p.getRecords().stream().map(a -> toResponse(a, isRead(a.getId()))).toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  @Transactional
  public AnnouncementResponse create(AnnouncementRequest req) {
    if (!StringUtils.hasText(req.getTitle()) || !StringUtils.hasText(req.getContent())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "标题与正文不能为空");
    }
    Announcement a = new Announcement();
    a.setTitle(req.getTitle().trim());
    a.setContent(req.getContent());
    a.setPinned(req.getPinned() != null && req.getPinned());
    a.setExpiresAt(req.getExpiresAt());
    a.setCreatedBy(SecurityUtil.currentUserId());
    announcementMapper.insert(a);
    auditService.record("CREATE", "ANNOUNCEMENT", a.getId(), "发布公告：" + a.getTitle());
    return toResponse(a, false);
  }

  @Transactional
  public AnnouncementResponse update(Long id, AnnouncementRequest req) {
    Announcement a = require(id);
    if (StringUtils.hasText(req.getTitle())) {
      a.setTitle(req.getTitle().trim());
    }
    if (req.getContent() != null) {
      a.setContent(req.getContent());
    }
    if (req.getPinned() != null) {
      a.setPinned(req.getPinned());
    }
    if (req.getExpiresAt() != null) {
      a.setExpiresAt(req.getExpiresAt());
    }
    announcementMapper.updateById(a);
    return toResponse(a, isRead(id));
  }

  @Transactional
  public void delete(Long id) {
    Announcement a = require(id);
    readMapper.delete(
        new LambdaQueryWrapper<AnnouncementRead>().eq(AnnouncementRead::getAnnouncementId, id));
    announcementMapper.deleteById(id);
    auditService.record("DELETE", "ANNOUNCEMENT", id, "删除公告：" + a.getTitle());
  }

  /** 标记已读（幂等）。 */
  @Transactional
  public void markRead(Long id) {
    require(id);
    Long userId = SecurityUtil.currentUserId();
    Long exists =
        readMapper.selectCount(
            new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, id)
                .eq(AnnouncementRead::getUserId, userId));
    if (exists == null || exists == 0) {
      AnnouncementRead read = new AnnouncementRead();
      read.setAnnouncementId(id);
      read.setUserId(userId);
      read.setReadAt(LocalDateTime.now());
      readMapper.insert(read);
    }
  }

  /** 未读公告数。 */
  public long unreadCount() {
    Long userId = SecurityUtil.currentUserId();
    List<Announcement> all =
        announcementMapper.selectList(
            new LambdaQueryWrapper<Announcement>()
                .and(
                    w ->
                        w.isNull(Announcement::getExpiresAt)
                            .or()
                            .gt(Announcement::getExpiresAt, LocalDateTime.now())));
    if (all.isEmpty()) {
      return 0;
    }
    List<Long> ids = all.stream().map(Announcement::getId).toList();
    Set<Long> read =
        readMapper
            .selectList(
                new LambdaQueryWrapper<AnnouncementRead>()
                    .eq(AnnouncementRead::getUserId, userId)
                    .in(AnnouncementRead::getAnnouncementId, ids))
            .stream()
            .map(AnnouncementRead::getAnnouncementId)
            .collect(Collectors.toSet());
    return ids.stream().filter(i -> !read.contains(i)).count();
  }

  private boolean isRead(Long announcementId) {
    Long count =
        readMapper.selectCount(
            new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, announcementId)
                .eq(AnnouncementRead::getUserId, SecurityUtil.currentUserId()));
    return count != null && count > 0;
  }

  private Announcement require(Long id) {
    Announcement a = announcementMapper.selectById(id);
    if (a == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "公告不存在");
    }
    return a;
  }

  private AnnouncementResponse toResponse(Announcement a, boolean read) {
    AnnouncementResponse r = new AnnouncementResponse();
    r.setId(a.getId());
    r.setTitle(a.getTitle());
    r.setContent(a.getContent());
    r.setPinned(a.getPinned());
    r.setExpiresAt(a.getExpiresAt());
    r.setRead(read);
    r.setCreatedAt(a.getCreatedAt());
    return r;
  }
}
