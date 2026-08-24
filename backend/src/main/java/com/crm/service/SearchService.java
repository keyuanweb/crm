package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.search.SearchResponse;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.Opportunity;
import com.crm.entity.Product;
import com.crm.entity.Ticket;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.ProductMapper;
import com.crm.repository.TicketMapper;
import com.crm.security.SecurityUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 全局搜索服务（032-global-search，FR-002/003/004）：跨 6 实体 LIKE 聚合， 多关键字 AND，数据权限过滤（ADMIN 全量/其他按创建人或归属人）。
 */
@Service
public class SearchService {

  private final CustomerMapper customerMapper;
  private final LeadMapper leadMapper;
  private final ContactMapper contactMapper;
  private final OpportunityMapper oppMapper;
  private final TicketMapper ticketMapper;
  private final ProductMapper productMapper;

  public SearchService(
      CustomerMapper customerMapper,
      LeadMapper leadMapper,
      ContactMapper contactMapper,
      OpportunityMapper oppMapper,
      TicketMapper ticketMapper,
      ProductMapper productMapper) {
    this.customerMapper = customerMapper;
    this.leadMapper = leadMapper;
    this.contactMapper = contactMapper;
    this.oppMapper = oppMapper;
    this.ticketMapper = ticketMapper;
    this.productMapper = productMapper;
  }

  /** 下拉搜索：各实体 Top 5。 */
  public SearchResponse search(String keyword) {
    return build(keyword, null, 5);
  }

  /** 结果页搜索：按类型过滤，每实体 20。 */
  public SearchResponse searchFull(String keyword, String type) {
    return build(keyword, type, 20);
  }

  private SearchResponse build(String keyword, String type, int limit) {
    SearchResponse resp = new SearchResponse();
    resp.setKeyword(keyword);
    List<SearchResponse.SearchGroup> groups = new ArrayList<>();
    long total = 0;
    if (StringUtils.hasText(keyword)) {
      if (type == null || type.isBlank() || "CUSTOMER".equals(type)) {
        SearchResponse.SearchGroup g = customers(keyword, limit);
        if (!g.getItems().isEmpty()) {
          groups.add(g);
          total += g.getItems().size();
        }
      }
      if (type == null || type.isBlank() || "LEAD".equals(type)) {
        SearchResponse.SearchGroup g = leads(keyword, limit);
        if (!g.getItems().isEmpty()) {
          groups.add(g);
          total += g.getItems().size();
        }
      }
      if (type == null || type.isBlank() || "CONTACT".equals(type)) {
        SearchResponse.SearchGroup g = contacts(keyword, limit);
        if (!g.getItems().isEmpty()) {
          groups.add(g);
          total += g.getItems().size();
        }
      }
      if (type == null || type.isBlank() || "OPPORTUNITY".equals(type)) {
        SearchResponse.SearchGroup g = opportunities(keyword, limit);
        if (!g.getItems().isEmpty()) {
          groups.add(g);
          total += g.getItems().size();
        }
      }
      if (type == null || type.isBlank() || "TICKET".equals(type)) {
        SearchResponse.SearchGroup g = tickets(keyword, limit);
        if (!g.getItems().isEmpty()) {
          groups.add(g);
          total += g.getItems().size();
        }
      }
      if (type == null || type.isBlank() || "PRODUCT".equals(type)) {
        SearchResponse.SearchGroup g = products(keyword, limit);
        if (!g.getItems().isEmpty()) {
          groups.add(g);
          total += g.getItems().size();
        }
      }
    }
    resp.setGroups(groups);
    resp.setTotal(total);
    return resp;
  }

  private boolean isAdmin() {
    return SecurityUtil.currentPrincipal() != null
        && "ADMIN".equals(SecurityUtil.currentPrincipal().role());
  }

  private Long userId() {
    return SecurityUtil.currentUserId();
  }

  private SearchResponse.SearchGroup customers(String keyword, int limit) {
    LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
    for (String word : keyword.trim().split("\\s+")) {
      qw.and(
          w ->
              w.like(Customer::getName, word)
                  .or()
                  .like(Customer::getCompany, word)
                  .or()
                  .like(Customer::getContactPerson, word)
                  .or()
                  .like(Customer::getPhone, word));
    }
    if (!isAdmin()) {
      qw.and(w -> w.eq(Customer::getCreatedBy, userId()).or().eq(Customer::getOwnerId, userId()));
    }
    qw.last("LIMIT " + limit);
    SearchResponse.SearchGroup g = group("CUSTOMER", "客户");
    for (Customer c : customerMapper.selectList(qw)) {
      SearchResponse.SearchItem item = new SearchResponse.SearchItem();
      item.setId(c.getId());
      item.setTitle(c.getName());
      item.setSubtitle(c.getCompany());
      item.setPath("/customers/" + c.getId());
      g.getItems().add(item);
    }
    return g;
  }

  private SearchResponse.SearchGroup leads(String keyword, int limit) {
    LambdaQueryWrapper<Lead> qw = new LambdaQueryWrapper<>();
    for (String word : keyword.trim().split("\\s+")) {
      qw.and(w -> w.like(Lead::getName, word).or().like(Lead::getCompany, word));
    }
    if (!isAdmin()) {
      qw.and(w -> w.eq(Lead::getOwnerId, userId()).or().eq(Lead::getCreatedBy, userId()));
    }
    qw.last("LIMIT " + limit);
    SearchResponse.SearchGroup g = group("LEAD", "线索");
    for (Lead l : leadMapper.selectList(qw)) {
      SearchResponse.SearchItem item = new SearchResponse.SearchItem();
      item.setId(l.getId());
      item.setTitle(l.getName());
      item.setSubtitle(l.getCompany());
      item.setPath("/leads/" + l.getId());
      g.getItems().add(item);
    }
    return g;
  }

  private SearchResponse.SearchGroup contacts(String keyword, int limit) {
    LambdaQueryWrapper<Contact> qw = new LambdaQueryWrapper<>();
    for (String word : keyword.trim().split("\\s+")) {
      qw.and(w -> w.like(Contact::getName, word).or().like(Contact::getPhone, word));
    }
    if (!isAdmin()) {
      qw.eq(Contact::getCreatedBy, userId());
    }
    qw.last("LIMIT " + limit);
    SearchResponse.SearchGroup g = group("CONTACT", "联系人");
    for (Contact c : contactMapper.selectList(qw)) {
      SearchResponse.SearchItem item = new SearchResponse.SearchItem();
      item.setId(c.getId());
      item.setTitle(c.getName());
      item.setSubtitle(c.getTitle());
      item.setPath("/contacts/" + c.getId());
      g.getItems().add(item);
    }
    return g;
  }

  private SearchResponse.SearchGroup opportunities(String keyword, int limit) {
    LambdaQueryWrapper<Opportunity> qw = new LambdaQueryWrapper<>();
    for (String word : keyword.trim().split("\\s+")) {
      qw.and(w -> w.like(Opportunity::getName, word));
    }
    if (!isAdmin()) {
      qw.eq(Opportunity::getCreatedBy, userId());
    }
    qw.last("LIMIT " + limit);
    SearchResponse.SearchGroup g = group("OPPORTUNITY", "商机");
    for (Opportunity o : oppMapper.selectList(qw)) {
      SearchResponse.SearchItem item = new SearchResponse.SearchItem();
      item.setId(o.getId());
      item.setTitle(o.getName());
      item.setPath("/opportunities/" + o.getId());
      g.getItems().add(item);
    }
    return g;
  }

  private SearchResponse.SearchGroup tickets(String keyword, int limit) {
    LambdaQueryWrapper<Ticket> qw = new LambdaQueryWrapper<>();
    for (String word : keyword.trim().split("\\s+")) {
      qw.and(w -> w.like(Ticket::getTitle, word));
    }
    if (!isAdmin()) {
      qw.and(w -> w.eq(Ticket::getAssigneeId, userId()).or().eq(Ticket::getCreatedBy, userId()));
    }
    qw.last("LIMIT " + limit);
    SearchResponse.SearchGroup g = group("TICKET", "工单");
    for (Ticket t : ticketMapper.selectList(qw)) {
      SearchResponse.SearchItem item = new SearchResponse.SearchItem();
      item.setId(t.getId());
      item.setTitle(t.getTitle());
      item.setPath("/tickets/" + t.getId());
      g.getItems().add(item);
    }
    return g;
  }

  private SearchResponse.SearchGroup products(String keyword, int limit) {
    LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
    for (String word : keyword.trim().split("\\s+")) {
      qw.and(w -> w.like(Product::getName, word).or().like(Product::getCode, word));
    }
    qw.last("LIMIT " + limit);
    SearchResponse.SearchGroup g = group("PRODUCT", "产品");
    for (Product p : productMapper.selectList(qw)) {
      SearchResponse.SearchItem item = new SearchResponse.SearchItem();
      item.setId(p.getId());
      item.setTitle(p.getName());
      item.setSubtitle(p.getCode());
      item.setPath("/products/" + p.getId());
      g.getItems().add(item);
    }
    return g;
  }

  private SearchResponse.SearchGroup group(String type, String label) {
    SearchResponse.SearchGroup g = new SearchResponse.SearchGroup();
    g.setType(type);
    g.setLabel(label);
    g.setItems(new ArrayList<>());
    return g;
  }
}
