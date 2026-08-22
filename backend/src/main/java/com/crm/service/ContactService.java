package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.contact.ContactRequest;
import com.crm.dto.contact.ContactResponse;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 联系人服务：CRUD、逻辑删除、同客户姓名+电话唯一（FR-C01~08）。 */
@Service
public class ContactService {

  private static final String DEFAULT_ROLE = "OTHER";

  private final ContactMapper contactMapper;
  private final CustomerMapper customerMapper;
  private final AuditService auditService;

  public ContactService(
      ContactMapper contactMapper, CustomerMapper customerMapper, AuditService auditService) {
    this.contactMapper = contactMapper;
    this.customerMapper = customerMapper;
    this.auditService = auditService;
  }

  public PageResult<ContactResponse> page(
      String keyword, Long customerId, String role, long page, long pageSize) {
    LambdaQueryWrapper<Contact> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(
          w ->
              w.like(Contact::getName, kw)
                  .or()
                  .like(Contact::getPhone, kw)
                  .or()
                  .like(Contact::getEmail, kw));
    }
    if (customerId != null) {
      qw.eq(Contact::getCustomerId, customerId);
    }
    if (StringUtils.hasText(role)) {
      qw.eq(Contact::getRole, role.trim());
    }
    qw.orderByDesc(Contact::getId);
    Page<Contact> p = contactMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(toResponses(p.getRecords()), p.getTotal(), page, pageSize);
  }

  public ContactResponse detail(Long id) {
    return toResponse(require(id));
  }

  @Transactional
  public ContactResponse create(ContactRequest req) {
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    ensureUnique(null, req.getCustomerId(), req.getName(), req.getPhone());
    Contact contact = new Contact();
    apply(req, contact);
    contact.setCreatedBy(SecurityUtil.currentUserId());
    contactMapper.insert(contact);
    auditService.record("CREATE", "CONTACT", contact.getId(), "创建联系人：" + contact.getName());
    return toResponse(contact);
  }

  @Transactional
  public ContactResponse update(Long id, ContactRequest req) {
    Contact existing = require(id);
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    ensureUnique(id, req.getCustomerId(), req.getName(), req.getPhone());
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = contactMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "CONTACT", id, "编辑联系人：" + existing.getName());
    return toResponse(contactMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    Contact contact = require(id);
    contactMapper.deleteById(id);
    auditService.record("DELETE", "CONTACT", id, "删除联系人：" + contact.getName());
  }

  public Contact require(Long id) {
    Contact contact = contactMapper.selectById(id);
    if (contact == null) {
      throw new BusinessException(ErrorCode.CONTACT_NOT_FOUND);
    }
    return contact;
  }

  private void ensureUnique(Long excludeId, Long customerId, String name, String phone) {
    LambdaQueryWrapper<Contact> qw =
        new LambdaQueryWrapper<Contact>()
            .eq(Contact::getCustomerId, customerId)
            .eq(Contact::getName, name.trim());
    if (StringUtils.hasText(phone)) {
      qw.eq(Contact::getPhone, phone.trim());
    } else {
      qw.and(w -> w.isNull(Contact::getPhone).or().eq(Contact::getPhone, ""));
    }
    if (excludeId != null) {
      qw.ne(Contact::getId, excludeId);
    }
    Long count = contactMapper.selectCount(qw);
    if (count != null && count > 0) {
      throw new BusinessException(ErrorCode.CONTACT_DUPLICATE);
    }
  }

  private void apply(ContactRequest req, Contact contact) {
    contact.setCustomerId(req.getCustomerId());
    contact.setName(req.getName().trim());
    contact.setTitle(trimToNull(req.getTitle()));
    contact.setPhone(trimToNull(req.getPhone()));
    contact.setEmail(trimToNull(req.getEmail()));
    contact.setRole(
        StringUtils.hasText(req.getRole()) ? req.getRole().trim().toUpperCase() : DEFAULT_ROLE);
    contact.setRemark(trimToNull(req.getRemark()));
  }

  /** 批量装配客户名，避免 N+1（章程原则五）。 */
  private List<ContactResponse> toResponses(List<Contact> contacts) {
    if (contacts.isEmpty()) {
      return List.of();
    }
    List<Long> customerIds =
        contacts.stream().map(Contact::getCustomerId).filter(Objects::nonNull).distinct().toList();
    Map<Long, String> customerNames = new java.util.HashMap<>();
    if (!customerIds.isEmpty()) {
      for (Customer c : customerMapper.selectBatchIds(customerIds)) {
        if (c != null && c.getId() != null && c.getName() != null) {
          customerNames.put(c.getId(), c.getName());
        }
      }
    }
    return contacts.stream()
        .map(
            c -> {
              ContactResponse resp = new ContactResponse();
              copyToResponse(c, resp);
              resp.setCustomerName(customerNames.get(c.getCustomerId()));
              return resp;
            })
        .toList();
  }

  private ContactResponse toResponse(Contact contact) {
    ContactResponse resp = new ContactResponse();
    copyToResponse(contact, resp);
    Customer customer = customerMapper.selectById(contact.getCustomerId());
    resp.setCustomerName(customer == null ? null : customer.getName());
    return resp;
  }

  private void copyToResponse(Contact contact, ContactResponse resp) {
    resp.setId(contact.getId());
    resp.setCustomerId(contact.getCustomerId());
    resp.setName(contact.getName());
    resp.setTitle(contact.getTitle());
    resp.setPhone(contact.getPhone());
    resp.setEmail(contact.getEmail());
    resp.setRole(contact.getRole());
    resp.setRemark(contact.getRemark());
    resp.setVersion(contact.getVersion());
    resp.setCreatedAt(contact.getCreatedAt());
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
