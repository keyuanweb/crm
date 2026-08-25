package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.call.CallRecordRequest;
import com.crm.dto.call.CallRecordResponse;
import com.crm.dto.call.CallStatsResponse;
import com.crm.entity.CallRecord;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.repository.CallRecordMapper;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 通话记录服务（061，FR-C01~C05）：CRUD/归属校验/统计。 */
@Service
public class CallRecordService {

  private final CallRecordMapper recordMapper;
  private final CustomerMapper customerMapper;
  private final ContactMapper contactMapper;

  public CallRecordService(
      CallRecordMapper recordMapper, CustomerMapper customerMapper, ContactMapper contactMapper) {
    this.recordMapper = recordMapper;
    this.customerMapper = customerMapper;
    this.contactMapper = contactMapper;
  }

  public PageResult<CallRecordResponse> page(
      String keyword, String direction, Long customerId, LocalDateTime from, LocalDateTime to,
      long page, long pageSize) {
    LambdaQueryWrapper<CallRecord> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(direction)) {
      qw.eq(CallRecord::getDirection, direction.trim());
    }
    if (customerId != null) {
      qw.eq(CallRecord::getCustomerId, customerId);
    }
    if (from != null) {
      qw.ge(CallRecord::getRecordedAt, from);
    }
    if (to != null) {
      qw.le(CallRecord::getRecordedAt, to);
    }
    // 关键词按客户名过滤（先查客户 id 集合）
    if (StringUtils.hasText(keyword)) {
      List<Long> customerIds =
          customerMapper
              .selectList(
                  new LambdaQueryWrapper<Customer>()
                      .like(Customer::getName, keyword.trim())
                      .or()
                      .like(Customer::getCompany, keyword.trim()))
              .stream()
              .map(Customer::getId)
              .toList();
      if (customerIds.isEmpty()) {
        return PageResult.of(List.of(), 0, page, pageSize);
      }
      qw.in(CallRecord::getCustomerId, customerIds);
    }
    qw.orderByDesc(CallRecord::getId);
    Page<CallRecord> p = recordMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(assemble(p.getRecords()), p.getTotal(), page, pageSize);
  }

  public CallRecordResponse detail(Long id) {
    return assembleOne(require(id));
  }

  @Transactional
  public CallRecordResponse create(CallRecordRequest req) {
    validateContactBelongs(req);
    CallRecord record = new CallRecord();
    apply(req, record);
    record.setRecordedBy(SecurityUtil.currentUserId());
    record.setRecordedAt(LocalDateTime.now());
    recordMapper.insert(record);
    return assembleOne(recordMapper.selectById(record.getId()));
  }

  @Transactional
  public CallRecordResponse update(Long id, CallRecordRequest req) {
    CallRecord record = require(id);
    validateContactBelongs(req);
    apply(req, record);
    recordMapper.updateById(record);
    return assembleOne(recordMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    recordMapper.deleteById(require(id).getId());
  }

  /** 通话统计（次数/总时长/平均时长，按方向/时间）。 */
  public CallStatsResponse stats(LocalDateTime from, LocalDateTime to, String direction) {
    LambdaQueryWrapper<CallRecord> qw = new LambdaQueryWrapper<>();
    if (from != null) {
      qw.ge(CallRecord::getRecordedAt, from);
    }
    if (to != null) {
      qw.le(CallRecord::getRecordedAt, to);
    }
    List<CallRecord> records = recordMapper.selectList(qw);
    if (StringUtils.hasText(direction)) {
      records = records.stream().filter(r -> direction.equals(r.getDirection())).toList();
    }
    long totalCount = records.size();
    long totalDuration = records.stream().mapToLong(r -> r.getDurationSeconds() == null ? 0 : r.getDurationSeconds()).sum();
    long avg = totalCount == 0 ? 0 : totalDuration / totalCount;

    Map<String, Long> byDir =
        records.stream().collect(Collectors.groupingBy(CallRecord::getDirection, Collectors.counting()));

    CallStatsResponse resp = new CallStatsResponse();
    resp.setTotalCount(totalCount);
    resp.setTotalDurationSeconds(totalDuration);
    resp.setAvgDurationSeconds(avg);
    List<CallStatsResponse.DirectionCount> list = new ArrayList<>();
    byDir.forEach(
        (d, c) -> {
          CallStatsResponse.DirectionCount dc = new CallStatsResponse.DirectionCount();
          dc.setDirection(d);
          dc.setCount(c);
          list.add(dc);
        });
    resp.setByDirection(list);
    return resp;
  }

  private void validateContactBelongs(CallRecordRequest req) {
    if (req.getContactId() != null) {
      Contact contact = contactMapper.selectById(req.getContactId());
      if (contact == null || !java.util.Objects.equals(contact.getCustomerId(), req.getCustomerId())) {
        throw new BusinessException(ErrorCode.CALL_CONTACT_MISMATCH);
      }
    }
  }

  private void apply(CallRecordRequest req, CallRecord record) {
    record.setCustomerId(req.getCustomerId());
    record.setContactId(req.getContactId());
    record.setDirection(req.getDirection().trim());
    record.setDurationSeconds(req.getDurationSeconds() == null ? 0 : req.getDurationSeconds());
    record.setResult(req.getResult().trim());
    record.setRemark(req.getRemark());
  }

  private CallRecord require(Long id) {
    CallRecord record = recordMapper.selectById(id);
    if (record == null) {
      throw new BusinessException(ErrorCode.CALL_RECORD_NOT_FOUND);
    }
    return record;
  }

  private List<CallRecordResponse> assemble(List<CallRecord> records) {
    if (records.isEmpty()) {
      return List.of();
    }
    List<Long> customerIds = records.stream().map(CallRecord::getCustomerId).filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, String> customerNames =
        customerIds.isEmpty()
            ? Map.of()
            : customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
    List<Long> contactIds = records.stream().map(CallRecord::getContactId).filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, String> contactNames =
        contactIds.isEmpty()
            ? Map.of()
            : contactMapper.selectBatchIds(contactIds).stream()
                .collect(Collectors.toMap(Contact::getId, Contact::getName));
    return records.stream()
        .map(
            r -> {
              CallRecordResponse resp = new CallRecordResponse();
              resp.setId(r.getId());
              resp.setCustomerId(r.getCustomerId());
              resp.setCustomerName(r.getCustomerId() == null ? null : customerNames.get(r.getCustomerId()));
              resp.setContactId(r.getContactId());
              resp.setContactName(r.getContactId() == null ? null : contactNames.get(r.getContactId()));
              resp.setDirection(r.getDirection());
              resp.setDurationSeconds(r.getDurationSeconds());
              resp.setResult(r.getResult());
              resp.setRemark(r.getRemark());
              resp.setRecordedBy(r.getRecordedBy());
              resp.setRecordedAt(r.getRecordedAt());
              return resp;
            })
        .toList();
  }

  private CallRecordResponse assembleOne(CallRecord record) {
    return assemble(List.of(record)).get(0);
  }
}
