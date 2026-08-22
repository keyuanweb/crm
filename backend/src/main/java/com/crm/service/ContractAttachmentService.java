package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.contract.AttachmentResponse;
import com.crm.entity.Contract;
import com.crm.entity.ContractAttachment;
import com.crm.repository.ContractAttachmentMapper;
import com.crm.repository.ContractMapper;
import com.crm.security.SecurityUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 合同附件服务（008，FR-CT07）：上传/下载/删除，本地磁盘存储。 */
@Service
public class ContractAttachmentService {

  private static final Logger log = LoggerFactory.getLogger(ContractAttachmentService.class);
  private static final long MAX_SIZE = 20L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES =
      Set.of("pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx");

  private final ContractMapper contractMapper;
  private final ContractAttachmentMapper attachmentMapper;
  private final AuditService auditService;

  /** 附件存储根目录（可配置）。 */
  @Value("${crm.contract.storage-dir:./contract-files}")
  private String storageDir;

  public ContractAttachmentService(
      ContractMapper contractMapper,
      ContractAttachmentMapper attachmentMapper,
      AuditService auditService) {
    this.contractMapper = contractMapper;
    this.attachmentMapper = attachmentMapper;
    this.auditService = auditService;
  }

  @Transactional
  public AttachmentResponse upload(Long contractId, MultipartFile file) {
    Contract contract = contractMapper.selectById(contractId);
    if (contract == null) {
      throw new BusinessException(ErrorCode.CONTRACT_NOT_FOUND);
    }
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.ATTACHMENT_INVALID);
    }
    if (file.getSize() > MAX_SIZE) {
      throw new BusinessException(ErrorCode.ATTACHMENT_INVALID);
    }
    String ext = extensionOf(file.getOriginalFilename());
    if (!ALLOWED_TYPES.contains(ext)) {
      throw new BusinessException(ErrorCode.ATTACHMENT_INVALID);
    }

    String relPath =
        contractId
            + "/"
            + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            + "/"
            + UUID.randomUUID()
            + "."
            + ext;
    Path target = resolveStoragePath(relPath);
    try {
      Files.createDirectories(target.getParent());
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ex) {
      log.error("Failed to store attachment: {}", ex.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    ContractAttachment attachment = new ContractAttachment();
    attachment.setContractId(contractId);
    attachment.setFileName(file.getOriginalFilename());
    attachment.setFilePath(relPath);
    attachment.setFileSize(file.getSize());
    attachment.setContentType(file.getContentType());
    attachment.setUploadedBy(SecurityUtil.currentUserId());
    attachmentMapper.insert(attachment);
    auditService.record(
        "UPLOAD", "CONTRACT_ATTACHMENT", attachment.getId(), "上传附件：" + attachment.getFileName());
    return toResponse(attachment);
  }

  /** 下载：返回文件 Resource，供 Controller 流式输出。 */
  public DownloadResult download(Long contractId, Long attachmentId) {
    ContractAttachment attachment = require(contractId, attachmentId);
    Path path = resolveStoragePath(attachment.getFilePath());
    try {
      Resource resource = new UrlResource(path.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
      }
      return new DownloadResult(resource, attachment.getFileName(), attachment.getContentType());
    } catch (IOException ex) {
      log.error("Failed to read attachment: {}", ex.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
  }

  @Transactional
  public void delete(Long contractId, Long attachmentId) {
    ContractAttachment attachment = require(contractId, attachmentId);
    Path path = resolveStoragePath(attachment.getFilePath());
    try {
      Files.deleteIfExists(path);
    } catch (IOException ex) {
      log.warn("Failed to delete file (record will still be removed): {}", ex.getMessage());
    }
    attachmentMapper.deleteById(attachmentId);
    auditService.record(
        "DELETE", "CONTRACT_ATTACHMENT", attachmentId, "删除附件：" + attachment.getFileName());
  }

  public ContractAttachment require(Long contractId, Long attachmentId) {
    ContractAttachment attachment = attachmentMapper.selectById(attachmentId);
    if (attachment == null || !contractId.equals(attachment.getContractId())) {
      throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
    }
    return attachment;
  }

  public List<AttachmentResponse> list(Long contractId) {
    return attachmentMapper
        .selectList(
            new LambdaQueryWrapper<ContractAttachment>()
                .eq(ContractAttachment::getContractId, contractId)
                .orderByDesc(ContractAttachment::getId))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  /** 存储根目录解析 + 路径穿越防护。 */
  private Path resolveStoragePath(String relPath) {
    Path root = Paths.get(storageDir).toAbsolutePath().normalize();
    Path resolved = root.resolve(relPath).normalize();
    if (!resolved.startsWith(root)) {
      throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
    }
    return resolved;
  }

  private String extensionOf(String filename) {
    if (!StringUtils.hasText(filename)) {
      return "";
    }
    int idx = filename.lastIndexOf('.');
    if (idx < 0 || idx == filename.length() - 1) {
      return "";
    }
    return filename.substring(idx + 1).toLowerCase();
  }

  private AttachmentResponse toResponse(ContractAttachment a) {
    AttachmentResponse resp = new AttachmentResponse();
    resp.setId(a.getId());
    resp.setFileName(a.getFileName());
    resp.setFileSize(a.getFileSize());
    resp.setContentType(a.getContentType());
    resp.setUploadedBy(a.getUploadedBy());
    resp.setCreatedAt(a.getCreatedAt());
    return resp;
  }

  /** 下载结果封装。 */
  public record DownloadResult(Resource resource, String fileName, String contentType) {}
}
