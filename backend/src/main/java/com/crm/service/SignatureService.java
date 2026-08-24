package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.signature.SignRequest;
import com.crm.dto.signature.SignatureRecordResponse;
import com.crm.entity.Contract;
import com.crm.entity.Quote;
import com.crm.entity.SignatureRecord;
import com.crm.entity.User;
import com.crm.repository.ContractMapper;
import com.crm.repository.QuoteMapper;
import com.crm.repository.SignatureRecordMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 电子签署服务（047，FR-S01~S09）：报价/合同签署与记录。 */
@Service
public class SignatureService {

  public static final String TYPE_QUOTE = "QUOTE";
  public static final String TYPE_CONTRACT = "CONTRACT";

  /** 签名图 base64 上限（500KB ≈ 683K 字符）。 */
  private static final int MAX_IMAGE_CHARS = 700_000;

  private final SignatureRecordMapper recordMapper;
  private final QuoteMapper quoteMapper;
  private final ContractMapper contractMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public SignatureService(
      SignatureRecordMapper recordMapper,
      QuoteMapper quoteMapper,
      ContractMapper contractMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.recordMapper = recordMapper;
    this.quoteMapper = quoteMapper;
    this.contractMapper = contractMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /** 签署报价单（仅 APPROVED）。 */
  @Transactional
  public SignatureRecordResponse signQuote(Long quoteId, SignRequest req) {
    ensureNotSigned(TYPE_QUOTE, quoteId);
    Quote quote = quoteMapper.selectById(quoteId);
    if (quote == null) {
      throw new BusinessException(ErrorCode.QUOTE_NOT_FOUND);
    }
    if (!QuoteService.STATUS_APPROVED.equals(quote.getStatus())) {
      throw new BusinessException(ErrorCode.SIGNATURE_STATE_INVALID);
    }
    quote.setStatus(QuoteService.STATUS_SIGNED);
    quoteMapper.updateById(quote);
    SignatureRecord record = createRecord(TYPE_QUOTE, quoteId, req);
    auditService.record("SIGN", "QUOTE", quoteId, "报价单签署");
    return toResponse(record);
  }

  /** 签署合同（仅 APPROVED；签后状态 SIGNED 才可生效）。 */
  @Transactional
  public SignatureRecordResponse signContract(Long contractId, SignRequest req) {
    ensureNotSigned(TYPE_CONTRACT, contractId);
    Contract contract = contractMapper.selectById(contractId);
    if (contract == null) {
      throw new BusinessException(ErrorCode.CONTRACT_NOT_FOUND);
    }
    if (!ContractService.STATUS_APPROVED.equals(contract.getStatus())) {
      throw new BusinessException(ErrorCode.SIGNATURE_STATE_INVALID);
    }
    contract.setStatus(ContractService.STATUS_SIGNED);
    contractMapper.updateById(contract);
    SignatureRecord record = createRecord(TYPE_CONTRACT, contractId, req);
    auditService.record("SIGN", "CONTRACT", contractId, "合同签署");
    return toResponse(record);
  }

  /** 已签署校验（提前于状态检查：已签单据返回 409）。 */
  private void ensureNotSigned(String businessType, Long businessId) {
    Long exists =
        recordMapper.selectCount(
            new LambdaQueryWrapper<SignatureRecord>()
                .eq(SignatureRecord::getBusinessType, businessType)
                .eq(SignatureRecord::getBusinessId, businessId));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.SIGNATURE_ALREADY_SIGNED);
    }
  }

  /** 查询签署记录（无则返回 null）。 */
  public SignatureRecordResponse findByBusiness(String businessType, Long businessId) {
    SignatureRecord record =
        recordMapper.selectOne(
            new LambdaQueryWrapper<SignatureRecord>()
                .eq(SignatureRecord::getBusinessType, businessType)
                .eq(SignatureRecord::getBusinessId, businessId));
    return record == null ? null : toResponse(record);
  }

  private SignatureRecord createRecord(String businessType, Long businessId, SignRequest req) {
    String image = req.getSignatureImage() == null ? "" : req.getSignatureImage().trim();
    if (image.isEmpty()) {
      throw new BusinessException(ErrorCode.SIGNATURE_IMAGE_REQUIRED);
    }
    if (image.length() > MAX_IMAGE_CHARS
        || image.getBytes(StandardCharsets.UTF_8).length > 700_000) {
      throw new BusinessException(ErrorCode.SIGNATURE_IMAGE_TOO_LARGE);
    }
    Long exists =
        recordMapper.selectCount(
            new LambdaQueryWrapper<SignatureRecord>()
                .eq(SignatureRecord::getBusinessType, businessType)
                .eq(SignatureRecord::getBusinessId, businessId));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCode.SIGNATURE_ALREADY_SIGNED);
    }
    SignatureRecord record = new SignatureRecord();
    record.setBusinessType(businessType);
    record.setBusinessId(businessId);
    Long signerId = SecurityUtil.currentUserId();
    record.setSignerId(signerId);
    User signer = signerId == null ? null : userMapper.selectById(signerId);
    record.setSignerName(signer == null ? null : signer.getDisplayName());
    record.setSignatureImage(image);
    record.setCreatedAt(LocalDateTime.now());
    recordMapper.insert(record);
    return record;
  }

  private SignatureRecordResponse toResponse(SignatureRecord record) {
    SignatureRecordResponse resp = new SignatureRecordResponse();
    resp.setId(record.getId());
    resp.setBusinessType(record.getBusinessType());
    resp.setBusinessId(record.getBusinessId());
    resp.setSignerId(record.getSignerId());
    resp.setSignerName(record.getSignerName());
    resp.setSignedAt(record.getCreatedAt());
    resp.setSignatureImage(record.getSignatureImage());
    return resp;
  }
}
