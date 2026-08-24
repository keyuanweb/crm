package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.email.EmailTemplateRequest;
import com.crm.dto.email.EmailTemplateResponse;
import com.crm.entity.EmailTemplate;
import com.crm.repository.EmailTemplateMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 邮件模板服务（030-email-marketing，FR-001）：模板 CRUD + 变量渲染。 */
@Service
public class EmailTemplateService {

  private final EmailTemplateMapper templateMapper;
  private final AuditService auditService;

  public EmailTemplateService(EmailTemplateMapper templateMapper, AuditService auditService) {
    this.templateMapper = templateMapper;
    this.auditService = auditService;
  }

  public List<EmailTemplateResponse> list(String category) {
    LambdaQueryWrapper<EmailTemplate> qw =
        new LambdaQueryWrapper<EmailTemplate>().orderByAsc(EmailTemplate::getId);
    if (StringUtils.hasText(category)) {
      qw.eq(EmailTemplate::getCategory, category.trim());
    }
    return templateMapper.selectList(qw).stream().map(this::toResponse).toList();
  }

  @Transactional
  public EmailTemplateResponse create(EmailTemplateRequest req) {
    validate(req);
    Long exists =
        templateMapper.selectCount(
            new LambdaQueryWrapper<EmailTemplate>()
                .eq(EmailTemplate::getName, req.getName().trim()));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "模板名已存在");
    }
    EmailTemplate t = new EmailTemplate();
    t.setName(req.getName().trim());
    t.setSubject(req.getSubject());
    t.setContent(req.getContent());
    t.setCategory(StringUtils.hasText(req.getCategory()) ? req.getCategory() : "NOTICE");
    t.setCreatedBy(SecurityUtil.currentUserId());
    templateMapper.insert(t);
    auditService.record("CREATE", "EMAIL_TEMPLATE", t.getId(), "创建邮件模板：" + t.getName());
    return toResponse(t);
  }

  @Transactional
  public EmailTemplateResponse update(Long id, EmailTemplateRequest req) {
    EmailTemplate t = require(id);
    if (StringUtils.hasText(req.getName())) {
      t.setName(req.getName().trim());
    }
    if (req.getSubject() != null) {
      t.setSubject(req.getSubject());
    }
    if (req.getContent() != null) {
      t.setContent(req.getContent());
    }
    if (req.getCategory() != null) {
      t.setCategory(req.getCategory());
    }
    templateMapper.updateById(t);
    return toResponse(t);
  }

  @Transactional
  public void delete(Long id) {
    EmailTemplate t = require(id);
    templateMapper.deleteById(id);
    auditService.record("DELETE", "EMAIL_TEMPLATE", id, "删除邮件模板：" + t.getName());
  }

  /** 变量渲染（{name}/{company}/{phone}；未匹配保留原样）。 */
  public String renderContent(EmailTemplate template, Map<String, String> vars) {
    String content = template.getContent() == null ? "" : template.getContent();
    String subject = template.getSubject() == null ? "" : template.getSubject();
    for (Map.Entry<String, String> e : vars.entrySet()) {
      if (e.getValue() == null) {
        continue;
      }
      content = content.replace("{" + e.getKey() + "}", e.getValue());
      subject = subject.replace("{" + e.getKey() + "}", e.getValue());
    }
    return content;
  }

  public EmailTemplate require(Long id) {
    EmailTemplate t = templateMapper.selectById(id);
    if (t == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "模板不存在");
    }
    return t;
  }

  private void validate(EmailTemplateRequest req) {
    if (!StringUtils.hasText(req.getName())
        || !StringUtils.hasText(req.getSubject())
        || !StringUtils.hasText(req.getContent())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "模板名/主题/正文不能为空");
    }
  }

  private EmailTemplateResponse toResponse(EmailTemplate t) {
    EmailTemplateResponse r = new EmailTemplateResponse();
    r.setId(t.getId());
    r.setName(t.getName());
    r.setSubject(t.getSubject());
    r.setContent(t.getContent());
    r.setCategory(t.getCategory());
    return r;
  }
}
