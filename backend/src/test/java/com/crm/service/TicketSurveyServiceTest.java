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
import com.crm.dto.survey.SurveyRequest;
import com.crm.entity.Ticket;
import com.crm.entity.TicketSurvey;
import com.crm.repository.TicketMapper;
import com.crm.repository.TicketSurveyMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** TicketSurveyService 单元测试（051 T008）：评分/状态/唯一/统计。 */
class TicketSurveyServiceTest {

  private TicketSurveyMapper surveyMapper;
  private TicketMapper ticketMapper;
  private AuditService auditService;
  private TicketSurveyService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, TicketSurvey.class);
    TableInfoHelper.initTableInfo(assistant, Ticket.class);
  }

  @BeforeEach
  void setUp() {
    surveyMapper = mock(TicketSurveyMapper.class);
    ticketMapper = mock(TicketMapper.class);
    auditService = mock(AuditService.class);
    service = new TicketSurveyService(surveyMapper, ticketMapper, auditService);
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

  private Ticket closedTicket(Long id) {
    Ticket t = new Ticket();
    t.setId(id);
    t.setStatus("CLOSED");
    return t;
  }

  private SurveyRequest request(int rating) {
    SurveyRequest req = new SurveyRequest();
    req.setRating(rating);
    req.setComment("满意");
    return req;
  }

  private TicketSurvey survey(int rating) {
    TicketSurvey s = new TicketSurvey();
    s.setId(1L);
    s.setTicketId(1L);
    s.setRating(rating);
    s.setCreatedAt(LocalDateTime.now());
    return s;
  }

  @Test
  @DisplayName("提交评分：CLOSED 工单成功 + 审计")
  void submitSucceeds() {
    when(ticketMapper.selectById(1L)).thenReturn(closedTicket(1L));
    when(surveyMapper.selectCount(any())).thenReturn(0L);
    when(surveyMapper.insert(any(TicketSurvey.class)))
        .thenAnswer(
            invocation -> {
              TicketSurvey s = invocation.getArgument(0);
              s.setId(10L);
              return 1;
            });

    var resp = service.submit(1L, request(5));

    assertThat(resp.getId()).isEqualTo(10L);
    assertThat(resp.getRating()).isEqualTo(5);
    verify(auditService).record("SURVEY", "TICKET", 1L, "工单满意度评分：5 分");
  }

  @Test
  @DisplayName("提交评分：未关闭工单 → 422")
  void submitOpenTicketThrows() {
    Ticket t = closedTicket(1L);
    t.setStatus("RESOLVED");
    when(ticketMapper.selectById(1L)).thenReturn(t);

    assertThatThrownBy(() -> service.submit(1L, request(5)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SURVEY_STATE_INVALID);
    verify(surveyMapper, never()).insert(any(TicketSurvey.class));
  }

  @Test
  @DisplayName("重复评分 → 409")
  void submitDuplicateThrows() {
    when(ticketMapper.selectById(1L)).thenReturn(closedTicket(1L));
    when(surveyMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.submit(1L, request(5)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SURVEY_ALREADY_SUBMITTED);
  }

  @Test
  @DisplayName("统计：CSAT 均值与 NPS 分布计算正确")
  void statsCalculates() {
    when(surveyMapper.selectList(any()))
        .thenReturn(List.of(survey(5), survey(5), survey(4), survey(2)));

    var stats = service.stats(null, null);

    assertThat(stats.getSampleCount()).isEqualTo(4);
    assertThat(stats.getCsatAverage()).isEqualTo(4.0); // (5+5+4+2)/4 = 4.0
    assertThat(stats.getPromoter().getCount()).isEqualTo(2); // rating=5
    assertThat(stats.getPassive().getCount()).isEqualTo(1); // rating=4
    assertThat(stats.getDetractor().getCount()).isEqualTo(1); // rating<=3
    assertThat(stats.getNpsScore()).isEqualTo(25); // 50% - 25%
  }
}
