package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.Customer;
import com.crm.entity.CustomerShare;
import com.crm.entity.Lead;
import com.crm.entity.Opportunity;
import com.crm.repository.CustomerMapper;
import com.crm.repository.CustomerShareMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 实体行级可见性判定（063-security-hardening 抽取，供 FollowUp/Comment 等复用）。 语义与
 * CustomerService.checkViewPermission / LeadService.checkLeadPermission 对齐。
 */
@Service
public class EntityAccessService {

  private final DataPermissionService dataPermissionService;
  private final CustomerMapper customerMapper;
  private final CustomerShareMapper customerShareMapper;
  private final LeadMapper leadMapper;
  private final OpportunityMapper opportunityMapper;

  public EntityAccessService(
      DataPermissionService dataPermissionService,
      CustomerMapper customerMapper,
      CustomerShareMapper customerShareMapper,
      LeadMapper leadMapper,
      OpportunityMapper opportunityMapper) {
    this.dataPermissionService = dataPermissionService;
    this.customerMapper = customerMapper;
    this.customerShareMapper = customerShareMapper;
    this.leadMapper = leadMapper;
    this.opportunityMapper = opportunityMapper;
  }

  /** 客户可见：ADMIN/ALL 或 owner ∈ 可见集 或 共享给我。 */
  public boolean canViewCustomer(Long userId, Long customerId) {
    if (customerId == null) {
      return false;
    }
    if (isUnrestricted(userId)) {
      return true;
    }
    Customer customer = customerMapper.selectById(customerId);
    if (customer == null) {
      return false;
    }
    List<Long> visibleOwners = dataPermissionService.resolveVisibleOwnerIds(userId);
    if (visibleOwners.isEmpty() || visibleOwners.contains(customer.getOwnerId())) {
      return true;
    }
    Long shared =
        customerShareMapper.selectCount(
            new LambdaQueryWrapper<CustomerShare>()
                .eq(CustomerShare::getCustomerId, customerId)
                .eq(CustomerShare::getSharedToUserId, userId));
    return shared != null && shared > 0;
  }

  /** 线索可见：ADMIN/ALL 或 owner ∈ 可见集。 */
  public boolean canViewLead(Long userId, Long leadId) {
    if (leadId == null) {
      return false;
    }
    if (isUnrestricted(userId)) {
      return true;
    }
    Lead lead = leadMapper.selectById(leadId);
    if (lead == null) {
      return false;
    }
    if (lead.getOwnerId() != null && lead.getOwnerId().equals(userId)) {
      return true;
    }
    List<Long> visibleOwners = dataPermissionService.resolveVisibleOwnerIds(userId);
    return visibleOwners.isEmpty() || visibleOwners.contains(lead.getOwnerId());
  }

  /** 商机可见：ADMIN/ALL 或所属客户可见（商机无 owner，继承客户可见性）。 */
  public boolean canViewOpportunity(Long userId, Long opportunityId) {
    if (opportunityId == null) {
      return false;
    }
    if (isUnrestricted(userId)) {
      return true;
    }
    Opportunity opportunity = opportunityMapper.selectById(opportunityId);
    if (opportunity == null) {
      return false;
    }
    return canViewCustomer(userId, opportunity.getCustomerId());
  }

  /** 当前用户可见客户 id 集（null=不过滤/ALL，空=无可见）。 */
  public List<Long> visibleCustomerIds(Long userId) {
    if (isUnrestricted(userId)) {
      return null;
    }
    List<Long> visibleOwners = dataPermissionService.resolveVisibleOwnerIds(userId);
    Set<Long> ids = new HashSet<>();
    if (visibleOwners.isEmpty()) {
      return List.of(); // DEPT 无成员 → 无可见
    }
    ids.addAll(
        customerMapper
            .selectList(
                new LambdaQueryWrapper<Customer>()
                    .select(Customer::getId)
                    .in(Customer::getOwnerId, visibleOwners))
            .stream()
            .map(Customer::getId)
            .toList());
    ids.addAll(
        customerShareMapper
            .selectList(
                new LambdaQueryWrapper<CustomerShare>()
                    .select(CustomerShare::getCustomerId)
                    .eq(CustomerShare::getSharedToUserId, userId))
            .stream()
            .map(CustomerShare::getCustomerId)
            .toList());
    return new ArrayList<>(ids);
  }

  /**
   * 「数据权限无限制」判定（FR-G11，显式三分支）。
   *
   * <ol>
   *   <li><b>无主体</b>（{@code principal == null}）→ 放行。服务于**系统内部调用**（如调度器、事件处理），
   *       与人工会话和机器主体都是不同主体。此分支**必须保留**：删掉会使内部调用被误拒。
   *   <li><b>管理员</b>（角色 {@code ADMIN}）→ 放行。
   *   <li><b>其余</b>（含 API Key 机器主体、普通角色）→ 不放行，走行级判定。
   * </ol>
   *
   * <p>此处刻意**不看** {@code userId} 指向的用户记录：判定的依据只能是**当前主体**。改造前 API Key 路径注入 {@code ADMIN}
   * 角色，正是靠"角色字符串"同时骗过本方法与 {@code PermissionAspect}；机器主体因此在第 3 分支落地，而不是新增一个分支。
   *
   * <p>副作用（须如实知悉）：{@code visibleCustomerIds} 等方法在本判定为假时，会用 {@link
   * DataPermissionService#resolveVisibleOwnerIds} 的结果作为过滤集，而该方法对机器主体返回"仅主体本人名下"——
   * 两者方向一致，不会出现"被判为不受限而不过滤"的组合。
   */
  private boolean isUnrestricted(Long userId) {
    if (userId == null) {
      return false;
    }
    var principal = com.crm.security.SecurityUtil.currentPrincipal();
    if (principal == null) {
      return true; // 分支 1：无主体（系统内部调用）
    }
    return "ADMIN".equals(principal.role()); // 分支 2：管理员；分支 3：其余 → 假
  }
}
