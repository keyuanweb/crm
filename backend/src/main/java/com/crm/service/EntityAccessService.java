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
 * 实体行级可见性判定（063-security-hardening 抽取，供 FollowUp/Comment 等复用）。
 * 语义与 CustomerService.checkViewPermission / LeadService.checkLeadPermission 对齐。
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

  private boolean isUnrestricted(Long userId) {
    if (userId == null) {
      return false;
    }
    var principal = com.crm.security.SecurityUtil.currentPrincipal();
    // 无 principal（系统内部调用）或 ADMIN → 放行
    return principal == null || "ADMIN".equals(principal.role());
  }
}
