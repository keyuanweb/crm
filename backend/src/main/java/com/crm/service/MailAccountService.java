package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.mail.MailAccountRequest;
import com.crm.dto.mail.MailAccountResponse;
import com.crm.entity.MailAccount;
import com.crm.repository.MailAccountMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 邮件账户服务（062，FR-E01~E07）：CRUD/邮箱校验/默认唯一。 */
@Service
public class MailAccountService {

  private final MailAccountMapper accountMapper;

  public MailAccountService(MailAccountMapper accountMapper) {
    this.accountMapper = accountMapper;
  }

  public List<MailAccountResponse> list() {
    return accountMapper
        .selectList(new LambdaQueryWrapper<MailAccount>().orderByDesc(MailAccount::getId))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public MailAccountResponse create(MailAccountRequest req) {
    validateEmail(req.getEmail());
    Long exists =
        accountMapper.selectCount(
            new LambdaQueryWrapper<MailAccount>().eq(MailAccount::getEmail, req.getEmail().trim()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.MAIL_EMAIL_DUPLICATE);
    }
    MailAccount account = new MailAccount();
    apply(req, account);
    account.setCreatedBy(SecurityUtil.currentUserId());
    if (Boolean.TRUE.equals(req.getIsDefaultSender())) {
      clearDefaultSender();
    }
    accountMapper.insert(account);
    return toResponse(accountMapper.selectById(account.getId()));
  }

  @Transactional
  public MailAccountResponse update(Long id, MailAccountRequest req) {
    MailAccount account = require(id);
    validateEmail(req.getEmail());
    if (!req.getEmail().trim().equalsIgnoreCase(account.getEmail())) {
      Long exists =
          accountMapper.selectCount(
              new LambdaQueryWrapper<MailAccount>()
                  .eq(MailAccount::getEmail, req.getEmail().trim())
                  .ne(MailAccount::getId, id));
      if (exists != null && exists > 0) {
        throw new BusinessException(ErrorCode.MAIL_EMAIL_DUPLICATE);
      }
    }
    apply(req, account);
    if (Boolean.TRUE.equals(req.getIsDefaultSender())) {
      clearDefaultSender();
    }
    accountMapper.updateById(account);
    return toResponse(accountMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    accountMapper.deleteById(require(id).getId());
  }

  public MailAccount require(Long id) {
    MailAccount account = accountMapper.selectById(id);
    if (account == null) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_NOT_FOUND);
    }
    return account;
  }

  private void clearDefaultSender() {
    MailAccount def =
        accountMapper.selectOne(
            new LambdaQueryWrapper<MailAccount>().eq(MailAccount::getIsDefaultSender, 1).last("LIMIT 1"));
    if (def != null) {
      def.setIsDefaultSender(0);
      accountMapper.updateById(def);
    }
  }

  private void validateEmail(String email) {
    if (email == null || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
      throw new BusinessException(ErrorCode.MAIL_EMAIL_INVALID);
    }
  }

  private void apply(MailAccountRequest req, MailAccount account) {
    account.setEmail(req.getEmail().trim().toLowerCase());
    account.setDisplayName(req.getDisplayName().trim());
    account.setImapHost(req.getImapHost());
    account.setImapPort(req.getImapPort());
    account.setSmtpHost(req.getSmtpHost());
    account.setSmtpPort(req.getSmtpPort());
    account.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
    account.setIsDefaultSender(Boolean.TRUE.equals(req.getIsDefaultSender()) ? 1 : 0);
  }

  private MailAccountResponse toResponse(MailAccount a) {
    MailAccountResponse resp = new MailAccountResponse();
    resp.setId(a.getId());
    resp.setEmail(a.getEmail());
    resp.setDisplayName(a.getDisplayName());
    resp.setImapHost(a.getImapHost());
    resp.setImapPort(a.getImapPort());
    resp.setSmtpHost(a.getSmtpHost());
    resp.setSmtpPort(a.getSmtpPort());
    resp.setEnabled(a.getEnabled() != null && a.getEnabled() == 1);
    resp.setIsDefaultSender(a.getIsDefaultSender() != null && a.getIsDefaultSender() == 1);
    resp.setCreatedAt(a.getCreatedAt());
    return resp;
  }
}
