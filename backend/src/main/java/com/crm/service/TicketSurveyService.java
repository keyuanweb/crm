package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.survey.SurveyRequest;
import com.crm.dto.survey.SurveyResponse;
import com.crm.dto.survey.SurveyStatsResponse;
import com.crm.entity.Ticket;
import com.crm.entity.TicketSurvey;
import com.crm.repository.TicketMapper;
import com.crm.repository.TicketSurveyMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 工单满意度服务（051，FR-S01~S08）：评分提交/查询/统计。 */
@Service
public class TicketSurveyService {

  private final TicketSurveyMapper surveyMapper;
  private final TicketMapper ticketMapper;
  private final AuditService auditService;

  public TicketSurveyService(
      TicketSurveyMapper surveyMapper, TicketMapper ticketMapper, AuditService auditService) {
    this.surveyMapper = surveyMapper;
    this.ticketMapper = ticketMapper;
    this.auditService = auditService;
  }

  /** 提交工单满意度评分（仅 CLOSED；一张工单一次）。 */
  @Transactional
  public SurveyResponse submit(Long ticketId, SurveyRequest req) {
    Ticket ticket = ticketMapper.selectById(ticketId);
    if (ticket == null) {
      throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
    }
    if (!TicketService.STATUS_CLOSED.equals(ticket.getStatus())) {
      throw new BusinessException(ErrorCode.SURVEY_STATE_INVALID);
    }
    Long exists =
        surveyMapper.selectCount(
            new LambdaQueryWrapper<TicketSurvey>().eq(TicketSurvey::getTicketId, ticketId));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.SURVEY_ALREADY_SUBMITTED);
    }
    TicketSurvey survey = new TicketSurvey();
    survey.setTicketId(ticketId);
    survey.setRating(req.getRating());
    survey.setComment(req.getComment());
    survey.setCreatedBy(SecurityUtil.currentUserId());
    survey.setCreatedAt(LocalDateTime.now());
    surveyMapper.insert(survey);
    auditService.record(
        "SURVEY", "TICKET", ticketId, "工单满意度评分：" + req.getRating() + " 分");
    return toResponse(survey);
  }

  /** 查询工单评分（无则 null）。 */
  public SurveyResponse findByTicket(Long ticketId) {
    TicketSurvey survey =
        surveyMapper.selectOne(
            new LambdaQueryWrapper<TicketSurvey>().eq(TicketSurvey::getTicketId, ticketId));
    return survey == null ? null : toResponse(survey);
  }

  /** 满意度统计（CSAT 均值 + NPS 分布，可选时间范围）。 */
  public SurveyStatsResponse stats(LocalDateTime from, LocalDateTime to) {
    LambdaQueryWrapper<TicketSurvey> qw = new LambdaQueryWrapper<>();
    if (from != null) {
      qw.ge(TicketSurvey::getCreatedAt, from);
    }
    if (to != null) {
      qw.le(TicketSurvey::getCreatedAt, to);
    }
    List<TicketSurvey> all = surveyMapper.selectList(qw);
    long total = all.size();
    long promoter = all.stream().filter(s -> s.getRating() != null && s.getRating() == 5).count();
    long passive = all.stream().filter(s -> s.getRating() != null && s.getRating() == 4).count();
    long detractor =
        all.stream().filter(s -> s.getRating() != null && s.getRating() <= 3).count();
    double avg =
        total == 0
            ? 0
            : all.stream().mapToInt(s -> s.getRating() == null ? 0 : s.getRating()).average()
                .orElse(0);

    SurveyStatsResponse resp = new SurveyStatsResponse();
    resp.setSampleCount(total);
    resp.setCsatAverage(Math.round(avg * 10) / 10.0);
    resp.setPromoter(bucket(promoter, total));
    resp.setPassive(bucket(passive, total));
    resp.setDetractor(bucket(detractor, total));
    resp.setNpsScore(total == 0 ? 0 : (int) Math.round(promoter * 100.0 / total - detractor * 100.0 / total));
    return resp;
  }

  private SurveyStatsResponse.Bucket bucket(long count, long total) {
    SurveyStatsResponse.Bucket b = new SurveyStatsResponse.Bucket();
    b.setCount(count);
    b.setPercent(total == 0 ? 0 : Math.round(count * 1000.0 / total) / 10.0);
    return b;
  }

  private SurveyResponse toResponse(TicketSurvey survey) {
    SurveyResponse resp = new SurveyResponse();
    resp.setId(survey.getId());
    resp.setTicketId(survey.getTicketId());
    resp.setRating(survey.getRating());
    resp.setComment(survey.getComment());
    resp.setCreatedBy(survey.getCreatedBy());
    resp.setCreatedAt(survey.getCreatedAt());
    return resp;
  }
}
