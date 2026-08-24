package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.recycle.RecycleItem;
import com.crm.service.RecycleBinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 回收站接口（025-recycle-bin，contracts/recycle-bin.md，仅 ADMIN）。 */
@RestController
@RequestMapping("/api/v1/recycle-bin")
@Tag(name = "回收站")
@PreAuthorize("hasRole('ADMIN')")
public class RecycleBinController {

  private final RecycleBinService recycleBinService;

  public RecycleBinController(RecycleBinService recycleBinService) {
    this.recycleBinService = recycleBinService;
  }

  @GetMapping
  @Operation(summary = "回收站列表（跨实体，类型/关键字筛选）")
  public ApiResponse<PageResult<RecycleItem>> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(recycleBinService.list(type, keyword, page, pageSize));
  }

  @PostMapping("/restore")
  @Operation(summary = "批量恢复（deleted=0）")
  public ApiResponse<Map<String, Object>> restore(@RequestBody RestoreRequest request) {
    return ApiResponse.ok(recycleBinService.restore(request.items));
  }

  @PostMapping("/purge")
  @Operation(summary = "批量彻底删除（物理删）")
  public ApiResponse<Map<String, Object>> purge(@RequestBody RestoreRequest request) {
    return ApiResponse.ok(recycleBinService.purge(request.items));
  }

  public static class RestoreRequest {
    public List<RecycleItem> items;
  }
}
