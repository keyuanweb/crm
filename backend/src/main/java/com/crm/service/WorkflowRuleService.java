package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.workflow.WorkflowRuleRequest;
import com.crm.dto.workflow.WorkflowRuleResponse;
import com.crm.entity.WorkflowRule;
import com.crm.repository.WorkflowRuleMapper;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 工作流规则服务（013，FR-W01~W03）：CRUD/启停/动作校验。 */
@Service
public class WorkflowRuleService {

  private static final Logger log = LoggerFactory.getLogger(WorkflowRuleService.class);

  private final WorkflowRuleMapper ruleMapper;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public WorkflowRuleService(
      WorkflowRuleMapper ruleMapper, AuditService auditService, ObjectMapper objectMapper) {
    this.ruleMapper = ruleMapper;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  public PageResult<WorkflowRuleResponse> page(
      String keyword, String eventType, Boolean enabled, long page, long pageSize) {
    LambdaQueryWrapper<WorkflowRule> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.like(WorkflowRule::getName, keyword.trim());
    }
    if (StringUtils.hasText(eventType)) {
      qw.eq(WorkflowRule::getEventType, eventType.trim());
    }
    if (enabled != null) {
      qw.eq(WorkflowRule::getEnabled, enabled);
    }
    qw.orderByDesc(WorkflowRule::getId);
    Page<WorkflowRule> p = ruleMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public WorkflowRuleResponse create(WorkflowRuleRequest req) {
    validateAction(req);
    WorkflowRule rule = new WorkflowRule();
    apply(req, rule);
    rule.setEnabled(req.getEnabled() == null || req.getEnabled());
    rule.setCreatedBy(SecurityUtil.currentUserId());
    ruleMapper.insert(rule);
    auditService.record("CREATE", "WORKFLOW_RULE", rule.getId(), "创建规则：" + rule.getName());
    return toResponse(rule);
  }

  @Transactional
  public WorkflowRuleResponse update(Long id, WorkflowRuleRequest req) {
    WorkflowRule existing = require(id);
    validateAction(req);
    apply(req, existing);
    existing.setEnabled(req.getEnabled() == null ? existing.getEnabled() : req.getEnabled());
    existing.setVersion(req.getVersion());
    int rows = ruleMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "WORKFLOW_RULE", id, "编辑规则：" + existing.getName());
    return toResponse(ruleMapper.selectById(id));
  }

  @Transactional
  public WorkflowRuleResponse toggle(Long id) {
    WorkflowRule rule = require(id);
    rule.setEnabled(!Boolean.TRUE.equals(rule.getEnabled()));
    ruleMapper.updateById(rule);
    auditService.record("TOGGLE", "WORKFLOW_RULE", id, "规则启停：" + rule.getName());
    return toResponse(ruleMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    WorkflowRule rule = require(id);
    ruleMapper.deleteById(id);
    auditService.record("DELETE", "WORKFLOW_RULE", id, "删除规则：" + rule.getName());
  }

  /** 查询某事件全部启用规则（供 WorkflowEngine）。 */
  public List<WorkflowRule> enabledRulesFor(String eventType) {
    return ruleMapper.selectList(
        new LambdaQueryWrapper<WorkflowRule>()
            .eq(WorkflowRule::getEventType, eventType)
            .eq(WorkflowRule::getEnabled, true));
  }

  public WorkflowRule require(Long id) {
    WorkflowRule rule = ruleMapper.selectById(id);
    if (rule == null) {
      throw new BusinessException(ErrorCode.WORKFLOW_RULE_NOT_FOUND);
    }
    return rule;
  }

  /** 动作配置按类型校验：ASSIGN 需 targetUserId；CREATE_TASK 需 titleTemplate。 */
  private void validateAction(WorkflowRuleRequest req) {
    Map<String, Object> action = req.getAction();
    if (action == null || action.isEmpty()) {
      throw new BusinessException(ErrorCode.WORKFLOW_INVALID_ACTION);
    }
    switch (req.getActionType()) {
      case "ASSIGN" -> {
        if (action.get("targetUserId") == null) {
          throw new BusinessException(ErrorCode.WORKFLOW_INVALID_ACTION);
        }
      }
      case "CREATE_TASK" -> {
        if (action.get("titleTemplate") == null) {
          throw new BusinessException(ErrorCode.WORKFLOW_INVALID_ACTION);
        }
      }
      case "NOTIFY" -> {
        if (action.get("message") == null) {
          throw new BusinessException(ErrorCode.WORKFLOW_INVALID_ACTION);
        }
      }
      default -> throw new BusinessException(ErrorCode.WORKFLOW_INVALID_ACTION);
    }
  }

  private void apply(WorkflowRuleRequest req, WorkflowRule rule) {
    rule.setName(req.getName().trim());
    rule.setEventType(req.getEventType().trim());
    rule.setConditionJson(writeJson(req.getCondition()));
    rule.setActionType(req.getActionType().trim());
    rule.setActionJson(writeJson(req.getAction()));
  }

  private String writeJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      log.warn("Failed to serialize workflow JSON: {}", ex.getMessage());
      throw new BusinessException(ErrorCode.WORKFLOW_INVALID_ACTION);
    }
  }

  private WorkflowRuleResponse toResponse(WorkflowRule rule) {
    WorkflowRuleResponse resp = new WorkflowRuleResponse();
    resp.setId(rule.getId());
    resp.setName(rule.getName());
    resp.setEventType(rule.getEventType());
    resp.setCondition(
        readMap(rule.getConditionJson(), new TypeReference<Map<String, String>>() {}));
    resp.setActionType(rule.getActionType());
    resp.setAction(readMap(rule.getActionJson(), new TypeReference<Map<String, Object>>() {}));
    resp.setEnabled(rule.getEnabled());
    resp.setVersion(rule.getVersion());
    resp.setCreatedAt(rule.getCreatedAt());
    return resp;
  }

  private <T> T readMap(String json, TypeReference<T> type) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception ex) {
      log.warn("Failed to parse workflow JSON: {}", ex.getMessage());
      return null;
    }
  }
}
