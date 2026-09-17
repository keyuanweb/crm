package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.MailInboundNotConfiguredException;
import com.crm.common.PageResult;
import com.crm.config.MailInboundStatus;
import com.crm.dto.mail.MailSyncRecordResponse;
import com.crm.entity.MailAccount;
import com.crm.entity.MailSyncRecord;
import com.crm.repository.MailSyncRecordMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 邮件同步记录服务（062，FR-E04~E06）：列表 / 触发收信同步 / 删除。 */
@Service
public class MailSyncRecordService {

  /** 演示记录的主题前缀（101）：让记录在数据层与界面上都带演示标记，不再冒充真实收信。 */
  private static final String DEMO_SUBJECT_PREFIX = "演示同步邮件";

  private final MailSyncRecordMapper recordMapper;
  private final MailAccountService accountService;
  private final MailInboundStatus inboundStatus;

  public MailSyncRecordService(
      MailSyncRecordMapper recordMapper,
      MailAccountService accountService,
      MailInboundStatus inboundStatus) {
    this.recordMapper = recordMapper;
    this.accountService = accountService;
    this.inboundStatus = inboundStatus;
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

  /**
   * 触发收信同步（101 收信侧诚实化；062 里叫 simulateSync）。
   *
   * <p><b>两条路</b>：
   *
   * <ul>
   *   <li><b>默认</b>（{@code crm.mail.inbound.demo-enabled} 未配置）：本部署没有收信源 ⇒ 抛 {@link
   *       MailInboundNotConfiguredException}（409 + {@code
   *       MAIL_INBOUND_NOT_CONFIGURED}），<b>不写任何记录</b>。 账户存在性仍<b>先</b>判（账户不存在照旧 404
   *       MAIL_ACCOUNT_NOT_FOUND，与 062 一致）。
   *   <li><b>演示</b>（显式打开开关）：生成一条 {@link MailSyncRecord#STATUS_SIMULATED} 记录供链路演示， 主题与外部 id
   *       带演示标记。<b>真实 IMAP 对接时替换的就是这一段</b>——拉取真实邮件后逐封落库， 并把状态写成 {@link
   *       MailSyncRecord#STATUS_SYNCED}。
   * </ul>
   *
   * <p>⚠️ 默认路上"没有写记录"是<b>行为</b>而不是<b>推理</b>：断言它的方式必须是正面断言（单测 {@code never().insert}、集成 {@code total
   * == 0}），不得用"反正事务会回滚"代替（推理在重构后会静默失效）。
   */
  @Transactional
  public MailSyncRecordResponse triggerSync(Long accountId) {
    MailAccount account = accountService.require(accountId);
    if (!inboundStatus.isDemoEnabled()) {
      throw new MailInboundNotConfiguredException();
    }
    MailSyncRecord record = new MailSyncRecord();
    record.setAccountId(accountId);
    record.setDirection("INBOUND");
    record.setSubject(DEMO_SUBJECT_PREFIX + "（非真实收信）");
    record.setFromAddress("customer@example.com");
    record.setToAddress(account.getEmail());
    record.setSyncStatus(MailSyncRecord.STATUS_SIMULATED);
    record.setExternalId("demo-" + System.nanoTime());
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
