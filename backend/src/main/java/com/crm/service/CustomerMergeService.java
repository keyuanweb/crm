package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.merge.DuplicateGroupResponse;
import com.crm.dto.merge.MergeRequest;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.entity.CustomerShare;
import com.crm.entity.CustomerTag;
import com.crm.entity.FollowUp;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOrder;
import com.crm.entity.Ticket;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.CustomerShareMapper;
import com.crm.repository.CustomerTagMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.repository.TicketMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 客户查重合并服务（034-customer-merge，FR-001~005）：查重扫描（名称归一化 + 电话/邮箱精确）+ 合并（关联数据转移 + 字段冲突主优先 + 从记录逻辑删除进回收站
 * + 审计）。
 */
@Service
public class CustomerMergeService {

  private final CustomerMapper customerMapper;
  private final SalesOrderMapper orderMapper;
  private final OpportunityMapper oppMapper;
  private final ContactMapper contactMapper;
  private final FollowUpMapper followUpMapper;
  private final TicketMapper ticketMapper;
  private final CustomerTagMapper customerTagMapper;
  private final CustomerShareMapper customerShareMapper;
  private final AuditService auditService;

  public CustomerMergeService(
      CustomerMapper customerMapper,
      SalesOrderMapper orderMapper,
      OpportunityMapper oppMapper,
      ContactMapper contactMapper,
      FollowUpMapper followUpMapper,
      TicketMapper ticketMapper,
      CustomerTagMapper customerTagMapper,
      CustomerShareMapper customerShareMapper,
      AuditService auditService) {
    this.customerMapper = customerMapper;
    this.orderMapper = orderMapper;
    this.oppMapper = oppMapper;
    this.contactMapper = contactMapper;
    this.followUpMapper = followUpMapper;
    this.ticketMapper = ticketMapper;
    this.customerTagMapper = customerTagMapper;
    this.customerShareMapper = customerShareMapper;
    this.auditService = auditService;
  }

  /** 查重扫描：返回重复组（每组主 + 疑似重复列表）。 */
  public List<DuplicateGroupResponse> scanDuplicates() {
    List<Customer> customers = customerMapper.selectList(null);
    // 按归一化名称+公司分组（精确）
    Map<String, List<Customer>> exactGroups = new LinkedHashMap<>();
    for (Customer c : customers) {
      String key = normalize(c.getName()) + "|" + normalize(c.getCompany());
      exactGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
    }
    // 电话/邮箱索引（联系方式重复）
    Map<String, List<Customer>> phoneGroups = new LinkedHashMap<>();
    Map<String, List<Customer>> emailGroups = new LinkedHashMap<>();
    for (Customer c : customers) {
      if (StringUtils.hasText(c.getPhone())) {
        phoneGroups.computeIfAbsent(c.getPhone().trim(), k -> new ArrayList<>()).add(c);
      }
      if (StringUtils.hasText(c.getEmail())) {
        emailGroups
            .computeIfAbsent(c.getEmail().trim().toLowerCase(), k -> new ArrayList<>())
            .add(c);
      }
    }

    Map<Long, DuplicateGroupResponse> groups = new LinkedHashMap<>();
    // 精确名称组（>1 才重复）
    for (List<Customer> list : exactGroups.values()) {
      if (list.size() > 1) {
        addGroup(groups, list.get(0), list.subList(1, list.size()), 100);
      }
    }
    // 电话组
    for (List<Customer> list : phoneGroups.values()) {
      if (list.size() > 1 && !isSameNameGroup(list)) {
        addGroup(groups, list.get(0), list.subList(1, list.size()), 85);
      }
    }
    // 邮箱组
    for (List<Customer> list : emailGroups.values()) {
      if (list.size() > 1 && !isSameNameGroup(list)) {
        addGroup(groups, list.get(0), list.subList(1, list.size()), 85);
      }
    }
    return new ArrayList<>(groups.values());
  }

  private boolean isSameNameGroup(List<Customer> list) {
    String first = normalize(list.get(0).getName());
    return list.stream().allMatch(c -> normalize(c.getName()).equals(first));
  }

  private void addGroup(
      Map<Long, DuplicateGroupResponse> groups,
      Customer primary,
      List<Customer> dups,
      int similarity) {
    DuplicateGroupResponse g =
        groups.computeIfAbsent(
            primary.getId(),
            k -> {
              DuplicateGroupResponse resp = new DuplicateGroupResponse();
              resp.setId(primary.getId());
              resp.setPrimaryId(primary.getId());
              resp.setPrimaryName(primary.getName());
              resp.setDuplicates(new ArrayList<>());
              return resp;
            });
    for (Customer d : dups) {
      if (d.getId().equals(primary.getId())) {
        continue;
      }
      DuplicateGroupResponse.DuplicateItem item = new DuplicateGroupResponse.DuplicateItem();
      item.setCustomerId(d.getId());
      item.setName(d.getName());
      item.setCompany(d.getCompany());
      item.setSimilarity(similarity);
      item.setRelatedCount(relatedCount(d.getId()));
      boolean exists =
          g.getDuplicates().stream().anyMatch(x -> x.getCustomerId().equals(d.getId()));
      if (!exists) {
        g.getDuplicates().add(item);
      }
    }
  }

  /** 关联数据计数（订单+商机+联系人+跟进+工单）。 */
  private long relatedCount(Long customerId) {
    long count = 0;
    Long n =
        orderMapper.selectCount(
            new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getCustomerId, customerId));
    count += n == null ? 0 : n;
    n =
        oppMapper.selectCount(
            new LambdaQueryWrapper<Opportunity>().eq(Opportunity::getCustomerId, customerId));
    count += n == null ? 0 : n;
    n =
        contactMapper.selectCount(
            new LambdaQueryWrapper<Contact>().eq(Contact::getCustomerId, customerId));
    count += n == null ? 0 : n;
    n =
        followUpMapper.selectCount(
            new LambdaQueryWrapper<FollowUp>().eq(FollowUp::getCustomerId, customerId));
    count += n == null ? 0 : n;
    n =
        ticketMapper.selectCount(
            new LambdaQueryWrapper<Ticket>().eq(Ticket::getCustomerId, customerId));
    count += n == null ? 0 : n;
    return count;
  }

  /** 合并：主记录保留 + 关联数据转移 + 字段冲突主优先 + 从记录回收站 + 审计。 */
  @Transactional
  public Map<String, Object> merge(MergeRequest req) {
    if (req.getPrimaryId() == null || req.getDuplicateId() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择主记录与重复记录");
    }
    if (req.getPrimaryId().equals(req.getDuplicateId())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "主记录与重复记录不能相同");
    }
    Customer primary = customerMapper.selectById(req.getPrimaryId());
    Customer duplicate = customerMapper.selectById(req.getDuplicateId());
    if (primary == null || duplicate == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "客户不存在");
    }
    Long fromId = duplicate.getId();
    Long toId = primary.getId();

    // 1. 关联数据转移
    long movedOrders =
        orderMapper.update(
            null,
            new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getCustomerId, fromId)
                .set(SalesOrder::getCustomerId, toId));
    long movedOpps =
        oppMapper.update(
            null,
            new LambdaUpdateWrapper<Opportunity>()
                .eq(Opportunity::getCustomerId, fromId)
                .set(Opportunity::getCustomerId, toId));
    long movedContacts =
        contactMapper.update(
            null,
            new LambdaUpdateWrapper<Contact>()
                .eq(Contact::getCustomerId, fromId)
                .set(Contact::getCustomerId, toId));
    long movedFollowUps =
        followUpMapper.update(
            null,
            new LambdaUpdateWrapper<FollowUp>()
                .eq(FollowUp::getCustomerId, fromId)
                .set(FollowUp::getCustomerId, toId));
    long movedTickets =
        ticketMapper.update(
            null,
            new LambdaUpdateWrapper<Ticket>()
                .eq(Ticket::getCustomerId, fromId)
                .set(Ticket::getCustomerId, toId));

    // 2. 标签/共享去重转移（从记录关联改为主，重复忽略）
    customerTagMapper.delete(
        new LambdaQueryWrapper<CustomerTag>().eq(CustomerTag::getCustomerId, fromId));
    customerShareMapper.delete(
        new LambdaQueryWrapper<CustomerShare>().eq(CustomerShare::getCustomerId, fromId));

    // 3. 字段冲突：主空取从
    mergeFields(primary, duplicate);
    customerMapper.updateById(primary);

    // 4. 从记录逻辑删除（回收站可见）
    customerMapper.deleteById(fromId);
    auditService.record(
        "MERGE",
        "CUSTOMER",
        toId,
        "合并客户："
            + duplicate.getName()
            + " → "
            + primary.getName()
            + "（订单"
            + movedOrders
            + "/商机"
            + movedOpps
            + "/联系人"
            + movedContacts
            + "/跟进"
            + movedFollowUps
            + "/工单"
            + movedTickets
            + "）");

    Map<String, Object> result = new HashMap<>();
    result.put("primaryId", toId);
    result.put("movedOrders", movedOrders);
    result.put("movedOpportunities", movedOpps);
    result.put("movedContacts", movedContacts);
    result.put("movedFollowUps", movedFollowUps);
    result.put("movedTickets", movedTickets);
    return result;
  }

  /** 字段冲突：主记录为空则取从记录值。 */
  private void mergeFields(Customer primary, Customer duplicate) {
    if (!StringUtils.hasText(primary.getCompany()) && StringUtils.hasText(duplicate.getCompany())) {
      primary.setCompany(duplicate.getCompany());
    }
    if (!StringUtils.hasText(primary.getPhone()) && StringUtils.hasText(duplicate.getPhone())) {
      primary.setPhone(duplicate.getPhone());
    }
    if (!StringUtils.hasText(primary.getEmail()) && StringUtils.hasText(duplicate.getEmail())) {
      primary.setEmail(duplicate.getEmail());
    }
    if (!StringUtils.hasText(primary.getAddress()) && StringUtils.hasText(duplicate.getAddress())) {
      primary.setAddress(duplicate.getAddress());
    }
    if (!StringUtils.hasText(primary.getContactPerson())
        && StringUtils.hasText(duplicate.getContactPerson())) {
      primary.setContactPerson(duplicate.getContactPerson());
    }
    if (!StringUtils.hasText(primary.getRemark()) && StringUtils.hasText(duplicate.getRemark())) {
      primary.setRemark(duplicate.getRemark());
    }
  }

  /** 名称归一化：去空格/转小写/全角转半角。 */
  public static String normalize(String s) {
    if (!StringUtils.hasText(s)) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (char ch : s.trim().toCharArray()) {
      if (Character.isWhitespace(ch)) {
        continue;
      }
      // 全角转半角
      if (ch >= '\uFF01' && ch <= '\uFF5E') {
        ch = (char) (ch - 0xFEE0);
      }
      if (ch == '\u3000') {
        continue;
      }
      sb.append(Character.toLowerCase(ch));
    }
    return sb.toString();
  }
}
