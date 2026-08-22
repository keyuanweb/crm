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
import com.crm.entity.Opportunity;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 跟进记录服务（FR-015，归属/权限规则见 contracts/follow-ups.md）。 */
@Service
public class FollowUpService {

  private final FollowUpMapper followUpMapper;
  private final CustomerMapper customerMapper;
  private final OpportunityMapper opportunityMapper;
  private final UserMapper userMapper;

  public FollowUpService(
      FollowUpMapper followUpMapper,
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper,
      UserMapper userMapper) {
    this.followUpMapper = followUpMapper;
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
    this.userMapper = userMapper;
  }

  public PageResult<FollowUpResponse> page(
      Long customerId, Long opportunityId, long page, long pageSize) {
    LambdaQueryWrapper<FollowUp> qw = new LambdaQueryWrapper<>();
    if (customerId != null) {
      qw.eq(FollowUp::getCustomerId, customerId);
    }
    if (opportunityId != null) {
      qw.eq(FollowUp::getOpportunityId, opportunityId);
    }
    qw.orderByDesc(FollowUp::getCreatedAt);
    Page<FollowUp> p = followUpMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public FollowUpResponse create(FollowUpRequest req) {
    validateLinkage(req.getCustomerId(), req.getOpportunityId());
    FollowUp followUp = new FollowUp();
    followUp.setCustomerId(req.getCustomerId());
    followUp.setOpportunityId(req.getOpportunityId());
    followUp.setMethod(req.getMethod().trim().toUpperCase());
    followUp.setContent(req.getContent());
    followUp.setNextFollowUpAt(req.getNextFollowUpAt());
    followUp.setFollowUpBy(SecurityUtil.currentUserId());
    followUpMapper.insert(followUp);
    return toResponse(followUp);
  }

  @Transactional
  public FollowUpResponse update(Long id, FollowUpRequest req) {
    FollowUp existing = require(id);
    Long currentUserId = SecurityUtil.currentUserId();
    if (currentUserId == null
        || (!currentUserId.equals(existing.getFollowUpBy()) && !isAdmin(currentUserId))) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    validateLinkage(req.getCustomerId(), req.getOpportunityId());
    existing.setCustomerId(req.getCustomerId());
    existing.setOpportunityId(req.getOpportunityId());
    existing.setMethod(req.getMethod().trim().toUpperCase());
    existing.setContent(req.getContent());
    existing.setNextFollowUpAt(req.getNextFollowUpAt());
    existing.setVersion(req.getVersion());
    int rows = followUpMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    return toResponse(followUpMapper.selectById(id));
  }

  public FollowUp require(Long id) {
    FollowUp followUp = followUpMapper.selectById(id);
    if (followUp == null) {
      throw new BusinessException(ErrorCode.FOLLOW_UP_NOT_FOUND);
    }
    return followUp;
  }

  private void validateLinkage(Long customerId, Long opportunityId) {
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
  }

  private boolean isAdmin(Long userId) {
    User user = userMapper.selectById(userId);
    return user != null && "ADMIN".equals(user.getRole());
  }

  private FollowUpResponse toResponse(FollowUp followUp) {
    FollowUpResponse resp = new FollowUpResponse();
    resp.setId(followUp.getId());
    resp.setCustomerId(followUp.getCustomerId());
    resp.setOpportunityId(followUp.getOpportunityId());
    resp.setMethod(followUp.getMethod());
    resp.setContent(followUp.getContent());
    resp.setNextFollowUpAt(followUp.getNextFollowUpAt());
    resp.setFollowUpBy(followUp.getFollowUpBy());
    resp.setVersion(followUp.getVersion());
    resp.setCreatedAt(followUp.getCreatedAt());
    if (followUp.getFollowUpBy() != null) {
      User user = userMapper.selectById(followUp.getFollowUpBy());
      resp.setFollowUpByName(user == null ? null : user.getDisplayName());
    }
    return resp;
  }
}
