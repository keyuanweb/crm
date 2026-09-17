package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerDetailResponse;
import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.followup.FollowUpBrief;
import com.crm.dto.opportunity.OpportunityBrief;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.SalesOrder;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.security.SecurityUtil;
import com.crm.support.BuiltinFieldRegistry;
import com.crm.support.BuiltinWriteGuard;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 客户服务：CRUD、逻辑删除、乐观锁、唯一性（FR-001~005）。 */
@Service
public class CustomerService {

  private final CustomerMapper customerMapper;
  private final OpportunityMapper opportunityMapper;
  private final FollowUpMapper followUpMapper;
  private final SalesOpportunityMapper salesOpportunityMapper;
  private final ContactMapper contactMapper;
  private final AuditService auditService;
  private final DashboardStatsService dashboardStatsService;
  private final DataPermissionService dataPermissionService;
  private final com.crm.repository.CustomerShareMapper customerShareMapper;
  private final com.crm.repository.UserMapper userMapper;
  private final com.crm.repository.SalesOrderMapper orderMapper;
  private final CustomFieldService customFieldService;
  private final Customer360Service customer360Service;
  private final WebhookService webhookService;
  private final BuiltinWriteGuard builtinWriteGuard;

  public CustomerService(
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper,
      FollowUpMapper followUpMapper,
      SalesOpportunityMapper salesOpportunityMapper,
      ContactMapper contactMapper,
      AuditService auditService,
      DashboardStatsService dashboardStatsService,
      DataPermissionService dataPermissionService,
      com.crm.repository.CustomerShareMapper customerShareMapper,
      com.crm.repository.UserMapper userMapper,
      com.crm.repository.SalesOrderMapper orderMapper,
      CustomFieldService customFieldService,
      Customer360Service customer360Service,
      WebhookService webhookService,
      BuiltinWriteGuard builtinWriteGuard) {
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
    this.followUpMapper = followUpMapper;
    this.salesOpportunityMapper = salesOpportunityMapper;
    this.contactMapper = contactMapper;
    this.auditService = auditService;
    this.dashboardStatsService = dashboardStatsService;
    this.dataPermissionService = dataPermissionService;
    this.customerShareMapper = customerShareMapper;
    this.userMapper = userMapper;
    this.orderMapper = orderMapper;
    this.customFieldService = customFieldService;
    this.customer360Service = customer360Service;
    this.webhookService = webhookService;
    this.builtinWriteGuard = builtinWriteGuard;
  }

  @Transactional(readOnly = true)
  public PageResult<CustomerResponse> page(
      String keyword, String status, List<Long> customFieldMatchedIds, long page, long pageSize) {
    LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
    // 自定义字段筛选（FR-S03）
    if (customFieldMatchedIds != null) {
      if (customFieldMatchedIds.isEmpty()) {
        return PageResult.of(List.of(), 0, page, pageSize);
      }
      qw.in(Customer::getId, customFieldMatchedIds);
    }
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(
          w ->
              w.like(Customer::getName, kw)
                  .or()
                  .like(Customer::getCompany, kw)
                  .or()
                  .like(Customer::getContactPerson, kw)
                  .or()
                  .like(Customer::getPhone, kw));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Customer::getStatus, status.trim());
    }
    applyDataScopeFilter(qw);
    qw.orderByDesc(Customer::getId);
    Page<Customer> p = customerMapper.selectPage(new Page<>(page, pageSize), qw);
    // 列表直接返回完整号码/邮箱（用户要求显示全部；导出路径仍按 063 对非管理员脱敏）
    List<CustomerResponse> items = p.getRecords().stream().map(this::toResponse).toList();
    // 016：批量回填自定义字段值
    List<Long> ids = p.getRecords().stream().map(Customer::getId).toList();
    if (!ids.isEmpty()) {
      Map<Long, List<com.crm.dto.customfield.CustomFieldValueDTO>> values =
          customFieldService.readValuesBatch("CUSTOMER", ids);
      items.forEach(i -> i.setCustomFieldValues(values.getOrDefault(i.getId(), List.of())));
    }
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  @Transactional(readOnly = true)
  public CustomerDetailResponse detail(Long id) {
    Customer customer = require(id);
    checkViewPermission(customer);
    CustomerDetailResponse resp = new CustomerDetailResponse();
    copyToResponse(customer, resp);

    List<Opportunity> opportunities =
        opportunityMapper.selectList(
            new LambdaQueryWrapper<Opportunity>()
                .eq(Opportunity::getCustomerId, id)
                .orderByDesc(Opportunity::getId));
    if (!opportunities.isEmpty()) {
      List<Long> oppIds = opportunities.stream().map(Opportunity::getId).toList();
      Map<Long, Long> soCounts =
          salesOpportunityMapper
              .selectList(
                  new LambdaQueryWrapper<SalesOpportunity>()
                      .in(SalesOpportunity::getOpportunityId, oppIds))
              .stream()
              .collect(
                  Collectors.groupingBy(SalesOpportunity::getOpportunityId, Collectors.counting()));
      List<OpportunityBrief> briefs =
          opportunities.stream()
              .map(
                  o -> {
                    OpportunityBrief b = new OpportunityBrief();
                    b.setId(o.getId());
                    b.setName(o.getName());
                    b.setStatus(o.getStatus());
                    b.setSalesOpportunityCount(soCounts.getOrDefault(o.getId(), 0L).intValue());
                    return b;
                  })
              .toList();
      resp.setOpportunities(briefs);
    }

    List<FollowUpBrief> followUps =
        followUpMapper
            .selectList(
                new LambdaQueryWrapper<FollowUp>()
                    .eq(FollowUp::getCustomerId, id)
                    .orderByDesc(FollowUp::getCreatedAt))
            .stream()
            .map(
                f -> {
                  FollowUpBrief b = new FollowUpBrief();
                  b.setId(f.getId());
                  b.setMethod(f.getMethod());
                  b.setContent(f.getContent());
                  b.setCreatedAt(f.getCreatedAt());
                  return b;
                })
            .toList();
    resp.setFollowUps(followUps);

    // 005：客户联系人列表
    List<com.crm.dto.contact.ContactResponse> contacts =
        contactMapper
            .selectList(
                new LambdaQueryWrapper<Contact>()
                    .eq(Contact::getCustomerId, id)
                    .orderByDesc(Contact::getId))
            .stream()
            .map(
                c -> {
                  com.crm.dto.contact.ContactResponse cr =
                      new com.crm.dto.contact.ContactResponse();
                  cr.setId(c.getId());
                  cr.setCustomerId(c.getCustomerId());
                  cr.setCustomerName(customer.getName());
                  cr.setName(c.getName());
                  cr.setTitle(c.getTitle());
                  cr.setPhone(c.getPhone());
                  cr.setEmail(c.getEmail());
                  cr.setRole(c.getRole());
                  cr.setRemark(c.getRemark());
                  return cr;
                })
            .toList();
    resp.setContacts(contacts);
    resp.setCustomFieldValues(customFieldService.readValues("CUSTOMER", id));
    // 018：客户 360 聚合（订单/回款/合同/工单 + 金额汇总 + 健康度）
    resp.setCustomer360(customer360Service.aggregate(id));
    return resp;
  }

  /**
   * 流失预警列表（018-customer-360，FR-005）：超过 N 天无跟进且无新订单的可见客户，按健康度升序。
   *
   * @param daysInactive 无活动阈值天数（>0；≤0 视为不启用预警）
   */
  public PageResult<com.crm.dto.customer.CustomerHealthBrief> atRiskCustomers(
      int daysInactive, long page, long pageSize) {
    if (daysInactive <= 0) {
      return PageResult.of(List.of(), 0, page, pageSize);
    }
    LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
    applyDataScopeFilter(qw);
    qw.orderByDesc(Customer::getId);
    List<Customer> customers = customerMapper.selectList(qw);
    if (customers.isEmpty()) {
      return PageResult.of(List.of(), 0, page, pageSize);
    }

    java.time.LocalDateTime now = java.time.LocalDateTime.now();
    java.time.LocalDateTime cutoff = now.minusDays(daysInactive);
    List<Long> ids = customers.stream().map(Customer::getId).toList();

    // 最近跟进时间（按客户分组取 max）
    Map<Long, java.time.LocalDateTime> lastFollowUp =
        followUpMapper
            .selectList(
                new LambdaQueryWrapper<FollowUp>()
                    .in(FollowUp::getCustomerId, ids)
                    .select(FollowUp::getCustomerId, FollowUp::getCreatedAt))
            .stream()
            .collect(
                Collectors.toMap(
                    FollowUp::getCustomerId,
                    FollowUp::getCreatedAt,
                    (a, b) -> a.isAfter(b) ? a : b));
    // 最近订单时间（按客户分组取 max）
    Map<Long, java.time.LocalDateTime> lastOrder =
        orderMapper
            .selectList(
                new LambdaQueryWrapper<SalesOrder>()
                    .in(SalesOrder::getCustomerId, ids)
                    .select(SalesOrder::getCustomerId, SalesOrder::getUpdatedAt))
            .stream()
            .collect(
                Collectors.toMap(
                    SalesOrder::getCustomerId,
                    SalesOrder::getUpdatedAt,
                    (a, b) -> a.isAfter(b) ? a : b));

    List<Long> ownerIds =
        customers.stream()
            .map(Customer::getOwnerId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, com.crm.entity.User> owners =
        ownerIds.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(com.crm.entity.User::getId, u -> u));

    // 候选集：无跟进且无订单的可见客户（创建早于 cutoff）
    java.util.List<Customer> candidates =
        customers.stream()
            .filter(c -> !lastFollowUp.containsKey(c.getId()))
            .filter(c -> !lastOrder.containsKey(c.getId()))
            .filter(
                c -> {
                  // 无跟进且无订单 → 无活动天数即客户创建至今
                  return c.getCreatedAt() != null && c.getCreatedAt().isBefore(cutoff);
                })
            .toList();

    // 064(性能修复)：批量健康评分（复用批量聚合，消除逐客户 aggregate 的 N+1）
    Map<Long, Integer> healthScores =
        candidates.isEmpty()
            ? Map.of()
            : customer360Service.healthScoresBatch(
                candidates.stream().map(Customer::getId).toList());

    List<com.crm.dto.customer.CustomerHealthBrief> all =
        candidates.stream()
            .map(
                c -> {
                  com.crm.dto.customer.CustomerHealthBrief b =
                      new com.crm.dto.customer.CustomerHealthBrief();
                  b.setId(c.getId());
                  b.setName(c.getName());
                  b.setCompany(c.getCompany());
                  b.setLastFollowUpAt(null);
                  b.setLastOrderAt(null);
                  int inactive =
                      c.getCreatedAt() == null
                          ? daysInactive
                          : (int)
                              Math.max(
                                  0,
                                  java.time.temporal.ChronoUnit.DAYS.between(
                                      c.getCreatedAt(), now));
                  b.setDaysInactive(inactive);
                  com.crm.entity.User owner =
                      c.getOwnerId() == null ? null : owners.get(c.getOwnerId());
                  b.setOwnerName(owner == null ? null : owner.getDisplayName());
                  b.setHealthScore(healthScores.getOrDefault(c.getId(), 0));
                  return b;
                })
            .sorted(
                java.util.Comparator.comparingInt(
                    com.crm.dto.customer.CustomerHealthBrief::getHealthScore))
            .toList();

    long total = all.size();
    int from = (int) Math.min(all.size(), (page - 1) * pageSize);
    int to = (int) Math.min(all.size(), page * pageSize);
    return PageResult.of(from >= to ? List.of() : all.subList(from, to), total, page, pageSize);
  }

  @Transactional
  public CustomerResponse create(CustomerRequest req) {
    ensureUnique(null, req.getName(), req.getCompany());
    // 102：提交了 HIDDEN / 改了 READ_ONLY 的内置字段 ⇒ 422。create 没有库中原值，故只判定、
    // 不回补（内置字段在 create 上等价于「不可设置」，见 BuiltinWriteGuard 的类注释）
    builtinWriteGuard.validateOnly(BuiltinFieldRegistry.ENTITY_CUSTOMER, req);
    Customer customer = new Customer();
    apply(req, customer);
    if (!StringUtils.hasText(customer.getStatus())) {
      customer.setStatus("ACTIVE");
    }
    // 064(数据完整性)：非 ADMIN 创建未指定 owner 时默认负责人=当前用户（消除无主数据）；
    // ADMIN 创建不设 owner（池子/分配场景）
    if (customer.getOwnerId() == null) {
      var principal = SecurityUtil.currentPrincipal();
      if (principal == null || !"ADMIN".equals(principal.role())) {
        customer.setOwnerId(SecurityUtil.currentUserId());
      }
    }
    customer.setCreatedBy(SecurityUtil.currentUserId());
    customerMapper.insert(customer);
    if (req.getCustomFieldValues() != null && !req.getCustomFieldValues().isEmpty()) {
      customFieldService.saveValues("CUSTOMER", customer.getId(), req.getCustomFieldValues());
    }
    dashboardStatsService.evict();
    auditService.record("CREATE", "CUSTOMER", customer.getId(), "创建客户：" + customer.getName());
    // 055：Webhook 事件（客户创建）
    java.util.Map<String, Object> payload = new java.util.HashMap<>();
    payload.put("id", customer.getId());
    payload.put("name", customer.getName() == null ? "" : customer.getName());
    webhookService.publish(
        WebhookService.EVENT_CUSTOMER_CREATED, "CUSTOMER", customer.getId(), payload);
    return toResponse(customer);
  }

  @Transactional
  public CustomerResponse update(Long id, CustomerRequest req) {
    Customer existing = require(id);
    checkWritePermission(existing);
    ensureUnique(id, req.getName(), req.getCompany());
    // 102：先判定（提交 HIDDEN ⇒ 422、改 READ_ONLY ⇒ 422）并取库中快照，再让 apply 无条件覆盖
    Map<String, Object> guardedBuiltin =
        builtinWriteGuard.capture(BuiltinFieldRegistry.ENTITY_CUSTOMER, req, existing);
    apply(req, existing);
    // ⚠️ 回补必须在 apply **之后**：apply 无条件覆盖全部字段，被省略的受保护字段只有后置回补才保得住
    // （008cbb9 那类「编辑一次备注就静默清空电话」的缺陷就是这里缺了这一步）
    builtinWriteGuard.restore(BuiltinFieldRegistry.ENTITY_CUSTOMER, existing, guardedBuiltin);
    existing.setVersion(req.getVersion());
    int rows = customerMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    if (req.getCustomFieldValues() != null && !req.getCustomFieldValues().isEmpty()) {
      customFieldService.saveValues("CUSTOMER", id, req.getCustomFieldValues());
    }
    dashboardStatsService.evict();
    auditService.record("UPDATE", "CUSTOMER", id, "编辑客户：" + existing.getName());
    return toResponse(customerMapper.selectById(id));
  }

  @Transactional
  @com.crm.security.RequirePermission("customer:delete")
  public void delete(Long id) {
    Customer customer = require(id);
    checkWritePermission(customer);
    customerMapper.deleteById(id);
    dashboardStatsService.evict();
    auditService.record("DELETE", "CUSTOMER", id, "逻辑删除客户：" + customer.getName());
  }

  /** 012：行级数据权限过滤（ALL 不过滤；其余按可见 owner 集合）。 */
  private void applyDataScopeFilter(LambdaQueryWrapper<Customer> qw) {
    Long userId = SecurityUtil.currentUserId();
    java.util.List<Long> visibleOwners = dataPermissionService.resolveVisibleOwnerIds(userId);
    if (!visibleOwners.isEmpty()) {
      qw.in(Customer::getOwnerId, visibleOwners);
    }
  }

  /** 012：查看权限——owner 在可见集合（含 ALL）或已共享给我。 */
  private void checkViewPermission(Customer customer) {
    Long userId = SecurityUtil.currentUserId();
    if (userId.equals(customer.getOwnerId())) {
      return;
    }
    java.util.List<Long> visibleOwners = dataPermissionService.resolveVisibleOwnerIds(userId);
    if (visibleOwners.isEmpty() || visibleOwners.contains(customer.getOwnerId())) {
      return;
    }
    Long shared =
        customerShareMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                    com.crm.entity.CustomerShare>()
                .eq(com.crm.entity.CustomerShare::getCustomerId, customer.getId())
                .eq(com.crm.entity.CustomerShare::getSharedToUserId, userId));
    if (shared != null && shared > 0) {
      return;
    }
    throw new BusinessException(ErrorCode.FORBIDDEN);
  }

  /** 012：写操作权限——归属本人或管理员（ALL）。共享用户只读不可写。 */
  private void checkWritePermission(Customer customer) {
    Long userId = SecurityUtil.currentUserId();
    if (userId.equals(customer.getOwnerId())) {
      return;
    }
    if (isAdmin(userId)) {
      return;
    }
    throw new BusinessException(ErrorCode.FORBIDDEN);
  }

  private boolean isAdmin(Long userId) {
    com.crm.entity.User user = userMapper.selectById(userId);
    return user != null && "ADMIN".equals(user.getRole());
  }

  public Customer require(Long id) {
    Customer customer = customerMapper.selectById(id);
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    return customer;
  }

  private void ensureUnique(Long excludeId, String name, String company) {
    LambdaQueryWrapper<Customer> qw =
        new LambdaQueryWrapper<Customer>()
            .eq(Customer::getName, name)
            .eq(Customer::getCompany, company);
    if (excludeId != null) {
      qw.ne(Customer::getId, excludeId);
    }
    Long count = customerMapper.selectCount(qw);
    if (count != null && count > 0) {
      throw new BusinessException(ErrorCode.CUSTOMER_DUPLICATE);
    }
  }

  private void apply(CustomerRequest req, Customer customer) {
    customer.setName(req.getName().trim());
    customer.setCompany(req.getCompany().trim());
    customer.setContactPerson(req.getContactPerson());
    customer.setPhone(req.getPhone());
    customer.setEmail(req.getEmail());
    customer.setAddress(req.getAddress());
    customer.setRemark(req.getRemark());
    customer.setCampaignId(req.getCampaignId()); // 014：营销归因
    if (StringUtils.hasText(req.getStatus())) {
      customer.setStatus(req.getStatus().trim());
    }
  }

  private CustomerResponse toResponse(Customer customer) {
    CustomerResponse resp = new CustomerResponse();
    copyToResponse(customer, resp);
    return resp;
  }

  private void copyToResponse(Customer customer, CustomerResponse resp) {
    resp.setId(customer.getId());
    resp.setName(customer.getName());
    resp.setCompany(customer.getCompany());
    resp.setContactPerson(customer.getContactPerson());
    resp.setPhone(customer.getPhone());
    resp.setEmail(customer.getEmail());
    resp.setAddress(customer.getAddress());
    resp.setRemark(customer.getRemark());
    resp.setStatus(customer.getStatus());
    resp.setOwnerId(customer.getOwnerId());
    resp.setCampaignId(customer.getCampaignId());
    resp.setVersion(customer.getVersion());
    resp.setCreatedAt(customer.getCreatedAt());
  }
}
