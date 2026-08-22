package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.export.ExportRequest;
import com.crm.entity.ExportJob;
import com.crm.repository.ExportJobMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** ExportJobService 单元测试（016 T031）：创建/下载鉴权/状态。 */
class ExportJobServiceTest {

  private ExportJobMapper exportJobMapper;
  private ExportExecutor exportExecutor;
  private AuditService auditService;
  private ExportJobService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, ExportJob.class);
  }

  @BeforeEach
  void setUp() {
    exportJobMapper = mock(ExportJobMapper.class);
    exportExecutor = mock(ExportExecutor.class);
    auditService = mock(AuditService.class);
    service =
        new ExportJobService(exportJobMapper, exportExecutor, new ObjectMapper(), auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private ExportJob job(Long id, String status, Long createdBy, String filePath) {
    ExportJob j = new ExportJob();
    j.setId(id);
    j.setExportType("LEAD");
    j.setStatus(status);
    j.setCreatedBy(createdBy);
    j.setFilePath(filePath);
    return j;
  }

  @Test
  @DisplayName("创建任务：PENDING + 提交执行器")
  void createSucceeds() {
    when(exportJobMapper.insert(any(ExportJob.class)))
        .thenAnswer(
            invocation -> {
              ExportJob j = invocation.getArgument(0);
              j.setId(1L);
              return 1;
            });
    when(exportJobMapper.selectById(1L)).thenReturn(job(1L, "PENDING", 1L, null));

    ExportRequest req = new ExportRequest();
    req.setExportType("LEAD");
    var resp = service.create(req);

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("PENDING");
    verify(auditService).record("EXPORT", "EXPORT_JOB", 1L, "创建导出任务：LEAD");
  }

  @Test
  @DisplayName("下载：非创建人非 ADMIN → 403")
  void downloadForbidden() {
    when(exportJobMapper.selectById(1L)).thenReturn(job(1L, "DONE", 99L, "C:/x.xlsx"));
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "sales1", "SALES"));

    assertThatThrownBy(() -> service.downloadPath(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EXPORT_FORBIDDEN);
  }

  @Test
  @DisplayName("下载：任务未完成 → 409")
  void downloadNotReady() {
    when(exportJobMapper.selectById(1L)).thenReturn(job(1L, "PENDING", 1L, null));

    assertThatThrownBy(() -> service.downloadPath(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EXPORT_NOT_READY);
  }

  @Test
  @DisplayName("下载：创建人已完成 → 返回路径")
  void downloadOk() {
    when(exportJobMapper.selectById(1L)).thenReturn(job(1L, "DONE", 1L, "C:/work/exports/a.xlsx"));

    var path = service.downloadPath(1L);

    assertThat(path.toString()).contains("a.xlsx");
  }
}
