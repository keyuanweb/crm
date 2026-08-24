package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.tag.SegmentRequest;
import com.crm.dto.tag.SegmentResponse;
import com.crm.security.RequirePermission;
import com.crm.service.SegmentService;
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

/** 动态细分接口（031-customer-tags）。 */
@RestController
@RequestMapping("/api/v1/segments")
@Tag(name = "客户细分")
public class SegmentController {

  private final SegmentService segmentService;

  public SegmentController(SegmentService segmentService) {
    this.segmentService = segmentService;
  }

  @GetMapping
  @Operation(summary = "细分列表（含成员数）")
  public ApiResponse<List<SegmentResponse>> list() {
    return ApiResponse.ok(segmentService.list());
  }

  @PostMapping
  @RequirePermission("tag:manage")
  @Operation(summary = "创建细分")
  public ApiResponse<SegmentResponse> create(@RequestBody SegmentRequest request) {
    return ApiResponse.ok(segmentService.create(request));
  }

  @PutMapping("/{id}")
  @RequirePermission("tag:manage")
  @Operation(summary = "编辑细分")
  public ApiResponse<SegmentResponse> update(
      @PathVariable Long id, @RequestBody SegmentRequest request) {
    return ApiResponse.ok(segmentService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequirePermission("tag:manage")
  @Operation(summary = "删除细分")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    segmentService.delete(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/{id}/members")
  @Operation(summary = "细分成员（分页）")
  public ApiResponse<PageResult<CustomerResponse>> members(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(segmentService.members(id, page, pageSize));
  }

  @GetMapping("/{id}/count")
  @Operation(summary = "细分成员数")
  public ApiResponse<Long> count(@PathVariable Long id) {
    return ApiResponse.ok(segmentService.countMembers(id));
  }
}
