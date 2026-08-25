package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.entity.EmailUnsubscribe;
import com.crm.repository.EmailSendLogMapper;
import com.crm.repository.EmailUnsubscribeMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 邮件退订服务（052，FR-E01~E04）：退订/名单/恢复/排除检查。 */
@Service
public class EmailUnsubscribeService {

  private final EmailUnsubscribeMapper unsubscribeMapper;
  private final EmailSendLogMapper sendLogMapper;

  public EmailUnsubscribeService(
      EmailUnsubscribeMapper unsubscribeMapper, EmailSendLogMapper sendLogMapper) {
    this.unsubscribeMapper = unsubscribeMapper;
    this.sendLogMapper = sendLogMapper;
  }

  /** 退订（幂等：已退订直接返回）。 */
  @Transactional
  public EmailUnsubscribe unsubscribe(String email, Long campaignId) {
    if (email == null || email.isBlank()) {
      throw new BusinessException(ErrorCode.EMAIL_UNSUBSCRIBE_EMAIL_REQUIRED);
    }
    String normalized = email.trim();
    EmailUnsubscribe existing =
        unsubscribeMapper.selectOne(
            new LambdaQueryWrapper<EmailUnsubscribe>().eq(EmailUnsubscribe::getEmail, normalized));
    if (existing != null) {
      return existing;
    }
    EmailUnsubscribe record = new EmailUnsubscribe();
    record.setEmail(normalized);
    record.setCampaignId(campaignId);
    record.setUnsubscribedAt(LocalDateTime.now());
    unsubscribeMapper.insert(record);
    return record;
  }

  /** 退订名单分页。 */
  public PageResult<EmailUnsubscribe> page(String keyword, long page, long pageSize) {
    LambdaQueryWrapper<EmailUnsubscribe> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.like(EmailUnsubscribe::getEmail, keyword.trim());
    }
    qw.orderByDesc(EmailUnsubscribe::getId);
    Page<EmailUnsubscribe> p = unsubscribeMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(p.getRecords(), p.getTotal(), page, pageSize);
  }

  /** 恢复（取消退订）。 */
  @Transactional
  public void restore(Long id) {
    unsubscribeMapper.deleteById(id);
  }

  /** 邮箱是否已退订（群发/自动化发信排除）。 */
  public boolean isUnsubscribed(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    Long count =
        unsubscribeMapper.selectCount(
            new LambdaQueryWrapper<EmailUnsubscribe>()
                .eq(EmailUnsubscribe::getEmail, email.trim()));
    return count != null && count > 0;
  }

  /** 批量过滤退订邮箱（收件人 id→email 映射）。 */
  public List<Long> filterUnsubscribed(java.util.Map<Long, String> emails) {
    return emails.entrySet().stream()
        .filter(e -> !isUnsubscribed(e.getValue()))
        .map(java.util.Map.Entry::getKey)
        .toList();
  }
}
