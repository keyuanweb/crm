package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.mail.MailAccountRequest;
import com.crm.dto.mail.MailAccountResponse;
import com.crm.dto.mail.MailSyncRecordResponse;
import com.crm.security.RequirePermission;
import com.crm.service.MailAccountService;
import com.crm.service.MailSyncRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/**
 * 邮件账户与同步记录接口（062）。
 *
 * <p><b>1.5 批 3：整类的角色字面量换成两个新码，且刻意拆成两个而不是一个。</b>本类改造前的门是"两半两制"： 账户配置四个端点是 {@code
 * hasRole('ADMIN')}，同步记录三个端点是 {@code hasAnyRole('ADMIN','SALES')}。
 *
 * <ul>
 *   <li>{@code mail_account:manage}（账户配置）→ 不授给任何角色，范围与改造前一致（仅 ADMIN）。它是**平台级 配置**：SMTP/IMAP
 *       主机、发件地址、默认发件人——改错一次全公司的邮件链路就哑了，与 {@code integration:manage} 同类。
 *   <li>{@code mail_sync:manage}（同步记录）→ 按判据①补授 ADMIN + SALES，一个不多一个不少，与改造前那道 {@code hasAnyRole}
 *       完全一致。
 * </ul>
 *
 * <p><b>为什么不能合成一个码</b>：合并后 SALES 要么能改账户配置（扩权，越过了改造前的 ADMIN 门），要么丢掉 模拟同步（把能用的打成 403）。这正是 1.5
 * 里"能看≠能开"那条原则在配置面与业务面上的第二次应用。
 *
 * <p><b>读端点的码与写共用</b>（账户列表挂 {@code mail_account:manage}）：本模块没有只读消费者—— 「邮件同步」菜单（{@code
 * mail-sync}）在种子里没有任何角色持有，页面本就不对非管理员开放。若将来把它授给 只读角色，此处应拆出 {@code mail_account:read}（同 invoice:read
 * 的处置）。
 */
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

  // ===== 账户配置（平台级，仅 ADMIN） =====

  @GetMapping
  @RequirePermission("mail_account:manage")
  @Operation(summary = "邮件账户列表")
  public ApiResponse<List<MailAccountResponse>> list() {
    return ApiResponse.ok(accountService.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequirePermission("mail_account:manage")
  @Operation(summary = "创建邮件账户")
  public ApiResponse<MailAccountResponse> create(@Valid @RequestBody MailAccountRequest request) {
    return ApiResponse.ok(accountService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("mail_account:manage")
  @Operation(summary = "编辑邮件账户")
  public ApiResponse<MailAccountResponse> update(
      @PathVariable Long id, @Valid @RequestBody MailAccountRequest request) {
    return ApiResponse.ok(accountService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("mail_account:manage")
  @Operation(summary = "删除邮件账户")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    accountService.delete(id);
    return ApiResponse.ok(null);
  }

  // ===== 同步记录（ADMIN + SALES） =====

  @PostMapping("/{id}/sync")
  @RequirePermission("mail_sync:manage")
  @Operation(summary = "模拟同步（生成 INBOUND 记录验证链路）")
  public ApiResponse<MailSyncRecordResponse> sync(@PathVariable Long id) {
    return ApiResponse.ok(recordService.simulateSync(id));
  }

  @GetMapping("/{id}/records")
  @RequirePermission("mail_sync:manage")
  @Operation(summary = "账户同步记录")
  public ApiResponse<PageResult<MailSyncRecordResponse>> records(
      @PathVariable Long id,
      @RequestParam(required = false) String direction,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(recordService.page(id, direction, page, pageSize));
  }

  @DeleteMapping("/{id}/records/{recordId}")
  @RequirePermission("mail_sync:manage")
  @Operation(summary = "删除同步记录")
  public ApiResponse<Void> deleteRecord(@PathVariable Long id, @PathVariable Long recordId) {
    recordService.delete(id, recordId);
    return ApiResponse.ok(null);
  }
}
