package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.dto.merge.DuplicateGroupResponse;
import com.crm.dto.merge.MergeRequest;
import com.crm.security.RequirePermission;
import com.crm.service.CustomerMergeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户查重合并接口（034-customer-merge）。 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "客户查重合并")
public class CustomerMergeController {

  private final CustomerMergeService mergeService;

  public CustomerMergeController(CustomerMergeService mergeService) {
    this.mergeService = mergeService;
  }

  @GetMapping("/duplicates")
  @RequirePermission("customer:merge")
  @Operation(summary = "查重扫描结果")
  public ApiResponse<List<DuplicateGroupResponse>> duplicates() {
    return ApiResponse.ok(mergeService.scanDuplicates());
  }

  @PostMapping("/merge")
  @RequirePermission("customer:merge")
  @Operation(summary = "合并客户（主保留 + 从转移 + 回收站）")
  public ApiResponse<Map<String, Object>> merge(@RequestBody MergeRequest request) {
    return ApiResponse.ok(mergeService.merge(request));
  }
}
