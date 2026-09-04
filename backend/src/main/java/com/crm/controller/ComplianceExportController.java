/** 合规导出控制器（080-data-retention，DSAR）。

支持 GDPR/个人信息保护法的数据主体导出请求。
 */
package com.crm.controller;

import com.crm.common.ApiResponse;
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

  @PostMapping
  @Operation(summary = "执行合规导出")
  public ApiResponse<Map<String, String>> executeExport(
      @RequestParam String entityType,
      @RequestParam String userId,
      @RequestParam(defaultValue = "CSV") String exportFormat) {
    String filePath = complianceExportService.executeExport(entityType, userId, exportFormat);
    return ApiResponse.ok(Map.of("filePath", filePath));
  }
}
