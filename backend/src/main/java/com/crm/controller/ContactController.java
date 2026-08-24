package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.contact.ContactRequest;
import com.crm.dto.contact.ContactResponse;
import com.crm.dto.customer.ImportResult;
import com.crm.service.ContactExcelService;
import com.crm.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

/** 联系人接口（FR-C01~08）。 */
@RestController
@RequestMapping("/api/v1/contacts")
@Tag(name = "联系人")
public class ContactController {

  private final ContactService contactService;
  private final ContactExcelService contactExcelService;

  public ContactController(ContactService contactService, ContactExcelService contactExcelService) {
    this.contactService = contactService;
    this.contactExcelService = contactExcelService;
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "批量导入联系人（xlsx，按客户名称匹配）")
  public ApiResponse<ImportResult> importContacts(@RequestParam("file") MultipartFile file)
      throws Exception {
    return ApiResponse.ok(contactExcelService.importContacts(file.getInputStream()));
  }

  @GetMapping("/import-template")
  @Operation(summary = "下载联系人导入模板")
  public ResponseEntity<byte[]> importTemplate() {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contact-import-template.xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(contactExcelService.generateTemplate());
  }

  @GetMapping
  @Operation(summary = "分页查询联系人列表（关键字/客户/角色筛选）")
  public ApiResponse<PageResult<ContactResponse>> page(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) String role,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(contactService.page(keyword, customerId, role, page, pageSize));
  }

  @GetMapping("/{id}")
  @Operation(summary = "联系人详情")
  public ApiResponse<ContactResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(contactService.detail(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "创建联系人")
  public ApiResponse<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
    return ApiResponse.ok(contactService.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "编辑联系人")
  public ApiResponse<ContactResponse> update(
      @PathVariable Long id, @Valid @RequestBody ContactRequest request) {
    return ApiResponse.ok(contactService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "逻辑删除联系人")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    contactService.delete(id);
    return ApiResponse.ok();
  }
}
