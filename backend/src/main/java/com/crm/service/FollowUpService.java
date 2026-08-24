package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.followup.FollowUpRequest;
import com.crm.dto.followup.FollowUpResponse;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.Lead;
import com.crm.entity.Opportunity;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 跟进记录服务（FR-015，归属/权限规则见 contracts/follow-ups.md）。 */
@Service
public class FollowUpService {

  private final FollowUpMapper followUpMapper;
  private final CustomerMapper customerMapper;
  private final OpportunityMapper opportunityMapper;
  private final UserMapper userMapper;
  private final LeadMapper leadMapper;
  private final DashboardStatsService dashboardStatsService;
  private final TaskService taskService;
  private final WorkflowEventPublisher workflowEventPublisher;
  private final LeadScoreService leadScoreService;

  public FollowUpService(
      FollowUpMapper followUpMapper,
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper,
      UserMapper userMapper,
      LeadMapper leadMapper,
      DashboardStatsService dashboardStatsService,
      TaskService taskService,
      WorkflowEventPublisher workflowEventPublisher,
      LeadScoreService leadScoreService) {
    this.followUpMapper = followUpMapper;
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
    this.userMapper = userMapper;
    this.leadMapper = leadMapper;
    this.dashboardStatsService = dashboardStatsService;
    this.taskService = taskService;
    this.workflowEventPublisher = workflowEventPublisher;
    this.leadScoreService = leadScoreService;
  }

  public PageResult<FollowUpResponse> page(
      Long customerId, Long leadId, Long opportunityId, long page, long pageSize) {
    LambdaQueryWrapper<FollowUp> qw = new LambdaQueryWrapper<>();
    if (customerId != null) {
      qw.eq(FollowUp::getCustomerId, customerId);
    }
    if (leadId != null) {
      qw.eq(FollowUp::getLeadId, leadId);
    }
    if (opportunityId != null) {
      qw.eq(FollowUp::getOpportunityId, opportunityId);
    }
    qw.orderByDesc(FollowUp::getCreatedAt);
    Page<FollowUp> p = followUpMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(toResponses(p.getRecords()), p.getTotal(), page, pageSize);
  }

  @Transactional
  public FollowUpResponse create(FollowUpRequest req) {
    validateLinkage(req.getCustomerId(), req.getLeadId(), req.getOpportunityId());
    FollowUp followUp = new FollowUp();
    followUp.setCustomerId(req.getCustomerId());
    followUp.setLeadId(req.getLeadId());
    followUp.setOpportunityId(req.getOpportunityId());
    followUp.setMethod(req.getMethod().trim().toUpperCase());
    followUp.setContent(req.getContent());
    followUp.setNextFollowUpAt(req.getNextFollowUpAt());
    followUp.setFollowUpBy(SecurityUtil.currentUserId());
    followUpMapper.insert(followUp);
    dashboardStatsService.evict();
    // 019：线索跟进后重算评分（跟进活跃度维度）
    if (req.getLeadId() != null) {
      Lead linkedLead = leadMapper.selectById(req.getLeadId());
      if (linkedLead != null) {
        leadScoreService.scoreAndUpdate(linkedLead);
        leadMapper.updateById(linkedLead);
      }
    }
    maybeCreateFollowUpTask(req);
    // 013：跟进创建触发工作流
    workflowEventPublisher.followUpCreated(
        followUp.getId(), java.util.Map.of("method", followUp.getMethod(), "name", "跟进"));
    return toResponse(followUp);
  }

  /** 010：勾选 createTask 且 nextFollowUpAt 非空时自动创建跟进任务。 */
  private void maybeCreateFollowUpTask(FollowUpRequest req) {
    if (!Boolean.TRUE.equals(req.getCreateTask()) || req.getNextFollowUpAt() == null) {
      return;
    }
    com.crm.dto.task.TaskRequest taskReq = new com.crm.dto.task.TaskRequest();
    String linkedName = null;
    String linkedType = null;
    Long linkedId = null;
    if (req.getCustomerId() != null) {
      Customer customer = customerMapper.selectById(req.getCustomerId());
      linkedName = customer == null ? null : customer.getName();
      linkedType = "CUSTOMER";
      linkedId = req.getCustomerId();
    } else if (req.getLeadId() != null) {
      Lead lead = leadMapper.selectById(req.getLeadId());
      linkedName = lead == null ? null : lead.getName();
      linkedType = "LEAD";
      linkedId = req.getLeadId();
    }
    taskReq.setTitle("跟进：" + (linkedName == null ? "客户" : linkedName));
    taskReq.setDueAt(req.getNextFollowUpAt());
    taskReq.setPriority("MEDIUM");
    taskReq.setLinkedType(linkedType);
    taskReq.setLinkedId(linkedId);
    taskService.create(taskReq);
  }

  @Transactional
  public FollowUpResponse update(Long id, FollowUpRequest req) {
    FollowUp existing = require(id);
    Long currentUserId = SecurityUtil.currentUserId();
    if (currentUserId == null
        || (!currentUserId.equals(existing.getFollowUpBy()) && !isAdmin(currentUserId))) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    validateLinkage(req.getCustomerId(), req.getLeadId(), req.getOpportunityId());
    existing.setCustomerId(req.getCustomerId());
    existing.setLeadId(req.getLeadId());
    existing.setOpportunityId(req.getOpportunityId());
    existing.setMethod(req.getMethod().trim().toUpperCase());
    existing.setContent(req.getContent());
    existing.setNextFollowUpAt(req.getNextFollowUpAt());
    existing.setVersion(req.getVersion());
    int rows = followUpMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    dashboardStatsService.evict();
    return toResponse(followUpMapper.selectById(id));
  }

  public FollowUp require(Long id) {
    FollowUp followUp = followUpMapper.selectById(id);
    if (followUp == null) {
      throw new BusinessException(ErrorCode.FOLLOW_UP_NOT_FOUND);
    }
    return followUp;
  }

  private void validateLinkage(Long customerId, Long leadId, Long opportunityId) {
    if (customerId == null && leadId == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "必须关联客户或线索");
    }
    if (customerId != null && leadId != null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "客户和线索只能关联一个");
    }
    if (customerId != null) {
      Customer customer = customerMapper.selectById(customerId);
      if (customer == null) {
        throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
      }
      if (opportunityId != null) {
        Opportunity opportunity = opportunityMapper.selectById(opportunityId);
        if (opportunity == null) {
          throw new BusinessException(ErrorCode.OPPORTUNITY_NOT_FOUND);
        }
        if (!opportunity.getCustomerId().equals(customerId)) {
          throw new BusinessException(ErrorCode.OPPORTUNITY_CUSTOMER_MISMATCH);
        }
      }
    } else {
      Lead lead = leadMapper.selectById(leadId);
      if (lead == null) {
        throw new BusinessException(ErrorCode.LEAD_NOT_FOUND);
      }
    }
  }

  private boolean isAdmin(Long userId) {
    User user = userMapper.selectById(userId);
    return user != null && "ADMIN".equals(user.getRole());
  }

  /** 批量装配：一次查询全部跟进人显示名，避免逐行查询（N+1，章程原则五）。 */
  private List<FollowUpResponse> toResponses(List<FollowUp> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    List<Long> userIds =
        list.stream().map(FollowUp::getFollowUpBy).filter(Objects::nonNull).distinct().toList();
    Map<Long, String> userNames =
        userIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    return list.stream()
        .map(
            followUp -> {
              FollowUpResponse resp = new FollowUpResponse();
              copyToResponse(followUp, resp);
              resp.setFollowUpByName(userNames.get(followUp.getFollowUpBy()));
              return resp;
            })
        .toList();
  }

  private FollowUpResponse toResponse(FollowUp followUp) {
    FollowUpResponse resp = new FollowUpResponse();
    copyToResponse(followUp, resp);
    if (followUp.getFollowUpBy() != null) {
      User user = userMapper.selectById(followUp.getFollowUpBy());
      resp.setFollowUpByName(user == null ? null : user.getDisplayName());
    }
    return resp;
  }

  private void copyToResponse(FollowUp followUp, FollowUpResponse resp) {
    resp.setId(followUp.getId());
    resp.setCustomerId(followUp.getCustomerId());
    resp.setLeadId(followUp.getLeadId());
    resp.setOpportunityId(followUp.getOpportunityId());
    resp.setMethod(followUp.getMethod());
    resp.setContent(followUp.getContent());
    resp.setNextFollowUpAt(followUp.getNextFollowUpAt());
    resp.setFollowUpBy(followUp.getFollowUpBy());
    resp.setVersion(followUp.getVersion());
    resp.setCreatedAt(followUp.getCreatedAt());
  }
}
