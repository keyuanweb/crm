package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.mail.MailAccountRequest;
import com.crm.dto.mail.MailAccountResponse;
import com.crm.dto.mail.MailSyncRecordResponse;
import com.crm.service.MailAccountService;
import com.crm.service.MailSyncRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 邮件账户与同步记录接口（062）。 */
@RestController
@RequestMapping("/api/v1/mail-accounts")
@Tag(name = "邮件同步")
public class MailAccountController {

  private final MailAccountService accountService;
  private final MailSyncRecordService recordService;

  public MailAccountController(
      MailAccountService accountService, MailSyncRecordService recordService) {
    this.accountService = accountService;
    this.recordService = recordService;
  }

  // ===== 账户配置（仅 ADMIN） =====

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "邮件账户列表")
  public ApiResponse<List<MailAccountResponse>> list() {
    return ApiResponse.ok(accountService.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "创建邮件账户")
  public ApiResponse<MailAccountResponse> create(@Valid @RequestBody MailAccountRequest request) {
    return ApiResponse.ok(accountService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "编辑邮件账户")
  public ApiResponse<MailAccountResponse> update(
      @PathVariable Long id, @Valid @RequestBody MailAccountRequest request) {
    return ApiResponse.ok(accountService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "删除邮件账户")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    accountService.delete(id);
    return ApiResponse.ok(null);
  }

  // ===== 同步记录（ADMIN + SALES） =====

  @PostMapping("/{id}/sync")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "模拟同步（生成 INBOUND 记录验证链路）")
  public ApiResponse<MailSyncRecordResponse> sync(@PathVariable Long id) {
    return ApiResponse.ok(recordService.simulateSync(id));
  }

  @GetMapping("/{id}/records")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "账户同步记录")
  public ApiResponse<PageResult<MailSyncRecordResponse>> records(
      @PathVariable Long id,
      @RequestParam(required = false) String direction,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(recordService.page(id, direction, page, pageSize));
  }

  @DeleteMapping("/{id}/records/{recordId}")
  @PreAuthorize("hasAnyRole('ADMIN','SALES')")
  @Operation(summary = "删除同步记录")
  public ApiResponse<Void> deleteRecord(@PathVariable Long id, @PathVariable Long recordId) {
    recordService.delete(id, recordId);
    return ApiResponse.ok(null);
  }
}
