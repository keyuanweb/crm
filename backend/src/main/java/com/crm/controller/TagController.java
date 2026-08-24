package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.tag.TagRequest;
import com.crm.dto.tag.TagResponse;
import com.crm.security.RequirePermission;
import com.crm.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 标签接口（031-customer-tags）。 */
@RestController
@RequestMapping("/api/v1/tags")
@Tag(name = "标签")
public class TagController {

  private final TagService tagService;

  public TagController(TagService tagService) {
    this.tagService = tagService;
  }

  @GetMapping
  @Operation(summary = "标签列表（按实体类型）")
  public ApiResponse<List<TagResponse>> list(@RequestParam(required = false) String entityType) {
    return ApiResponse.ok(tagService.list(entityType));
  }

  @PostMapping
  @RequirePermission("tag:manage")
  @Operation(summary = "创建标签")
  public ApiResponse<TagResponse> create(@RequestBody TagRequest request) {
    return ApiResponse.ok(tagService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("tag:manage")
  @Operation(summary = "编辑标签")
  public ApiResponse<TagResponse> update(@PathVariable Long id, @RequestBody TagRequest request) {
    return ApiResponse.ok(tagService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("tag:manage")
  @Operation(summary = "删除标签（级联清关联）")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    tagService.delete(id);
    return ApiResponse.ok(null);
  }

  @PutMapping("/customers/{customerId}/tags")
  @Operation(summary = "客户打标（覆盖式）")
  public ApiResponse<Void> setCustomerTags(
      @PathVariable Long customerId, @RequestBody TagIdsRequest request) {
    tagService.setCustomerTags(customerId, request.tagIds);
    return ApiResponse.ok(null);
  }

  @GetMapping("/customers/{customerId}/tags")
  @Operation(summary = "客户标签列表")
  public ApiResponse<List<TagResponse>> customerTags(@PathVariable Long customerId) {
    return ApiResponse.ok(tagService.customerTags(customerId));
  }

  public static class TagIdsRequest {
    public List<Long> tagIds;
  }
}
