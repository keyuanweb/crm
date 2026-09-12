/** 数据保留策略 Service 实现（080-data-retention）。 */
package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.DataRetentionExecutionResponse;
import com.crm.dto.DataRetentionPolicyRequest;
import com.crm.dto.DataRetentionPolicyResponse;
import com.crm.entity.*;
import com.crm.model.entity.DataRetentionExecution;
import com.crm.model.entity.DataRetentionPolicy;
import com.crm.repository.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DataRetentionPolicyServiceImpl implements DataRetentionPolicyService {

  private static final Logger log = LoggerFactory.getLogger(DataRetentionPolicyServiceImpl.class);

  private final DataRetentionPolicyRepository policyRepository;
  private final DataRetentionExecutionRepository executionRepository;
  private final AuditService auditService;
  private final CustomerMapper customerMapper;
  private final LeadMapper leadMapper;
  private final ContactMapper contactMapper;
  private final FollowUpMapper followUpMapper;
  private final OpportunityMapper opportunityMapper;
  private final ContractMapper contractMapper;
  private final TicketMapper ticketMapper;
  private final TaskItemMapper taskItemMapper;
  private final WorkflowExecutionLogMapper workflowExecutionLogMapper;

  public DataRetentionPolicyServiceImpl(
      DataRetentionPolicyRepository policyRepository,
      DataRetentionExecutionRepository executionRepository,
      AuditService auditService,
      CustomerMapper customerMapper,
      LeadMapper leadMapper,
      ContactMapper contactMapper,
      FollowUpMapper followUpMapper,
      OpportunityMapper opportunityMapper,
      ContractMapper contractMapper,
      TicketMapper ticketMapper,
      TaskItemMapper taskItemMapper,
      WorkflowExecutionLogMapper workflowExecutionLogMapper) {
    this.policyRepository = policyRepository;
    this.executionRepository = executionRepository;
    this.auditService = auditService;
    this.customerMapper = customerMapper;
    this.leadMapper = leadMapper;
    this.contactMapper = contactMapper;
    this.followUpMapper = followUpMapper;
    this.opportunityMapper = opportunityMapper;
    this.contractMapper = contractMapper;
    this.ticketMapper = ticketMapper;
    this.taskItemMapper = taskItemMapper;
    this.workflowExecutionLogMapper = workflowExecutionLogMapper;
  }

  @Override
  public DataRetentionPolicyResponse createPolicy(DataRetentionPolicyRequest request) {
    DataRetentionPolicy entity = new DataRetentionPolicy();
    entity.setEntityType(request.getEntityType());
    entity.setRetentionDays(request.getRetentionDays());
    entity.setActionType(request.getActionType());
    entity.setStatus("ACTIVE");
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());

    policyRepository.insert(entity);
    auditService.record(
        "CREATE",
        "DATA_RETENTION_POLICY",
        entity.getId(),
        "Create policy: entityType="
            + entity.getEntityType()
            + ", retentionDays="
            + entity.getRetentionDays());
    log.info(
        "Created data retention policy: id={}, entityType={}",
        entity.getId(),
        entity.getEntityType());
    return toResponse(entity);
  }

  @Override
  public List<DataRetentionPolicyResponse> getAllPolicies() {
    List<DataRetentionPolicy> entities =
        policyRepository.selectList(
            new LambdaQueryWrapper<DataRetentionPolicy>()
                .orderByDesc(DataRetentionPolicy::getCreatedAt));
    List<DataRetentionPolicyResponse> responses = new ArrayList<>();
    for (DataRetentionPolicy entity : entities) {
      responses.add(toResponse(entity));
    }
    return responses;
  }

  @Override
  public DataRetentionPolicyResponse getPolicy(Long id) {
    DataRetentionPolicy entity = policyRepository.selectById(id);
    return entity != null ? toResponse(entity) : null;
  }

  @Override
  public DataRetentionPolicyResponse updatePolicy(Long id, DataRetentionPolicyRequest request) {
    DataRetentionPolicy entity = policyRepository.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("Policy not found: " + id);
    }
    entity.setEntityType(request.getEntityType());
    entity.setRetentionDays(request.getRetentionDays());
    entity.setActionType(request.getActionType());
    entity.setUpdatedAt(LocalDateTime.now());
    policyRepository.updateById(entity);
    auditService.record(
        "UPDATE",
        "DATA_RETENTION_POLICY",
        id,
        "Update policy: retentionDays=" + entity.getRetentionDays());
    log.info("Updated data retention policy: id={}", id);
    return toResponse(entity);
  }

  @Override
  public void deletePolicy(Long id) {
    DataRetentionPolicy entity = policyRepository.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("Policy not found: " + id);
    }
    policyRepository.deleteById(id);
    auditService.record("DELETE", "DATA_RETENTION_POLICY", id, "Delete policy");
    log.info("Deleted data retention policy: id={}", id);
  }

  @Override
  public List<DataRetentionExecutionResponse> getExecutions(Long policyId) {
    List<DataRetentionExecution> entities =
        executionRepository.findByPolicyIdOrderByExecutedAtDesc(policyId);
    List<DataRetentionExecutionResponse> responses = new ArrayList<>();
    for (DataRetentionExecution entity : entities) {
      responses.add(toExecutionResponse(entity));
    }
    return responses;
  }

  @Override
  public void executeArchival() {
    List<DataRetentionPolicy> activePolicies =
        policyRepository.selectList(
            new LambdaQueryWrapper<DataRetentionPolicy>()
                .eq(DataRetentionPolicy::getStatus, "ACTIVE"));

    for (DataRetentionPolicy policy : activePolicies) {
      DataRetentionExecution execution = null;
      try {
        execution = new DataRetentionExecution();
        execution.setPolicyId(policy.getId());
        execution.setExecutedAt(LocalDateTime.now());
        execution.setStatus("SUCCESS");
        execution.setCreatedAt(LocalDateTime.now());

        int processedCount = processPolicy(policy);
        execution.setProcessedCount(processedCount);

        executionRepository.insert(execution);
        log.info(
            "Executed archival for policy: id={}, entityType={}, processed={}",
            policy.getId(),
            policy.getEntityType(),
            processedCount);
      } catch (Exception e) {
        log.error("Failed to execute archival for policy: id={}", policy.getId(), e);
        if (execution != null) {
          execution.setStatus("FAILED");
          execution.setErrorMessage(e.getMessage());
          executionRepository.insert(execution);
        }
      }
    }
  }

  /** 处理单个策略：根据 entityType 查询到期数据并执行归档/删除操作。 */
  private int processPolicy(DataRetentionPolicy policy) {
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(policy.getRetentionDays());
    String entityType = policy.getEntityType();
    int processedCount = 0;

    switch (entityType.toUpperCase()) {
      case "CUSTOMER":
        processedCount += archiveExpiredCustomers(cutoffDate);
        break;
      case "LEAD":
        processedCount += archiveExpiredLeads(cutoffDate);
        break;
      case "CONTACT":
        processedCount += archiveExpiredContacts(cutoffDate);
        break;
      case "FOLLOW_UP":
        processedCount += archiveExpiredFollowUps(cutoffDate);
        break;
      case "OPPORTUNITY":
        processedCount += archiveExpiredOpportunities(cutoffDate);
        break;
      case "CONTRACT":
        processedCount += archiveExpiredContracts(cutoffDate);
        break;
      case "TICKET":
        processedCount += archiveExpiredTickets(cutoffDate);
        break;
      case "TASK":
        processedCount += archiveExpiredTasks(cutoffDate);
        break;
      case "WORKFLOW_LOG":
        processedCount += archiveExpiredWorkflowLogs(cutoffDate);
        break;
      default:
        log.warn("Unsupported entity type for archival: {}", entityType);
    }

    return processedCount;
  }

  /** 归档过期客户。 */
  private int archiveExpiredCustomers(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<Customer> qw =
        new LambdaQueryWrapper<Customer>().lt(Customer::getCreatedAt, cutoffDate);
    List<Customer> expired = customerMapper.selectList(qw);
    int count = expired.size();
    for (Customer c : expired) {
      c.setDeleted(1);
      customerMapper.updateById(c);
    }
    log.info("Archived {} expired customers before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期线索。 */
  private int archiveExpiredLeads(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<Lead> qw = new LambdaQueryWrapper<Lead>().lt(Lead::getCreatedAt, cutoffDate);
    List<Lead> expired = leadMapper.selectList(qw);
    int count = expired.size();
    for (Lead l : expired) {
      l.setDeleted(1);
      leadMapper.updateById(l);
    }
    log.info("Archived {} expired leads before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期联系人。 */
  private int archiveExpiredContacts(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<Contact> qw =
        new LambdaQueryWrapper<Contact>().lt(Contact::getCreatedAt, cutoffDate);
    List<Contact> expired = contactMapper.selectList(qw);
    int count = expired.size();
    for (Contact c : expired) {
      c.setDeleted(1);
      contactMapper.updateById(c);
    }
    log.info("Archived {} expired contacts before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期跟进记录。 */
  private int archiveExpiredFollowUps(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<FollowUp> qw =
        new LambdaQueryWrapper<FollowUp>().lt(FollowUp::getCreatedAt, cutoffDate);
    List<FollowUp> expired = followUpMapper.selectList(qw);
    int count = expired.size();
    for (FollowUp f : expired) {
      f.setDeleted(1);
      followUpMapper.updateById(f);
    }
    log.info("Archived {} expired follow-ups before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期商机。 */
  private int archiveExpiredOpportunities(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<Opportunity> qw =
        new LambdaQueryWrapper<Opportunity>().lt(Opportunity::getCreatedAt, cutoffDate);
    List<Opportunity> expired = opportunityMapper.selectList(qw);
    int count = expired.size();
    for (Opportunity o : expired) {
      o.setDeleted(1);
      opportunityMapper.updateById(o);
    }
    log.info("Archived {} expired opportunities before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期合同。 */
  private int archiveExpiredContracts(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<Contract> qw =
        new LambdaQueryWrapper<Contract>().lt(Contract::getCreatedAt, cutoffDate);
    List<Contract> expired = contractMapper.selectList(qw);
    int count = expired.size();
    for (Contract c : expired) {
      c.setDeleted(1);
      contractMapper.updateById(c);
    }
    log.info("Archived {} expired contracts before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期工单。 */
  private int archiveExpiredTickets(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<Ticket> qw =
        new LambdaQueryWrapper<Ticket>().lt(Ticket::getCreatedAt, cutoffDate);
    List<Ticket> expired = ticketMapper.selectList(qw);
    int count = expired.size();
    for (Ticket t : expired) {
      t.setDeleted(1);
      ticketMapper.updateById(t);
    }
    log.info("Archived {} expired tickets before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期任务。 */
  private int archiveExpiredTasks(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<TaskItem> qw =
        new LambdaQueryWrapper<TaskItem>().lt(TaskItem::getCreatedAt, cutoffDate);
    List<TaskItem> expired = taskItemMapper.selectList(qw);
    int count = expired.size();
    for (TaskItem t : expired) {
      t.setDeleted(1);
      taskItemMapper.updateById(t);
    }
    log.info("Archived {} expired tasks before {}", count, cutoffDate);
    return count;
  }

  /** 归档过期工作流执行日志。 */
  private int archiveExpiredWorkflowLogs(LocalDateTime cutoffDate) {
    LambdaQueryWrapper<WorkflowExecutionLog> qw =
        new LambdaQueryWrapper<WorkflowExecutionLog>()
            .lt(WorkflowExecutionLog::getCreatedAt, cutoffDate);
    List<WorkflowExecutionLog> expired = workflowExecutionLogMapper.selectList(qw);
    int count = expired.size();
    for (WorkflowExecutionLog w : expired) {
      w.setDeleted(1);
      workflowExecutionLogMapper.updateById(w);
    }
    log.info("Archived {} expired workflow logs before {}", count, cutoffDate);
    return count;
  }

  private DataRetentionPolicyResponse toResponse(DataRetentionPolicy entity) {
    DataRetentionPolicyResponse response = new DataRetentionPolicyResponse();
    response.setId(entity.getId());
    response.setEntityType(entity.getEntityType());
    response.setRetentionDays(entity.getRetentionDays());
    response.setActionType(entity.getActionType());
    response.setStatus(entity.getStatus());
    response.setCreatedAt(entity.getCreatedAt());
    response.setUpdatedAt(entity.getUpdatedAt());
    return response;
  }

  private DataRetentionExecutionResponse toExecutionResponse(DataRetentionExecution entity) {
    DataRetentionExecutionResponse response = new DataRetentionExecutionResponse();
    response.setId(entity.getId());
    response.setPolicyId(entity.getPolicyId());
    response.setExecutedAt(entity.getExecutedAt());
    response.setStatus(entity.getStatus());
    response.setProcessedCount(entity.getProcessedCount());
    response.setErrorMessage(entity.getErrorMessage());
    response.setCreatedAt(entity.getCreatedAt());
    return response;
  }
}
