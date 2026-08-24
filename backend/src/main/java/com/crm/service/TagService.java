package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.tag.TagRequest;
import com.crm.dto.tag.TagResponse;
import com.crm.entity.Customer;
import com.crm.entity.CustomerTag;
import com.crm.entity.Tag;
import com.crm.repository.CustomerMapper;
import com.crm.repository.CustomerTagMapper;
import com.crm.repository.TagMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 标签服务（031-customer-tags，FR-001/002/005）：标签 CRUD + 客户打标 + 标签查询。 */
@Service
public class TagService {

  private final TagMapper tagMapper;
  private final CustomerTagMapper customerTagMapper;
  private final CustomerMapper customerMapper;
  private final AuditService auditService;
  private final WorkflowEventPublisher workflowEventPublisher;

  public TagService(
      TagMapper tagMapper,
      CustomerTagMapper customerTagMapper,
      CustomerMapper customerMapper,
      AuditService auditService,
      WorkflowEventPublisher workflowEventPublisher) {
    this.tagMapper = tagMapper;
    this.customerTagMapper = customerTagMapper;
    this.customerMapper = customerMapper;
    this.auditService = auditService;
    this.workflowEventPublisher = workflowEventPublisher;
  }

  /** 标签列表（按实体类型）。 */
  public List<TagResponse> list(String entityType) {
    String type = StringUtils.hasText(entityType) ? entityType : "CUSTOMER";
    return tagMapper
        .selectList(
            new LambdaQueryWrapper<Tag>().eq(Tag::getEntityType, type).orderByAsc(Tag::getId))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  /** 创建标签。 */
  @Transactional
  public TagResponse create(TagRequest req) {
    if (!StringUtils.hasText(req.getName())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能为空");
    }
    Long exists =
        tagMapper.selectCount(
            new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, req.getName().trim())
                .eq(
                    Tag::getEntityType,
                    StringUtils.hasText(req.getEntityType()) ? req.getEntityType() : "CUSTOMER"));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "标签已存在");
    }
    Tag tag = new Tag();
    tag.setName(req.getName().trim());
    tag.setColor(req.getColor());
    tag.setEntityType(StringUtils.hasText(req.getEntityType()) ? req.getEntityType() : "CUSTOMER");
    tag.setCreatedBy(SecurityUtil.currentUserId());
    tagMapper.insert(tag);
    auditService.record("CREATE", "TAG", tag.getId(), "创建标签：" + tag.getName());
    return toResponse(tag);
  }

  /** 编辑标签。 */
  @Transactional
  public TagResponse update(Long id, TagRequest req) {
    Tag tag = require(id);
    if (StringUtils.hasText(req.getName())) {
      tag.setName(req.getName().trim());
    }
    if (req.getColor() != null) {
      tag.setColor(req.getColor());
    }
    tagMapper.updateById(tag);
    return toResponse(tag);
  }

  /** 删除标签（级联清关联）。 */
  @Transactional
  public void delete(Long id) {
    Tag tag = require(id);
    customerTagMapper.delete(new LambdaQueryWrapper<CustomerTag>().eq(CustomerTag::getTagId, id));
    tagMapper.deleteById(id);
    auditService.record("DELETE", "TAG", id, "删除标签：" + tag.getName());
  }

  /** 客户打标（覆盖式：先清后插）。 */
  @Transactional
  public void setCustomerTags(Long customerId, List<Long> tagIds) {
    requireCustomer(customerId);
    customerTagMapper.delete(
        new LambdaQueryWrapper<CustomerTag>().eq(CustomerTag::getCustomerId, customerId));
    if (tagIds != null) {
      for (Long tagId : tagIds) {
        CustomerTag ct = new CustomerTag();
        ct.setCustomerId(customerId);
        ct.setTagId(tagId);
        customerTagMapper.insert(ct);
      }
    }
    auditService.record("UPDATE", "CUSTOMER", customerId, "更新客户标签");
    // 049：标签变更发布营销事件
    List<String> names =
        tagIds == null || tagIds.isEmpty()
            ? java.util.List.of()
            : tagMapper.selectBatchIds(tagIds).stream().map(Tag::getName).toList();
    workflowEventPublisher.tagChanged(customerId, java.util.Map.of("tags", String.join(",", names)));
  }

  /** 客户标签列表。 */
  public List<TagResponse> customerTags(Long customerId) {
    List<Long> tagIds =
        customerTagMapper
            .selectList(
                new LambdaQueryWrapper<CustomerTag>().eq(CustomerTag::getCustomerId, customerId))
            .stream()
            .map(CustomerTag::getTagId)
            .toList();
    if (tagIds.isEmpty()) {
      return List.of();
    }
    return tagMapper.selectBatchIds(tagIds).stream().map(this::toResponse).toList();
  }

  /** 按标签名集合取客户 id（细分用）。 */
  public List<Long> customerIdsByTagNames(List<String> tagNames) {
    if (tagNames == null || tagNames.isEmpty()) {
      return List.of();
    }
    List<Long> tagIds =
        tagMapper
            .selectList(
                new LambdaQueryWrapper<Tag>()
                    .in(Tag::getName, tagNames)
                    .eq(Tag::getEntityType, "CUSTOMER"))
            .stream()
            .map(Tag::getId)
            .toList();
    if (tagIds.isEmpty()) {
      return List.of();
    }
    return customerTagMapper
        .selectList(new LambdaQueryWrapper<CustomerTag>().in(CustomerTag::getTagId, tagIds))
        .stream()
        .map(CustomerTag::getCustomerId)
        .distinct()
        .toList();
  }

  private Tag require(Long id) {
    Tag tag = tagMapper.selectById(id);
    if (tag == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "标签不存在");
    }
    return tag;
  }

  private void requireCustomer(Long customerId) {
    Customer c = customerMapper.selectById(customerId);
    if (c == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
  }

  private TagResponse toResponse(Tag tag) {
    TagResponse resp = new TagResponse();
    resp.setId(tag.getId());
    resp.setName(tag.getName());
    resp.setColor(tag.getColor());
    resp.setEntityType(tag.getEntityType());
    return resp;
  }
}
