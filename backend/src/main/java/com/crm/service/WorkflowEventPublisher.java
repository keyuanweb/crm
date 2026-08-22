package com.crm.service;

import java.util.Map;
import org.springframework.stereotype.Service;

/** 工作流事件发布门面（013）：业务 Service 调用触发，解耦 WorkflowEngine。 事件执行失败不影响主流程（WorkflowEngine 内部已隔离）。 */
@Service
public class WorkflowEventPublisher {

  private final WorkflowEngine workflowEngine;

  public WorkflowEventPublisher(WorkflowEngine workflowEngine) {
    this.workflowEngine = workflowEngine;
  }

  public void leadCreated(Long leadId, Map<String, String> context) {
    workflowEngine.fire(WorkflowEngine.EVENT_LEAD_CREATED, "LEAD", leadId, context);
  }

  public void opportunityStageChanged(Long salesOpportunityId, Map<String, String> context) {
    workflowEngine.fire(
        WorkflowEngine.EVENT_OPPORTUNITY_STAGE_CHANGED,
        "SALES_OPPORTUNITY",
        salesOpportunityId,
        context);
  }

  public void followUpCreated(Long followUpId, Map<String, String> context) {
    workflowEngine.fire(WorkflowEngine.EVENT_FOLLOW_UP_CREATED, "FOLLOW_UP", followUpId, context);
  }

  public void paymentRecorded(Long orderId, Map<String, String> context) {
    workflowEngine.fire(WorkflowEngine.EVENT_PAYMENT_RECORDED, "SALES_ORDER", orderId, context);
  }
}
