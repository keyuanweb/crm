/**
 * 合规导出控制器（080-data-retention，DSAR）。
 *
 * <p>支持 GDPR/个人信息保护法的数据主体导出请求。
 */
package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.security.RequirePermission;
import com.crm.service.ComplianceExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data-retention/compliance-export")
@Tag(name = "合规导出", description = "GDPR/个人信息保护合规导出")
public class ComplianceExportController {

  private final ComplianceExportService complianceExportService;

  public ComplianceExportController(ComplianceExportService complianceExportService) {
    this.complianceExportService = complianceExportService;
  }

  /**
   * 执行合规导出。
   *
   * <p><b>权限码 `export:compliance` 为既有字典项，本次只是补声明</b>（FR-G14）：本端点导出的是<b>个人信息</b> （DSAR
   * 数据主体请求），是全局认证之外还应受权限约束的破坏性/敏感性操作；改造前它没有任何权限注解，任何已登录账号 都能导出任意 `userId`
   * 的个人数据——授权实际只由"是否登录"决定。此处按既有 97 条权限码字典选取最贴合的一项， 不新增权限码（新增即需同步角色矩阵与前端，属于 FR-G14 明令排除的范围）。
   */
  @PostMapping
  @RequirePermission("export:compliance")
  @Operation(summary = "执行合规导出")
  public ApiResponse<Map<String, String>> executeExport(
      @RequestParam String entityType,
      @RequestParam String userId,
      @RequestParam(defaultValue = "CSV") String exportFormat) {
    String filePath = complianceExportService.executeExport(entityType, userId, exportFormat);
    return ApiResponse.ok(Map.of("filePath", filePath));
  }
}
