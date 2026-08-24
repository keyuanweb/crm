package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.PageResult;
import com.crm.dto.recycle.RecycleItem;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.Opportunity;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.security.SecurityUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 回收站服务（025-recycle-bin，FR-001~006）：跨实体查询已逻辑删除记录，支持恢复与彻底删除。 用 Mapper 注解 SQL 绕过 @TableLogic 过滤。ADMIN
 * 全量，SALES 仅本人（预留）。
 */
@Service
public class RecycleBinService {

  private static final Logger log = LoggerFactory.getLogger(RecycleBinService.class);

  private final CustomerMapper customerMapper;
  private final LeadMapper leadMapper;
  private final ContactMapper contactMapper;
  private final OpportunityMapper oppMapper;
  private final AuditService auditService;

  public RecycleBinService(
      CustomerMapper customerMapper,
      LeadMapper leadMapper,
      ContactMapper contactMapper,
      OpportunityMapper oppMapper,
      AuditService auditService) {
    this.customerMapper = customerMapper;
    this.leadMapper = leadMapper;
    this.contactMapper = contactMapper;
    this.oppMapper = oppMapper;
    this.auditService = auditService;
  }

  /** 回收站列表（4 实体合并，类型/关键字过滤，内存分页）。 */
  public PageResult<RecycleItem> list(String type, String keyword, long page, long pageSize) {
    Long userId = SecurityUtil.currentUserId();
    String role = SecurityUtil.currentPrincipal().role();
    boolean admin = "ADMIN".equals(role);

    List<RecycleItem> all = new ArrayList<>();
    if (type == null || type.isBlank() || "CUSTOMER".equals(type)) {
      for (Customer c :
          admin ? customerMapper.selectDeletedAll() : customerMapper.selectDeletedByUser(userId)) {
        all.add(
            new RecycleItem(
                "CUSTOMER", c.getId(), c.getName(), c.getUpdatedAt(), c.getCreatedBy()));
      }
    }
    if (type == null || type.isBlank() || "LEAD".equals(type)) {
      for (Lead l :
          admin ? leadMapper.selectDeletedAll() : leadMapper.selectDeletedByUser(userId)) {
        all.add(
            new RecycleItem("LEAD", l.getId(), l.getName(), l.getUpdatedAt(), l.getCreatedBy()));
      }
    }
    if (type == null || type.isBlank() || "CONTACT".equals(type)) {
      for (Contact c :
          admin ? contactMapper.selectDeletedAll() : contactMapper.selectDeletedByUser(userId)) {
        all.add(
            new RecycleItem("CONTACT", c.getId(), c.getName(), c.getUpdatedAt(), c.getCreatedBy()));
      }
    }
    if (type == null || type.isBlank() || "OPPORTUNITY".equals(type)) {
      for (Opportunity o :
          admin ? oppMapper.selectDeletedAll() : oppMapper.selectDeletedByUser(userId)) {
        all.add(
            new RecycleItem(
                "OPPORTUNITY", o.getId(), o.getName(), o.getUpdatedAt(), o.getCreatedBy()));
      }
    }

    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim().toLowerCase();
      all.removeIf(i -> i.getName() == null || !i.getName().toLowerCase().contains(kw));
    }

    long total = all.size();
    int from = (int) Math.min(total, (page - 1) * pageSize);
    int to = (int) Math.min(total, page * pageSize);
    List<RecycleItem> pageItems = from >= to ? List.of() : all.subList(from, to);
    log.debug("Recycle bin list: total={}", total);
    return PageResult.of(pageItems, total, page, pageSize);
  }

  /** 批量恢复（客户唯一性冲突跳过，其余恢复）。 */
  @Transactional
  public Map<String, Object> restore(List<RecycleItem> items) {
    int restored = 0;
    List<Map<String, String>> failures = new ArrayList<>();
    for (RecycleItem item : items) {
      try {
        switch (item.getType()) {
          case "CUSTOMER" -> {
            if (customerConflict(item.getId())) {
              failures.add(
                  Map.of(
                      "type",
                      item.getType(),
                      "id",
                      String.valueOf(item.getId()),
                      "message",
                      "客户名称与现有记录重复"));
              continue;
            }
            customerMapper.restoreById(item.getId());
          }
          case "LEAD" -> leadMapper.restoreById(item.getId());
          case "CONTACT" -> contactMapper.restoreById(item.getId());
          case "OPPORTUNITY" -> oppMapper.restoreById(item.getId());
          default -> throw new IllegalArgumentException("不支持的实体类型：" + item.getType());
        }
        restored++;
        auditService.record(
            "RESTORE",
            item.getType(),
            item.getId(),
            "回收站恢复：" + item.getType() + "#" + item.getId());
      } catch (Exception ex) {
        log.warn("Restore failed for {} #{}: {}", item.getType(), item.getId(), ex.getMessage());
        failures.add(
            Map.of(
                "type",
                item.getType(),
                "id",
                String.valueOf(item.getId()),
                "message",
                ex.getMessage()));
      }
    }
    return Map.of("restoredCount", restored, "failures", failures);
  }

  /** 批量彻底删除（物理删）。 */
  @Transactional
  public Map<String, Object> purge(List<RecycleItem> items) {
    int purged = 0;
    for (RecycleItem item : items) {
      try {
        switch (item.getType()) {
          case "CUSTOMER" -> customerMapper.purgeById(item.getId());
          case "LEAD" -> leadMapper.purgeById(item.getId());
          case "CONTACT" -> contactMapper.purgeById(item.getId());
          case "OPPORTUNITY" -> oppMapper.purgeById(item.getId());
          default -> throw new IllegalArgumentException("不支持的实体类型：" + item.getType());
        }
        purged++;
        auditService.record(
            "PURGE",
            item.getType(),
            item.getId(),
            "回收站彻底删除：" + item.getType() + "#" + item.getId());
      } catch (Exception ex) {
        log.warn("Purge failed for {} #{}: {}", item.getType(), item.getId(), ex.getMessage());
      }
    }
    return Map.of("purgedCount", purged);
  }

  /** 客户恢复唯一性冲突检查（查 deleted=0 是否存在同名同公司）。 */
  private boolean customerConflict(Long id) {
    Long count =
        customerMapper.selectCount(
            new LambdaQueryWrapper<Customer>()
                .eq(Customer::getName, selectNameForCheck(id))
                .eq(Customer::getCompany, selectCompanyForCheck(id))
                .ne(Customer::getId, id));
    return count != null && count > 0;
  }

  private String selectNameForCheck(Long id) {
    Customer c = customerMapper.selectById(id);
    return c == null ? "" : c.getName();
  }

  private String selectCompanyForCheck(Long id) {
    Customer c = customerMapper.selectById(id);
    return c == null ? "" : c.getCompany();
  }
}
