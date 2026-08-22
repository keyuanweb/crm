package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.pool.BatchTransferRequest;
import com.crm.dto.pool.PoolScanResult;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 客户公海服务（011，FR-PL01~PL08）：公海/领取/扫描退回/批量转移。 */
@Service
public class CustomerPoolService {

  private static final Logger log = LoggerFactory.getLogger(CustomerPoolService.class);

  private final CustomerMapper customerMapper;
  private final FollowUpMapper followUpMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  /** 公海退回阈值（天），默认 30，可配置 crm.pool.stale-days。 */
  @Value("${crm.pool.stale-days:30}")
  private int staleDays;

  public CustomerPoolService(
      CustomerMapper customerMapper,
      FollowUpMapper followUpMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.customerMapper = customerMapper;
    this.followUpMapper = followUpMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /** 公海客户分页：owner_id IS NULL。 */
  public PageResult<CustomerResponse> pool(
      String keyword, String status, long page, long pageSize) {
    LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
    qw.isNull(Customer::getOwnerId);
    applyFilters(qw, keyword, status);
    qw.orderByDesc(Customer::getId);
    Page<Customer> p = customerMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(toResponses(p.getRecords()), p.getTotal(), page, pageSize);
  }

  /** 我的客户分页：owner_id = 当前用户。 */
  public PageResult<CustomerResponse> mine(
      String keyword, String status, long page, long pageSize) {
    Long userId = SecurityUtil.currentUserId();
    LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
    qw.eq(Customer::getOwnerId, userId);
    applyFilters(qw, keyword, status);
    qw.orderByDesc(Customer::getId);
    Page<Customer> p = customerMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(toResponses(p.getRecords()), p.getTotal(), page, pageSize);
  }

  /** 领取：owner 空 → 本人（条件更新，并发安全）。 */
  @Transactional
  public CustomerResponse claim(Long id) {
    Customer customer = customerMapper.selectById(id);
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    Long userId = SecurityUtil.currentUserId();
    int rows =
        customerMapper.update(
            null,
            new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, id)
                .isNull(Customer::getOwnerId)
                .set(Customer::getOwnerId, userId));
    if (rows == 0) {
      throw new BusinessException(ErrorCode.CUSTOMER_ALREADY_OWNED);
    }
    auditService.record("CLAIM", "CUSTOMER", id, "领取客户：" + customer.getName());
    return toResponse(customerMapper.selectById(id));
  }

  /** 公海扫描：超 N 天未跟进/创建且已归属的客户退回公海（仅 ADMIN 调用）。 */
  @Transactional
  public PoolScanResult scan() {
    List<Customer> owned =
        customerMapper.selectList(
            new LambdaQueryWrapper<Customer>().isNotNull(Customer::getOwnerId));
    if (owned.isEmpty()) {
      return new PoolScanResult(0);
    }
    // 批量取最近跟进时间（避免 N+1）
    List<Long> customerIds = owned.stream().map(Customer::getId).toList();
    Map<Long, LocalDateTime> lastFollowUpAt = new HashMap<>();
    for (FollowUp fu :
        followUpMapper.selectList(
            new LambdaQueryWrapper<FollowUp>()
                .in(FollowUp::getCustomerId, customerIds)
                .select(FollowUp::getCustomerId, FollowUp::getCreatedAt))) {
      LocalDateTime prev = lastFollowUpAt.get(fu.getCustomerId());
      if (prev == null || fu.getCreatedAt().isAfter(prev)) {
        lastFollowUpAt.put(fu.getCustomerId(), fu.getCreatedAt());
      }
    }
    LocalDateTime cutoff = LocalDateTime.now().minusDays(staleDays);
    List<Long> staleIds =
        owned.stream()
            .filter(c -> isStale(c, lastFollowUpAt.get(c.getId()), cutoff))
            .map(Customer::getId)
            .toList();
    if (staleIds.isEmpty()) {
      return new PoolScanResult(0);
    }
    for (Long id : staleIds) {
      customerMapper.update(
          null,
          new LambdaUpdateWrapper<Customer>()
              .eq(Customer::getId, id)
              .isNotNull(Customer::getOwnerId)
              .set(Customer::getOwnerId, null));
      auditService.record("POOL_RETURN", "CUSTOMER", id, "超期未跟进退回公海");
    }
    log.info("Pool scan returned {} customers (stale-days={})", staleIds.size(), staleDays);
    return new PoolScanResult(staleIds.size());
  }

  /** 批量转移/分配：归属 → targetOwnerId（目标用户须存在）。 */
  @Transactional
  public long batchTransfer(BatchTransferRequest req) {
    User target = userMapper.selectById(req.getTargetOwnerId());
    if (target == null) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    List<Long> ids = req.getCustomerIds();
    int rows = 0;
    for (Long id : ids) {
      rows +=
          customerMapper.update(
              null,
              new LambdaUpdateWrapper<Customer>()
                  .eq(Customer::getId, id)
                  .set(Customer::getOwnerId, req.getTargetOwnerId()));
      auditService.record("TRANSFER", "CUSTOMER", id, "转移客户至用户 " + req.getTargetOwnerId());
    }
    return rows;
  }

  private boolean isStale(Customer customer, LocalDateTime lastFollowUpAt, LocalDateTime cutoff) {
    LocalDateTime activeAt = lastFollowUpAt != null ? lastFollowUpAt : customer.getCreatedAt();
    return activeAt == null || activeAt.isBefore(cutoff);
  }

  private void applyFilters(LambdaQueryWrapper<Customer> qw, String keyword, String status) {
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
  }

  /** 批量装配：ownerName 批量查询避免 N+1。 */
  private List<CustomerResponse> toResponses(List<Customer> customers) {
    if (customers.isEmpty()) {
      return List.of();
    }
    Map<Long, String> ownerNames = new HashMap<>();
    List<Long> ownerIds =
        customers.stream().map(Customer::getOwnerId).filter(Objects::nonNull).distinct().toList();
    if (!ownerIds.isEmpty()) {
      for (User u : userMapper.selectBatchIds(ownerIds)) {
        if (u != null && u.getId() != null) {
          ownerNames.put(
              u.getId(), u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
        }
      }
    }
    return customers.stream()
        .map(
            c -> {
              CustomerResponse resp = toResponse(c);
              resp.setOwnerName(c.getOwnerId() == null ? null : ownerNames.get(c.getOwnerId()));
              return resp;
            })
        .toList();
  }

  private CustomerResponse toResponse(Customer customer) {
    CustomerResponse resp = new CustomerResponse();
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
    resp.setVersion(customer.getVersion());
    resp.setCreatedAt(customer.getCreatedAt());
    return resp;
  }
}
