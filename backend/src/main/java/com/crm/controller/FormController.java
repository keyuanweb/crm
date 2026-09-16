package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.form.FormRequest;
import com.crm.dto.form.FormResponse;
import com.crm.dto.form.SubmissionResponse;
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.security.RequirePermission;
import com.crm.service.FormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 在线表单接口（036-online-forms）。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "在线表单")
public class FormController {

  private final FormService formService;

  public FormController(FormService formService) {
    this.formService = formService;
  }

  // ---------- 管理（form:manage） ----------

  @GetMapping("/forms")
  @RequirePermission("form:manage")
  @Operation(summary = "表单列表")
  public ApiResponse<List<FormResponse>> list() {
    return ApiResponse.ok(formService.list());
  }

  @PostMapping("/forms")
  @RequirePermission("form:manage")
  @Operation(summary = "创建表单")
  public ApiResponse<FormResponse> create(@RequestBody FormRequest request) {
    return ApiResponse.ok(formService.create(request));
  }

  @PutMapping("/forms/{id}")
  @RequirePermission("form:manage")
  @Operation(summary = "编辑表单")
  public ApiResponse<FormResponse> update(@PathVariable Long id, @RequestBody FormRequest request) {
    return ApiResponse.ok(formService.update(id, request));
  }

  @DeleteMapping("/forms/{id}")
  @RequirePermission("form:manage")
  @Operation(summary = "删除表单")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    formService.delete(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/forms/{id}/toggle")
  @RequirePermission("form:manage")
  @Operation(summary = "启用/停用表单")
  public ApiResponse<FormResponse> toggle(@PathVariable Long id) {
    return ApiResponse.ok(formService.toggle(id));
  }

  @GetMapping("/forms/{id}/submissions")
  @RequirePermission("form:manage")
  @Operation(summary = "提交记录")
  public ApiResponse<PageResult<SubmissionResponse>> submissions(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(formService.submissions(id, page, pageSize));
  }

  // ---------- 公开提交（匿名，禁 JWT） ----------

  @PostMapping("/public/forms/{id}/submit")
  @Operation(summary = "公开提交（匿名）")
  public ApiResponse<Map<String, Object>> submit(
      @PathVariable Long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
    return ApiResponse.ok(formService.submit(id, payload, request));
  }

  @GetMapping("/public/forms/{id}/meta")
  // P1（`public-read` 60/60s，按 IP）：匿名 + 打 DB，但无写副作用、非凭证端点 ⇒ 排在 P0 之后。
  // 与 `#submit` 分开成两个 scope：表单 meta 是「打开页面」的高频预取，提交是「提交一次」的低频写，
  // 共用一个桶会让预取把提交的配额挤掉（反过来也一样）——与邮件追踪/表单提交分开是同一个理由。
  @RateLimit(scope = "public-read", limit = 60, windowSeconds = 60, by = RateLimitDimension.IP)
  @Operation(summary = "公开表单元信息（字段/名称/提示）")
  public ApiResponse<Map<String, Object>> meta(@PathVariable Long id) {
    return ApiResponse.ok(
        Map.of(
            "name", formService.requirePublic(id).getName(),
            "fields", formService.fieldsOf(id),
            "successMessage", formService.requirePublic(id).getSuccessMessage()));
  }
}
