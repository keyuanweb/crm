package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.contract.AttachmentResponse;
import com.crm.dto.contract.ContractRequest;
import com.crm.dto.contract.ContractResponse;
import com.crm.entity.Contract;
import com.crm.entity.ContractAttachment;
import com.crm.entity.ContractTemplate;
import com.crm.entity.Customer;
import com.crm.entity.Quote;
import com.crm.repository.ContractAttachmentMapper;
import com.crm.repository.ContractMapper;
import com.crm.repository.ContractTemplateMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.QuoteMapper;
import com.crm.security.SecurityUtil;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 合同服务（008-contract-management，FR-CT01~CT06）：状态机/基于报价创建/审批/生效。 */
@Service
public class ContractService {

  private static final Logger log = LoggerFactory.getLogger(ContractService.class);

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_PENDING = "PENDING_APPROVAL";
  public static final String STATUS_APPROVED = "APPROVED";
  public static final String STATUS_EFFECTIVE = "EFFECTIVE";
  public static final String STATUS_COMPLETED = "COMPLETED";
  public static final String STATUS_TERMINATED = "TERMINATED";
  public static final String STATUS_REJECTED = "REJECTED";

  private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DecimalFormat AMOUNT = new DecimalFormat("#,##0.00");

  private final ContractMapper contractMapper;
  private final ContractAttachmentMapper attachmentMapper;
  private final ContractTemplateMapper templateMapper;
  private final CustomerMapper customerMapper;
  private final QuoteMapper quoteMapper;
  private final AuditService auditService;
  private final ApprovalEngineService approvalEngineService;
  private final ApprovalLaunchDelegate approvalLaunchDelegate;

  public ContractService(
      ContractMapper contractMapper,
      ContractAttachmentMapper attachmentMapper,
      ContractTemplateMapper templateMapper,
      CustomerMapper customerMapper,
      QuoteMapper quoteMapper,
      AuditService auditService,
      ApprovalEngineService approvalEngineService,
      ApprovalLaunchDelegate approvalLaunchDelegate) {
    this.contractMapper = contractMapper;
    this.attachmentMapper = attachmentMapper;
    this.templateMapper = templateMapper;
    this.customerMapper = customerMapper;
    this.quoteMapper = quoteMapper;
    this.auditService = auditService;
    this.approvalEngineService = approvalEngineService;
    this.approvalLaunchDelegate = approvalLaunchDelegate;
  }

  public PageResult<ContractResponse> page(
      String keyword, String status, Long customerId, long page, long pageSize) {
    LambdaQueryWrapper<Contract> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(w -> w.like(Contract::getContractNo, kw).or().like(Contract::getTitle, kw));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Contract::getStatus, status.trim());
    }
    if (customerId != null) {
      qw.eq(Contract::getCustomerId, customerId);
    }
    qw.orderByDesc(Contract::getId);
    Page<Contract> p = contractMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(c -> toResponse(c, false)).toList(),
        p.getTotal(),
        page,
        pageSize);
  }

  public ContractResponse detail(Long id) {
    return toResponse(require(id), true);
  }

  @Transactional
  public ContractResponse create(ContractRequest req) {
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    Long amount = req.getAmount();
    if (req.getQuoteId() != null) {
      Quote quote = quoteMapper.selectById(req.getQuoteId());
      if (quote == null) {
        throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND);
      }
      if (!QuoteService.STATUS_APPROVED.equals(quote.getStatus())) {
        throw new BusinessException(ErrorCode.QUOTE_NOT_APPROVED);
      }
      if (!req.getCustomerId().equals(quote.getCustomerId())) {
        throw new BusinessException(ErrorCode.OPPORTUNITY_CUSTOMER_MISMATCH);
      }
      amount = quote.getTotalAmount();
    }
    validateDates(req.getStartDate(), req.getEndDate());
    validateRenewedFrom(req.getRenewedFromId());

    Contract contract = new Contract();
    contract.setContractNo(nextContractNo(LocalDate.now()));
    contract.setTitle(req.getTitle().trim());
    contract.setCustomerId(req.getCustomerId());
    contract.setQuoteId(req.getQuoteId());
    contract.setAmount(amount == null ? 0L : amount);
    contract.setStartDate(req.getStartDate());
    contract.setEndDate(req.getEndDate());
    contract.setContent(buildContent(req, contract));
    contract.setStatus(STATUS_DRAFT);
    contract.setRemark(req.getRemark());
    contract.setRenewedFromId(req.getRenewedFromId());
    contract.setCreatedBy(SecurityUtil.currentUserId());
    contractMapper.insert(contract);
    auditService.record("CREATE", "CONTRACT", contract.getId(), "创建合同：" + contract.getContractNo());
    return toResponse(contractMapper.selectById(contract.getId()), true);
  }

  @Transactional
  public ContractResponse update(Long id, ContractRequest req) {
    Contract existing = require(id);
    if (!STATUS_DRAFT.equals(existing.getStatus())
        && !STATUS_REJECTED.equals(existing.getStatus())) {
      throw new BusinessException(ErrorCode.CONTRACT_INVALID_STATE);
    }
    Customer customer = customerMapper.selectById(req.getCustomerId());
    if (customer == null) {
      throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    validateDates(req.getStartDate(), req.getEndDate());
    validateRenewedFrom(req.getRenewedFromId());

    existing.setTitle(req.getTitle().trim());
    existing.setCustomerId(req.getCustomerId());
    existing.setQuoteId(req.getQuoteId());
    existing.setAmount(req.getAmount() == null ? 0L : req.getAmount());
    existing.setStartDate(req.getStartDate());
    existing.setEndDate(req.getEndDate());
    if (StringUtils.hasText(req.getContent())) {
      existing.setContent(req.getContent());
    }
    existing.setRemark(req.getRemark());
    existing.setRenewedFromId(req.getRenewedFromId());
    existing.setVersion(req.getVersion());
    int rows = contractMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "CONTRACT", id, "编辑合同：" + existing.getContractNo());
    return toResponse(contractMapper.selectById(id), true);
  }

  @Transactional
  public ContractResponse submit(Long id) {
    Contract contract = require(id);
    if (!STATUS_DRAFT.equals(contract.getStatus())
        && !STATUS_REJECTED.equals(contract.getStatus())) {
      throw new BusinessException(ErrorCode.CONTRACT_INVALID_STATE);
    }
    contractMapper.update(
        null,
        new LambdaUpdateWrapper<Contract>()
            .eq(Contract::getId, id)
            .set(Contract::getStatus, STATUS_PENDING)
            .set(Contract::getRejectReason, null));
    // 033：存在启用的合同审批流 → 自动发起审批实例（独立事务，失败不污染主事务）
    try {
      approvalLaunchDelegate.launchQuietly(
          "CONTRACT",
          id,
          "合同审批："
              + contract.getContractNo()
              + " "
              + (contract.getTitle() == null ? "" : contract.getTitle()),
          contract.getAmount() == null ? 0 : contract.getAmount());
    } catch (RuntimeException ex) {
      // 审批发起失败不影响合同提交（原审批逻辑兜底）
      log.warn("合同审批流启动失败（{}）: {}", id, ex.getMessage());
    }
    auditService.record("SUBMIT", "CONTRACT", id, "提交审批：" + contract.getContractNo());
    return toResponse(contractMapper.selectById(id), true);
  }

  @Transactional
  public ContractResponse approve(Long id) {
    Contract contract = require(id);
    if (!STATUS_PENDING.equals(contract.getStatus())) {
      throw new BusinessException(ErrorCode.CONTRACT_INVALID_STATE);
    }
    contract.setStatus(STATUS_APPROVED);
    contract.setApproverId(SecurityUtil.currentUserId());
    contract.setApprovedAt(LocalDateTime.now());
    contractMapper.updateById(contract);
    auditService.record("APPROVE", "CONTRACT", id, "审批通过：" + contract.getContractNo());
    return toResponse(contractMapper.selectById(id), true);
  }

  @Transactional
  public ContractResponse reject(Long id, String reason) {
    if (!StringUtils.hasText(reason)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    Contract contract = require(id);
    if (!STATUS_PENDING.equals(contract.getStatus())) {
      throw new BusinessException(ErrorCode.CONTRACT_INVALID_STATE);
    }
    contract.setStatus(STATUS_REJECTED);
    contract.setRejectReason(reason.trim());
    contractMapper.updateById(contract);
    auditService.record(
        "REJECT", "CONTRACT", id, "拒绝合同：" + contract.getContractNo() + "（" + reason.trim() + "）");
    return toResponse(contractMapper.selectById(id), true);
  }

  @Transactional
  public ContractResponse effective(Long id) {
    Contract contract = require(id);
    if (!STATUS_APPROVED.equals(contract.getStatus())) {
      throw new BusinessException(ErrorCode.CONTRACT_INVALID_STATE);
    }
    contract.setStatus(STATUS_EFFECTIVE);
    contract.setEffectiveAt(LocalDateTime.now());
    contractMapper.updateById(contract);
    auditService.record("EFFECTIVE", "CONTRACT", id, "合同生效：" + contract.getContractNo());
    return toResponse(contractMapper.selectById(id), true);
  }

  @Transactional
  public ContractResponse complete(Long id) {
    Contract contract = require(id);
    if (!STATUS_APPROVED.equals(contract.getStatus())
        && !STATUS_EFFECTIVE.equals(contract.getStatus())) {
      throw new BusinessException(ErrorCode.CONTRACT_INVALID_STATE);
    }
    contract.setStatus(STATUS_COMPLETED);
    contractMapper.updateById(contract);
    auditService.record("COMPLETE", "CONTRACT", id, "合同完成：" + contract.getContractNo());
    return toResponse(contractMapper.selectById(id), true);
  }

  @Transactional
  public ContractResponse terminate(Long id, String reason) {
    if (!StringUtils.hasText(reason)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    Contract contract = require(id);
    if (!STATUS_APPROVED.equals(contract.getStatus())
        && !STATUS_EFFECTIVE.equals(contract.getStatus())) {
      throw new BusinessException(ErrorCode.CONTRACT_INVALID_STATE);
    }
    contract.setStatus(STATUS_TERMINATED);
    contract.setTerminatedReason(reason.trim());
    contractMapper.updateById(contract);
    auditService.record(
        "TERMINATE",
        "CONTRACT",
        id,
        "终止合同：" + contract.getContractNo() + "（" + reason.trim() + "）");
    return toResponse(contractMapper.selectById(id), true);
  }

  public Contract require(Long id) {
    Contract contract = contractMapper.selectById(id);
    if (contract == null) {
      throw new BusinessException(ErrorCode.CONTRACT_NOT_FOUND);
    }
    return contract;
  }

  private void validateDates(LocalDate start, LocalDate end) {
    if (start != null && end != null && start.isAfter(end)) {
      throw new BusinessException(ErrorCode.AMOUNT_RANGE_INVALID);
    }
  }

  /** 正文：模板提供时替换占位符；否则用请求正文。 */
  private String buildContent(ContractRequest req, Contract contract) {
    if (req.getTemplateId() != null) {
      ContractTemplate template = templateMapper.selectById(req.getTemplateId());
      if (template == null || !"ACTIVE".equals(template.getStatus())) {
        throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
      }
      Customer customer = customerMapper.selectById(contract.getCustomerId());
      String customerName = customer == null ? "" : customer.getName();
      return ContractTemplateService.render(
          template.getContent(), customerName, contract.getContractNo(), contract.getAmount());
    }
    return req.getContent();
  }

  private String nextContractNo(LocalDate date) {
    String day = date.format(NO_FORMAT);
    String prefix = "HT-" + day + "-";
    long maxSeq = 0;
    List<Contract> existing =
        contractMapper.selectList(
            new LambdaQueryWrapper<Contract>().likeRight(Contract::getContractNo, prefix));
    for (Contract c : existing) {
      String suffix = c.getContractNo().substring(prefix.length());
      try {
        long seq = Long.parseLong(suffix);
        if (seq > maxSeq) {
          maxSeq = seq;
        }
      } catch (NumberFormatException ignored) {
        // 忽略异常编号
      }
    }
    return prefix + String.format("%04d", maxSeq + 1);
  }

  /** 单条/列表装配（详情含附件列表）。 */
  /** 续约来源合同校验（046）：存在即可。 */
  private void validateRenewedFrom(Long renewedFromId) {
    if (renewedFromId != null && contractMapper.selectById(renewedFromId) == null) {
      throw new BusinessException(ErrorCode.CONTRACT_NOT_FOUND);
    }
  }

  private ContractResponse toResponse(Contract contract, boolean withAttachments) {
    ContractResponse resp = new ContractResponse();
    resp.setId(contract.getId());
    resp.setContractNo(contract.getContractNo());
    resp.setTitle(contract.getTitle());
    resp.setCustomerId(contract.getCustomerId());
    Customer customer = customerMapper.selectById(contract.getCustomerId());
    resp.setCustomerName(customer == null ? null : customer.getName());
    resp.setQuoteId(contract.getQuoteId());
    resp.setAmount(contract.getAmount());
    resp.setStartDate(contract.getStartDate());
    resp.setEndDate(contract.getEndDate());
    resp.setContent(contract.getContent());
    resp.setStatus(contract.getStatus());
    resp.setApproverId(contract.getApproverId());
    resp.setApprovedAt(contract.getApprovedAt());
    resp.setRejectReason(contract.getRejectReason());
    resp.setEffectiveAt(contract.getEffectiveAt());
    resp.setTerminatedReason(contract.getTerminatedReason());
    resp.setRemark(contract.getRemark());
    // 046：续约来源与去向
    resp.setRenewedFromId(contract.getRenewedFromId());
    if (contract.getRenewedFromId() != null) {
      Contract from = contractMapper.selectById(contract.getRenewedFromId());
      resp.setRenewedFromNo(from == null ? null : from.getContractNo());
    }
    List<Contract> renewedBy =
        contractMapper.selectList(
            new LambdaQueryWrapper<Contract>()
                .eq(Contract::getRenewedFromId, contract.getId())
                .orderByAsc(Contract::getId));
    resp.setRenewedBy(
        renewedBy.stream()
            .map(
                c -> {
                  ContractResponse.RenewalTarget t = new ContractResponse.RenewalTarget();
                  t.setId(c.getId());
                  t.setContractNo(c.getContractNo());
                  t.setTitle(c.getTitle());
                  return t;
                })
            .toList());
    resp.setVersion(contract.getVersion());
    resp.setCreatedAt(contract.getCreatedAt());
    if (withAttachments) {
      List<ContractAttachment> attachments =
          attachmentMapper.selectList(
              new LambdaQueryWrapper<ContractAttachment>()
                  .eq(ContractAttachment::getContractId, contract.getId())
                  .orderByDesc(ContractAttachment::getId));
      resp.setAttachments(attachments.stream().map(this::toAttachmentResponse).toList());
    }
    return resp;
  }

  private AttachmentResponse toAttachmentResponse(ContractAttachment a) {
    AttachmentResponse resp = new AttachmentResponse();
    resp.setId(a.getId());
    resp.setFileName(a.getFileName());
    resp.setFileSize(a.getFileSize());
    resp.setContentType(a.getContentType());
    resp.setUploadedBy(a.getUploadedBy());
    resp.setCreatedAt(a.getCreatedAt());
    return resp;
  }
}
