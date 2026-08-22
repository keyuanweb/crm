package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.share.CustomerShareRequest;
import com.crm.dto.share.SharedCustomerResponse;
import com.crm.entity.Customer;
import com.crm.entity.CustomerShare;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.CustomerShareMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 客户共享服务（012，FR-DP07/DP08）：共享/取消/共享给我（只读）。 */
@Service
public class CustomerShareService {

  private final CustomerShareMapper shareMapper;
  private final CustomerMapper customerMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public CustomerShareService(
      CustomerShareMapper shareMapper,
      CustomerMapper customerMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.shareMapper = shareMapper;
    this.customerMapper = customerMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /** 共享客户给用户（归属者或 ADMIN）。 */
  @Transactional
  public SharedCustomerResponse share(CustomerShareRequest req) {
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    Long currentUserId = SecurityUtil.currentUserId();
    User current = userMapper.selectById(currentUserId);
    boolean isAdmin = current != null && "ADMIN".equals(current.getRole());
    if (!isAdmin && !currentUserId.equals(customer.getOwnerId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    Long exists =
        shareMapper.selectCount(
            new LambdaQueryWrapper<CustomerShare>()
                .eq(CustomerShare::getCustomerId, req.getCustomerId())
                .eq(CustomerShare::getSharedToUserId, req.getSharedToUserId()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.SHARE_EXISTS);
    }
    CustomerShare share = new CustomerShare();
    share.setCustomerId(req.getCustomerId());
    share.setSharedToUserId(req.getSharedToUserId());
    share.setSharedBy(currentUserId);
    shareMapper.insert(share);
    auditService.record(
        "SHARE", "CUSTOMER", req.getCustomerId(), "共享客户给用户 " + req.getSharedToUserId());
    return toSharedResponse(share, customer);
  }

  /** 取消共享（归属者或 ADMIN）。 */
  @Transactional
  public void unshare(Long shareId) {
    CustomerShare share = shareMapper.selectById(shareId);
    if (share == null) {
      throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
    }
    Customer customer = customerMapper.selectById(share.getCustomerId());
    Long currentUserId = SecurityUtil.currentUserId();
    User current = userMapper.selectById(currentUserId);
    boolean isAdmin = current != null && "ADMIN".equals(current.getRole());
    if (!isAdmin && (customer == null || !currentUserId.equals(customer.getOwnerId()))) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    shareMapper.deleteById(shareId);
    auditService.record(
        "UNSHARE", "CUSTOMER", share.getCustomerId(), "取消共享给用户 " + share.getSharedToUserId());
  }

  /** 共享给我的客户列表。 */
  public PageResult<SharedCustomerResponse> sharedToMe(long page, long pageSize) {
    Long userId = SecurityUtil.currentUserId();
    Page<CustomerShare> p =
        shareMapper.selectPage(
            new Page<>(page, pageSize),
            new LambdaQueryWrapper<CustomerShare>()
                .eq(CustomerShare::getSharedToUserId, userId)
                .orderByDesc(CustomerShare::getId));
    List<CustomerShare> records = p.getRecords();
    List<Long> customerIds = records.stream().map(CustomerShare::getCustomerId).distinct().toList();
    Map<Long, Customer> customerById =
        customerIds.isEmpty()
            ? Map.of()
            : customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
    List<SharedCustomerResponse> items =
        records.stream()
            .map(s -> toSharedResponse(s, customerById.get(s.getCustomerId())))
            .toList();
    return PageResult.of(items, p.getTotal(), page, pageSize);
  }

  private SharedCustomerResponse toSharedResponse(CustomerShare share, Customer customer) {
    SharedCustomerResponse resp = new SharedCustomerResponse();
    resp.setShareId(share.getId());
    resp.setCustomerId(share.getCustomerId());
    resp.setCustomerName(customer == null ? null : customer.getName());
    resp.setCompany(customer == null ? null : customer.getCompany());
    resp.setSharedBy(share.getSharedBy());
    resp.setSharedAt(share.getCreatedAt());
    return resp;
  }
}
