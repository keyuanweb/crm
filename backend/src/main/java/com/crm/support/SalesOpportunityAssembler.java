package com.crm.support;

import com.crm.dto.opportunity.SalesOpportunityResponse;
import com.crm.entity.Customer;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.CustomerMapper;
import com.crm.repository.OpportunityMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 销售机会响应装配器：批量加载父商机与客户名称，避免逐行查询（N+1，章程原则五）。 供 SalesOpportunityService 与 OpportunityService
 * 复用，保证组装逻辑单一来源。
 */
@Component
public class SalesOpportunityAssembler {

  private final OpportunityMapper opportunityMapper;
  private final CustomerMapper customerMapper;

  public SalesOpportunityAssembler(
      OpportunityMapper opportunityMapper, CustomerMapper customerMapper) {
    this.opportunityMapper = opportunityMapper;
    this.customerMapper = customerMapper;
  }

  /** 批量装配：一次查询全部父商机与客户名称。 */
  public List<SalesOpportunityResponse> assemble(List<SalesOpportunity> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    List<Long> oppIds =
        list.stream()
            .map(SalesOpportunity::getOpportunityId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, Opportunity> opportunities =
        oppIds.isEmpty()
            ? Map.of()
            : opportunityMapper.selectBatchIds(oppIds).stream()
                .collect(Collectors.toMap(Opportunity::getId, Function.identity()));
    List<Long> customerIds =
        opportunities.values().stream().map(Opportunity::getCustomerId).distinct().toList();
    Map<Long, String> customerNames =
        customerIds.isEmpty()
            ? Map.of()
            : customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
    return list.stream()
        .map(so -> assembleOne(so, opportunities.get(so.getOpportunityId()), customerNames))
        .toList();
  }

  /** 单条装配（详情/写操作返回用）：两次查询，无 N+1 风险。 */
  public SalesOpportunityResponse assembleOne(SalesOpportunity so) {
    if (so == null || so.getOpportunityId() == null) {
      return new SalesOpportunityResponse();
    }
    Opportunity parent = opportunityMapper.selectById(so.getOpportunityId());
    Map<Long, String> customerNames = Map.of();
    if (parent != null && parent.getCustomerId() != null) {
      Customer customer = customerMapper.selectById(parent.getCustomerId());
      if (customer != null) {
        customerNames = Map.of(customer.getId(), customer.getName());
      }
    }
    return assembleOne(so, parent, customerNames);
  }

  /** 装配单条（父商机与客户名由调用方提供，避免额外查询）。 */
  public SalesOpportunityResponse assembleOne(
      SalesOpportunity so, Opportunity parent, Map<Long, String> customerNames) {
    SalesOpportunityResponse resp = new SalesOpportunityResponse();
    resp.setId(so.getId());
    resp.setOpportunityId(so.getOpportunityId());
    resp.setAmount(so.getAmount());
    resp.setStage(so.getStage());
    resp.setExpectedCloseDate(so.getExpectedCloseDate());
    resp.setCloseResult(so.getCloseResult());
    resp.setClosedAt(so.getClosedAt());
    resp.setVersion(so.getVersion());
    resp.setCreatedAt(so.getCreatedAt());
    if (parent != null) {
      resp.setOpportunityName(parent.getName());
      resp.setCustomerName(customerNames.get(parent.getCustomerId()));
    }
    return resp;
  }
}
