package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractResponse;
import com.crm.entity.Contract;
import com.crm.entity.Customer;
import com.crm.repository.ContractMapper;
import com.crm.repository.CustomerMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 合同续约服务（046，FR-R01~R06）：到期归类/续约漏斗/来源去向。 */
@Service
public class ContractRenewalService {

  public static final String GROUP_EXPIRING_SOON = "EXPIRING_SOON";
  public static final String GROUP_EXPIRED_UNRENEWED = "EXPIRED_UNRENEWED";
  public static final String GROUP_RENEWED = "RENEWED";

  /** 到期提醒阈值（天）。 */
  private static final long EXPIRING_WINDOW_DAYS = 90;

  private final ContractMapper contractMapper;
  private final CustomerMapper customerMapper;

  public ContractRenewalService(ContractMapper contractMapper, CustomerMapper customerMapper) {
    this.contractMapper = contractMapper;
    this.customerMapper = customerMapper;
  }

  /** 续约漏斗视图：按分组返回生效合同列表。 */
  public PageResult<ContractResponse> overview(
      String group, String keyword, long page, long pageSize) {
    if (!Set.of(GROUP_EXPIRING_SOON, GROUP_EXPIRED_UNRENEWED, GROUP_RENEWED).contains(group)) {
      throw new BusinessException(ErrorCode.CONTRACT_RENEWAL_GROUP_INVALID);
    }
    LocalDate today = LocalDate.now();

    // 当前页合同 id 集合（先筛选 id，再装配，避免在内存做分页）
    LambdaQueryWrapper<Contract> qw =
        new LambdaQueryWrapper<Contract>()
            .eq(Contract::getStatus, ContractService.STATUS_EFFECTIVE);
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(w -> w.like(Contract::getContractNo, kw).or().like(Contract::getTitle, kw));
    }
    qw.orderByAsc(Contract::getEndDate).orderByAsc(Contract::getId);
    Page<Contract> p = contractMapper.selectPage(new Page<>(page, pageSize), qw);
    List<Contract> candidates = p.getRecords();
    if (candidates.isEmpty()) {
      return PageResult.of(List.of(), 0, page, pageSize);
    }

    // 续约去向集合：合同 id → 是否有续约（一次查询）
    Set<Long> renewedIds =
        contractMapper
            .selectList(
                new LambdaQueryWrapper<Contract>()
                    .in(
                        Contract::getRenewedFromId,
                        candidates.stream().map(Contract::getId).toList()))
            .stream()
            .map(Contract::getRenewedFromId)
            .collect(Collectors.toSet());

    List<Contract> filtered =
        candidates.stream()
            .filter(
                c -> {
                  boolean hasRenewal = renewedIds.contains(c.getId());
                  boolean expired = c.getEndDate() != null && c.getEndDate().isBefore(today);
                  boolean expiringSoon =
                      c.getEndDate() != null
                          && !expired
                          && !c.getEndDate().isAfter(today.plusDays(EXPIRING_WINDOW_DAYS));
                  return switch (group) {
                    case GROUP_EXPIRING_SOON -> expiringSoon;
                    case GROUP_EXPIRED_UNRENEWED -> expired && !hasRenewal;
                    case GROUP_RENEWED -> hasRenewal;
                    default -> false;
                  };
                })
            .toList();

    List<ContractResponse> items = assemble(filtered);
    return PageResult.of(items, filtered.size(), page, pageSize);
  }

  private List<ContractResponse> assemble(List<Contract> contracts) {
    if (contracts.isEmpty()) {
      return List.of();
    }
    Set<Long> customerIds =
        contracts.stream().map(Contract::getCustomerId).collect(Collectors.toSet());
    Map<Long, String> customerNames =
        customerIds.isEmpty()
            ? Map.of()
            : customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
    Map<Long, Contract> byId =
        contracts.stream().collect(Collectors.toMap(Contract::getId, c -> c));
    Map<Long, List<Contract>> renewedBy =
        contractMapper
            .selectList(
                new LambdaQueryWrapper<Contract>().in(Contract::getRenewedFromId, byId.keySet()))
            .stream()
            .collect(Collectors.groupingBy(Contract::getRenewedFromId));
    return contracts.stream()
        .map(
            c -> {
              ContractResponse resp = new ContractResponse();
              resp.setId(c.getId());
              resp.setContractNo(c.getContractNo());
              resp.setTitle(c.getTitle());
              resp.setCustomerId(c.getCustomerId());
              resp.setCustomerName(customerNames.get(c.getCustomerId()));
              resp.setAmount(c.getAmount());
              resp.setStartDate(c.getStartDate());
              resp.setEndDate(c.getEndDate());
              resp.setStatus(c.getStatus());
              resp.setRenewedFromId(c.getRenewedFromId());
              if (c.getRenewedFromId() != null) {
                Contract from = byId.get(c.getRenewedFromId());
                if (from == null) {
                  from = contractMapper.selectById(c.getRenewedFromId());
                }
                resp.setRenewedFromNo(from == null ? null : from.getContractNo());
              }
              resp.setRenewedBy(
                  renewedBy.getOrDefault(c.getId(), List.of()).stream()
                      .map(
                          r -> {
                            ContractResponse.RenewalTarget t = new ContractResponse.RenewalTarget();
                            t.setId(r.getId());
                            t.setContractNo(r.getContractNo());
                            t.setTitle(r.getTitle());
                            return t;
                          })
                      .toList());
              resp.setVersion(c.getVersion());
              resp.setCreatedAt(c.getCreatedAt());
              return resp;
            })
        .toList();
  }
}
