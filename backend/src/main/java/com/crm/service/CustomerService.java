package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.MaskingUtil;
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
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.security.SecurityUtil;
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
      com.crm.repository.UserMapper userMapper) {
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
  }

  public PageResult<CustomerResponse> page(
      String keyword, String status, long page, long pageSize) {
    LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
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
    // FR-016：列表/搜索结果敏感字段脱敏，详情保持完整
    List<CustomerResponse> items =
        p.getRecords().stream()
            .map(this::toResponse)
            .map(
                resp -> {
                  resp.setPhone(MaskingUtil.maskPhone(resp.getPhone()));
                  resp.setEmail(MaskingUtil.maskEmail(resp.getEmail()));
                  return resp;
                })
            .toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

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
    return resp;
  }

  @Transactional
  public CustomerResponse create(CustomerRequest req) {
    ensureUnique(null, req.getName(), req.getCompany());
    Customer customer = new Customer();
    apply(req, customer);
    if (!StringUtils.hasText(customer.getStatus())) {
      customer.setStatus("ACTIVE");
    }
    customer.setCreatedBy(SecurityUtil.currentUserId());
    customerMapper.insert(customer);
    dashboardStatsService.evict();
    auditService.record("CREATE", "CUSTOMER", customer.getId(), "创建客户：" + customer.getName());
    return toResponse(customer);
  }

  @Transactional
  public CustomerResponse update(Long id, CustomerRequest req) {
    Customer existing = require(id);
    checkWritePermission(existing);
    ensureUnique(id, req.getName(), req.getCompany());
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = customerMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    dashboardStatsService.evict();
    auditService.record("UPDATE", "CUSTOMER", id, "编辑客户：" + existing.getName());
    return toResponse(customerMapper.selectById(id));
  }

  @Transactional
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
    resp.setCampaignId(customer.getCampaignId());
    resp.setVersion(customer.getVersion());
    resp.setCreatedAt(customer.getCreatedAt());
  }
}
