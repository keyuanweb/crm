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
import com.crm.dto.portal.PortalTicketQueryRequest;
import com.crm.dto.portal.PortalTicketRequest;
import com.crm.dto.ticket.TicketResponse;
import com.crm.entity.Contact;
import com.crm.entity.KnowledgeArticle;
import com.crm.entity.Ticket;
import com.crm.repository.ContactMapper;
import com.crm.repository.KnowledgeArticleMapper;
import com.crm.repository.TicketMapper;
import com.crm.repository.TicketReplyMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CustomerPortalService 单元测试（050 T010）：文章可见性/提单识别/双验证。 */
class CustomerPortalServiceTest {

  private KnowledgeArticleMapper articleMapper;
  private TicketService ticketService;
  private ContactMapper contactMapper;
  private TicketMapper ticketMapper;
  private TicketReplyMapper replyMapper;
  private CustomerPortalService service;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, KnowledgeArticle.class);
    TableInfoHelper.initTableInfo(assistant, Contact.class);
    TableInfoHelper.initTableInfo(assistant, Ticket.class);
  }

  @BeforeEach
  void setUp() {
    articleMapper = mock(KnowledgeArticleMapper.class);
    ticketService = mock(TicketService.class);
    contactMapper = mock(ContactMapper.class);
    ticketMapper = mock(TicketMapper.class);
    replyMapper = mock(TicketReplyMapper.class);
    service =
        new CustomerPortalService(
            articleMapper, ticketService, contactMapper, ticketMapper, replyMapper);
  }

  @Test
  @DisplayName("文章详情：草稿不可见 → BAD_REQUEST")
  void draftArticleHidden() {
    KnowledgeArticle a = new KnowledgeArticle();
    a.setId(1L);
    a.setStatus("DRAFT");
    a.setTitle("草稿");
    when(articleMapper.selectById(1L)).thenReturn(a);

    assertThatThrownBy(() -> service.article(1L)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("提单：手机匹配联系人 → 创建工单")
  void submitTicketByPhone() {
    when(contactMapper.selectOne(any())).thenReturn(contact(10L, 5L));
    TicketResponse created = new TicketResponse();
    created.setId(99L);
    created.setStatus("OPEN");
    created.setPriority("MEDIUM");
    created.setCreatedAt(LocalDateTime.now());
    when(ticketService.create(any())).thenReturn(created);

    PortalTicketRequest req = new PortalTicketRequest();
    req.setPhone("13800000000");
    req.setTitle("使用问题");
    var resp = service.submitTicket(req);

    assertThat(resp.getTicketId()).isEqualTo(99L);
    verify(ticketService).create(any());
  }

  @Test
  @DisplayName("提单：手机/邮箱都空 → 422")
  void submitTicketNoContactThrows() {
    PortalTicketRequest req = new PortalTicketRequest();
    req.setTitle("x");

    assertThatThrownBy(() -> service.submitTicket(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PORTAL_CONTACT_REQUIRED);
    verify(ticketService, never()).create(any());
  }

  @Test
  @DisplayName("提单：未匹配客户 → 422")
  void submitTicketCustomerNotFound() {
    when(contactMapper.selectOne(any())).thenReturn(null);

    PortalTicketRequest req = new PortalTicketRequest();
    req.setPhone("13800000000");
    req.setTitle("x");

    assertThatThrownBy(() -> service.submitTicket(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PORTAL_CUSTOMER_NOT_FOUND);
  }

  @Test
  @DisplayName("进度查询：客户不匹配 → 404")
  void ticketStatusMismatch() {
    Ticket t = new Ticket();
    t.setId(1L);
    t.setCustomerId(5L);
    when(ticketMapper.selectById(1L)).thenReturn(t);
    when(contactMapper.selectOne(any())).thenReturn(contact(10L, 999L)); // 客户 999 ≠ 5

    PortalTicketQueryRequest req = new PortalTicketQueryRequest();
    req.setTicketId(1L);
    req.setPhone("13800000000");

    assertThatThrownBy(() -> service.ticketStatus(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PORTAL_TICKET_NOT_FOUND);
  }

  @Test
  @DisplayName("进度查询：匹配成功返回状态与回复")
  void ticketStatusOk() {
    Ticket t = new Ticket();
    t.setId(1L);
    t.setCustomerId(5L);
    t.setStatus("IN_PROGRESS");
    t.setPriority("HIGH");
    t.setSlaStatus("NORMAL");
    t.setCreatedAt(LocalDateTime.now());
    when(ticketMapper.selectById(1L)).thenReturn(t);
    when(contactMapper.selectOne(any())).thenReturn(contact(10L, 5L));
    when(replyMapper.selectList(any())).thenReturn(List.of());

    PortalTicketQueryRequest req = new PortalTicketQueryRequest();
    req.setTicketId(1L);
    req.setPhone("13800000000");

    var resp = service.ticketStatus(req);

    assertThat(resp.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(resp.getSlaStatus()).isEqualTo("NORMAL");
  }

  private Contact contact(Long id, Long customerId) {
    Contact c = new Contact();
    c.setId(id);
    c.setCustomerId(customerId);
    c.setName("张三");
    return c;
  }
}
