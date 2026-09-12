package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.common.PageResult;
import com.crm.dto.recycle.RecycleItem;
import com.crm.security.RequirePermission;
import com.crm.service.RecycleBinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站接口（025-recycle-bin，contracts/recycle-bin.md）。
 *
 * <p><b>1.5：类级 {@code @PreAuthorize("hasRole('ADMIN')")} 换成矩阵里的三个码</b>——读用 {@code recycle:view}、
 * 恢复用 {@code recycle:restore}、物理删除用 {@code recycle:purge}。可访问范围不变：「回收站」菜单只有 ADMIN
 * 持有，而这三个码至今无人被授（V46/V75/V80 都没有），所以今天仍是"只有 ADMIN 能用"；区别是管理员从此可以
 * 在角色页上把它们勾给别的角色，而不用改代码。读与写刻意分成两个码：跨实体列出已删除数据、把数据物理 删除，是两种风险等级，不该由同一个码同时放行。
 */
@RestController
@RequestMapping("/api/v1/recycle-bin")
@Tag(name = "回收站")
public class RecycleBinController {

  private final RecycleBinService recycleBinService;

  public RecycleBinController(RecycleBinService recycleBinService) {
    this.recycleBinService = recycleBinService;
  }

  @GetMapping
  @RequirePermission("recycle:view")
  @Operation(summary = "回收站列表（跨实体，类型/关键字筛选）")
  public ApiResponse<PageResult<RecycleItem>> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(recycleBinService.list(type, keyword, page, pageSize));
  }

  @PostMapping("/restore")
  @RequirePermission("recycle:restore")
  @Operation(summary = "批量恢复（deleted=0）")
  public ApiResponse<Map<String, Object>> restore(@RequestBody RestoreRequest request) {
    return ApiResponse.ok(recycleBinService.restore(request.items));
  }

  @PostMapping("/purge")
  @RequirePermission("recycle:purge")
  @Operation(summary = "批量彻底删除（物理删）")
  public ApiResponse<Map<String, Object>> purge(@RequestBody RestoreRequest request) {
    return ApiResponse.ok(recycleBinService.purge(request.items));
  }

  public static class RestoreRequest {
    public List<RecycleItem> items;
  }
}
