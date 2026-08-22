package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.PageResult;
import com.crm.dto.workflow.ExecutionLogResponse;
import com.crm.entity.WorkflowExecutionLog;
import com.crm.entity.WorkflowRule;
import com.crm.repository.WorkflowExecutionLogMapper;
import com.crm.repository.WorkflowRuleMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 工作流执行日志查询服务（013，FR-W07）。 */
@Service
public class WorkflowExecutionLogQueryService {

  private final WorkflowExecutionLogMapper logMapper;
  private final WorkflowRuleMapper ruleMapper;

  public WorkflowExecutionLogQueryService(
      WorkflowExecutionLogMapper logMapper, WorkflowRuleMapper ruleMapper) {
    this.logMapper = logMapper;
    this.ruleMapper = ruleMapper;
  }

  public PageResult<ExecutionLogResponse> page(
      Long ruleId, String eventType, Boolean success, long page, long pageSize) {
    LambdaQueryWrapper<WorkflowExecutionLog> qw = new LambdaQueryWrapper<>();
    if (ruleId != null) {
      qw.eq(WorkflowExecutionLog::getRuleId, ruleId);
    }
    if (StringUtils.hasText(eventType)) {
      qw.eq(WorkflowExecutionLog::getEventType, eventType.trim());
    }
    if (success != null) {
      qw.eq(WorkflowExecutionLog::getSuccess, success);
    }
    qw.orderByDesc(WorkflowExecutionLog::getId);
    Page<WorkflowExecutionLog> p = logMapper.selectPage(new Page<>(page, pageSize), qw);
    List<WorkflowExecutionLog> records = p.getRecords();
    // 批量装配规则名
    List<Long> ruleIds = records.stream().map(WorkflowExecutionLog::getRuleId).distinct().toList();
    Map<Long, String> ruleNames =
        ruleIds.isEmpty()
            ? Map.of()
            : ruleMapper.selectBatchIds(ruleIds).stream()
                .collect(Collectors.toMap(WorkflowRule::getId, WorkflowRule::getName, (a, b) -> a));
    List<ExecutionLogResponse> items =
        records.stream()
            .map(
                l -> {
                  ExecutionLogResponse resp = new ExecutionLogResponse();
                  resp.setId(l.getId());
                  resp.setRuleId(l.getRuleId());
                  resp.setRuleName(ruleNames.get(l.getRuleId()));
                  resp.setEventType(l.getEventType());
                  resp.setEntityType(l.getEntityType());
                  resp.setEntityId(l.getEntityId());
                  resp.setMatched(l.getMatched());
                  resp.setActionResult(l.getActionResult());
                  resp.setSuccess(l.getSuccess());
                  resp.setErrorMessage(l.getErrorMessage());
                  resp.setCreatedAt(l.getCreatedAt());
                  return resp;
                })
            .toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }
}
