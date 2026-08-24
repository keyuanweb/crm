package com.crm.service;

import com.crm.entity.ApprovalFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 审批流启动委托（033 修复）：独立事务（REQUIRES_NEW）发起审批，失败回滚不影响主事务。 先查启用流程（无流程直接跳过、不进入事务），有流程才启动；启动异常传播由调用方捕获。 */
@Service
public class ApprovalLaunchDelegate {

  private static final Logger log = LoggerFactory.getLogger(ApprovalLaunchDelegate.class);

  private final ApprovalFlowService flowService;
  private final ApprovalEngineService approvalEngineService;

  public ApprovalLaunchDelegate(
      ApprovalFlowService flowService, ApprovalEngineService approvalEngineService) {
    this.flowService = flowService;
    this.approvalEngineService = approvalEngineService;
  }

  /** 无启用流程 → 静默跳过；有流程 → 独立事务启动（异常传播给调用方）。 */
  public void launchQuietly(String businessType, Long businessId, String title, double amount) {
    ApprovalFlow flow = flowService.enabledFlow(businessType);
    if (flow == null) {
      log.info("未配置启用的审批流（{}），跳过自动审批", businessType);
      return;
    }
    launchInNewTx(businessType, businessId, title, amount);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void launchInNewTx(String businessType, Long businessId, String title, double amount) {
    approvalEngineService.start(businessType, businessId, title, amount);
  }
}
