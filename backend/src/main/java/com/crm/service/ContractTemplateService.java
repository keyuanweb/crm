package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.contract.ContractTemplateRequest;
import com.crm.dto.contract.ContractTemplateResponse;
import com.crm.entity.ContractTemplate;
import com.crm.repository.ContractTemplateMapper;
import com.crm.security.SecurityUtil;
import java.text.DecimalFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 合同模板服务（008，FR-CT08）：CRUD/占位符替换。 */
@Service
public class ContractTemplateService {

  private static final DecimalFormat AMOUNT = new DecimalFormat("#,##0.00");

  private final ContractTemplateMapper templateMapper;
  private final AuditService auditService;

  public ContractTemplateService(ContractTemplateMapper templateMapper, AuditService auditService) {
    this.templateMapper = templateMapper;
    this.auditService = auditService;
  }

  public PageResult<ContractTemplateResponse> page(
      String keyword, String status, long page, long pageSize) {
    LambdaQueryWrapper<ContractTemplate> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      qw.like(ContractTemplate::getName, keyword.trim());
    }
    if (StringUtils.hasText(status)) {
      qw.eq(ContractTemplate::getStatus, status.trim());
    }
    qw.orderByDesc(ContractTemplate::getId);
    Page<ContractTemplate> p = templateMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public ContractTemplateResponse create(ContractTemplateRequest req) {
    ContractTemplate template = new ContractTemplate();
    template.setName(req.getName().trim());
    template.setContent(req.getContent());
    template.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus().trim() : "ACTIVE");
    template.setCreatedBy(SecurityUtil.currentUserId());
    templateMapper.insert(template);
    auditService.record(
        "CREATE", "CONTRACT_TEMPLATE", template.getId(), "创建合同模板：" + template.getName());
    return toResponse(template);
  }

  @Transactional
  public ContractTemplateResponse update(Long id, ContractTemplateRequest req) {
    ContractTemplate existing = require(id);
    existing.setName(req.getName().trim());
    existing.setContent(req.getContent());
    if (StringUtils.hasText(req.getStatus())) {
      existing.setStatus(req.getStatus().trim());
    }
    existing.setVersion(req.getVersion());
    int rows = templateMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "CONTRACT_TEMPLATE", id, "编辑合同模板：" + existing.getName());
    return toResponse(templateMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    ContractTemplate template = require(id);
    templateMapper.deleteById(id);
    auditService.record("DELETE", "CONTRACT_TEMPLATE", id, "停用合同模板：" + template.getName());
  }

  public ContractTemplate require(Long id) {
    ContractTemplate template = templateMapper.selectById(id);
    if (template == null) {
      throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
    }
    return template;
  }

  /** 占位符替换：{customerName}/{contractNo}/{amount}（金额转元千分位）。 */
  public static String render(String content, String customerName, String contractNo, Long amount) {
    if (content == null) {
      return null;
    }
    String amountText = amount == null ? "-" : AMOUNT.format(amount / 100d);
    return content
        .replace("{customerName}", customerName == null ? "" : customerName)
        .replace("{contractNo}", contractNo == null ? "" : contractNo)
        .replace("{amount}", amountText);
  }

  private ContractTemplateResponse toResponse(ContractTemplate template) {
    ContractTemplateResponse resp = new ContractTemplateResponse();
    resp.setId(template.getId());
    resp.setName(template.getName());
    resp.setContent(template.getContent());
    resp.setStatus(template.getStatus());
    resp.setVersion(template.getVersion());
    resp.setCreatedAt(template.getCreatedAt());
    return resp;
  }
}
