package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.portal.PortalArticleResponse;
import com.crm.dto.portal.PortalTicketQueryRequest;
import com.crm.dto.portal.PortalTicketRequest;
import com.crm.dto.portal.PortalTicketResponse;
import com.crm.dto.portal.PortalTicketStatusResponse;
import com.crm.dto.ticket.TicketRequest;
import com.crm.entity.Contact;
import com.crm.entity.KnowledgeArticle;
import com.crm.entity.Ticket;
import com.crm.entity.TicketReply;
import com.crm.repository.ContactMapper;
import com.crm.repository.KnowledgeArticleMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.TicketReplyMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 客户自助门户服务（050，FR-P01~P09）：知识库浏览/在线提单/进度查询。 */
@Service
public class CustomerPortalService {

  private final KnowledgeArticleMapper articleMapper;
  private final TicketService ticketService;
  private final ContactMapper contactMapper;
  private final TicketMapper ticketMapper;
  private final TicketReplyMapper replyMapper;

  public CustomerPortalService(
      KnowledgeArticleMapper articleMapper,
      TicketService ticketService,
      ContactMapper contactMapper,
      TicketMapper ticketMapper,
      TicketReplyMapper replyMapper) {
    this.articleMapper = articleMapper;
    this.ticketService = ticketService;
    this.contactMapper = contactMapper;
    this.ticketMapper = ticketMapper;
    this.replyMapper = replyMapper;
  }

  // ===== 知识库门户 =====

  /** 公开文章列表（仅 PUBLISHED，搜索标题/关键词）。 */
  public PageResult<PortalArticleResponse> articles(String keyword, long page, long pageSize) {
    LambdaQueryWrapper<KnowledgeArticle> qw =
        new LambdaQueryWrapper<KnowledgeArticle>()
            .eq(KnowledgeArticle::getStatus, KnowledgeArticleService.STATUS_PUBLISHED);
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(
          w ->
              w.like(KnowledgeArticle::getTitle, kw)
                  .or()
                  .like(KnowledgeArticle::getKeywords, kw));
    }
    qw.orderByDesc(KnowledgeArticle::getId);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<KnowledgeArticle> p =
        articleMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), qw);
    List<PortalArticleResponse> items =
        p.getRecords().stream()
            .map(
                a -> {
                  PortalArticleResponse r = new PortalArticleResponse();
                  r.setId(a.getId());
                  r.setCategory(a.getCategory());
                  r.setTitle(a.getTitle());
                  r.setKeywords(a.getKeywords());
                  r.setUpdatedAt(a.getUpdatedAt());
                  return r;
                })
            .toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  /** 公开文章详情（仅 PUBLISHED）。 */
  public PortalArticleResponse article(Long id) {
    KnowledgeArticle a = articleMapper.selectById(id);
    if (a == null
        || !KnowledgeArticleService.STATUS_PUBLISHED.equals(a.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "文章不可见");
    }
    PortalArticleResponse r = new PortalArticleResponse();
    r.setId(a.getId());
    r.setCategory(a.getCategory());
    r.setTitle(a.getTitle());
    r.setKeywords(a.getKeywords());
    r.setContent(a.getContent());
    r.setUpdatedAt(a.getUpdatedAt());
    return r;
  }

  // ===== 在线提单 =====

  /** 门户提单：手机/邮箱识别客户 → 复用 TicketService 创建。 */
  @Transactional
  public PortalTicketResponse submitTicket(PortalTicketRequest req) {
    if (!StringUtils.hasText(req.getPhone()) && !StringUtils.hasText(req.getEmail())) {
      throw new BusinessException(ErrorCode.PORTAL_CONTACT_REQUIRED);
    }
    Contact contact = resolveContact(req.getPhone(), req.getEmail());
    if (contact == null) {
      throw new BusinessException(ErrorCode.PORTAL_CUSTOMER_NOT_FOUND);
    }
    TicketRequest ticketReq = new TicketRequest();
    ticketReq.setCustomerId(contact.getCustomerId());
    ticketReq.setContactId(contact.getId());
    ticketReq.setTitle(req.getTitle().trim());
    ticketReq.setDescription(req.getDescription());
    ticketReq.setPriority(
        StringUtils.hasText(req.getPriority()) ? req.getPriority().trim() : "MEDIUM");
    var created = ticketService.create(ticketReq);

    PortalTicketResponse resp = new PortalTicketResponse();
    resp.setTicketId(created.getId());
    resp.setStatus(created.getStatus());
    resp.setPriority(created.getPriority());
    resp.setCreatedAt(created.getCreatedAt());
    return resp;
  }

  /** 按手机号或邮箱匹配联系人（任一命中）。 */
  private Contact resolveContact(String phone, String email) {
    if (StringUtils.hasText(phone)) {
      Contact c =
          contactMapper.selectOne(
              new LambdaQueryWrapper<Contact>()
                  .eq(Contact::getPhone, phone.trim())
                  .last("LIMIT 1"));
      if (c != null) {
        return c;
      }
    }
    if (StringUtils.hasText(email)) {
      return contactMapper.selectOne(
          new LambdaQueryWrapper<Contact>()
              .eq(Contact::getEmail, email.trim())
              .last("LIMIT 1"));
    }
    return null;
  }

  // ===== 进度查询 =====

  /** 门户进度查询：工单号 + 手机/邮箱双验证。 */
  public PortalTicketStatusResponse ticketStatus(PortalTicketQueryRequest req) {
    if (req.getTicketId() == null) {
      throw new BusinessException(ErrorCode.PORTAL_TICKET_NOT_FOUND);
    }
    if (!StringUtils.hasText(req.getPhone()) && !StringUtils.hasText(req.getEmail())) {
      throw new BusinessException(ErrorCode.PORTAL_CONTACT_REQUIRED);
    }
    Ticket ticket = ticketMapper.selectById(req.getTicketId());
    if (ticket == null) {
      throw new BusinessException(ErrorCode.PORTAL_TICKET_NOT_FOUND);
    }
    Contact contact = resolveContact(req.getPhone(), req.getEmail());
    if (contact == null || !ticket.getCustomerId().equals(contact.getCustomerId())) {
      throw new BusinessException(ErrorCode.PORTAL_TICKET_NOT_FOUND);
    }

    PortalTicketStatusResponse resp = new PortalTicketStatusResponse();
    resp.setTicketId(ticket.getId());
    resp.setStatus(ticket.getStatus());
    resp.setPriority(ticket.getPriority());
    resp.setSlaStatus(ticket.getSlaStatus());
    resp.setCreatedAt(ticket.getCreatedAt());
    List<TicketReply> replies =
        replyMapper.selectList(
            new LambdaQueryWrapper<TicketReply>()
                .eq(TicketReply::getTicketId, ticket.getId())
                .orderByAsc(TicketReply::getId));
    resp.setReplies(
        replies.stream()
            .map(
                r -> {
                  PortalTicketStatusResponse.ReplyView v =
                      new PortalTicketStatusResponse.ReplyView();
                  v.setContent(r.getContent());
                  v.setCreatedAt(r.getCreatedAt() == null ? LocalDateTime.now() : r.getCreatedAt());
                  return v;
                })
            .toList());
    return resp;
  }
}
