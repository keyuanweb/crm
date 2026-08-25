package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.mail.MailSyncRecordResponse;
import com.crm.entity.MailAccount;
import com.crm.entity.MailSyncRecord;
import com.crm.repository.MailSyncRecordMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 邮件同步记录服务（062，FR-E04~E06）：列表/模拟同步/删除。 */
@Service
public class MailSyncRecordService {

  private final MailSyncRecordMapper recordMapper;
  private final MailAccountService accountService;

  public MailSyncRecordService(
      MailSyncRecordMapper recordMapper, MailAccountService accountService) {
    this.recordMapper = recordMapper;
    this.accountService = accountService;
  }

  public PageResult<MailSyncRecordResponse> page(
      Long accountId, String direction, long page, long pageSize) {
    LambdaQueryWrapper<MailSyncRecord> qw =
        new LambdaQueryWrapper<MailSyncRecord>().eq(MailSyncRecord::getAccountId, accountId);
    if (direction != null && !direction.isBlank()) {
      qw.eq(MailSyncRecord::getDirection, direction.trim());
    }
    qw.orderByDesc(MailSyncRecord::getId);
    Page<MailSyncRecord> p = recordMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  /** 模拟同步：生成一条 INBOUND 记录（验证链路；真实 IMAP 对接时替换为拉取逻辑）。 */
  @Transactional
  public MailSyncRecordResponse simulateSync(Long accountId) {
    MailAccount account = accountService.require(accountId);
    MailSyncRecord record = new MailSyncRecord();
    record.setAccountId(accountId);
    record.setDirection("INBOUND");
    record.setSubject("模拟同步邮件");
    record.setFromAddress("customer@example.com");
    record.setToAddress(account.getEmail());
    record.setSyncStatus("SYNCED");
    record.setExternalId("mock-" + System.nanoTime());
    record.setSyncTime(LocalDateTime.now());
    recordMapper.insert(record);
    return toResponse(recordMapper.selectById(record.getId()));
  }

  @Transactional
  public void delete(Long accountId, Long recordId) {
    MailSyncRecord record = recordMapper.selectById(recordId);
    if (record == null || !accountId.equals(record.getAccountId())) {
      throw new BusinessException(ErrorCode.MAIL_RECORD_NOT_FOUND);
    }
    recordMapper.deleteById(recordId);
  }

  private MailSyncRecordResponse toResponse(MailSyncRecord r) {
    MailSyncRecordResponse resp = new MailSyncRecordResponse();
    resp.setId(r.getId());
    resp.setAccountId(r.getAccountId());
    resp.setDirection(r.getDirection());
    resp.setSubject(r.getSubject());
    resp.setFromAddress(r.getFromAddress());
    resp.setToAddress(r.getToAddress());
    resp.setSyncStatus(r.getSyncStatus());
    resp.setExternalId(r.getExternalId());
    resp.setSyncTime(r.getSyncTime());
    return resp;
  }
}
