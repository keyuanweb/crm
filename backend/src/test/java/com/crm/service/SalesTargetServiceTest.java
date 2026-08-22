package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.dto.stats.SalesTargetRequest;
import com.crm.entity.SalesTarget;
import com.crm.repository.SalesTargetMapper;
import com.crm.security.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** SalesTargetService 单元测试（006 T018）：upsert/审计/未设置返回 null。 */
@ExtendWith(MockitoExtension.class)
class SalesTargetServiceTest {

  private SalesTargetMapper targetMapper;
  private AuditService auditService;
  private DashboardStatsService dashboardStatsService;
  private SalesTargetService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    targetMapper = mock(SalesTargetMapper.class);
    auditService = mock(AuditService.class);
    dashboardStatsService = mock(DashboardStatsService.class);
    service = new SalesTargetService(targetMapper, auditService, dashboardStatsService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  @Test
  @DisplayName("未设置目标时返回 targetAmount=null")
  void getMissingReturnsNull() {
    when(targetMapper.selectOne(any())).thenReturn(null);

    var resp = service.get("2026-08");

    assertThat(resp.getTargetAmount()).isNull();
    assertThat(resp.getMonth()).isEqualTo("2026-08");
  }

  @Test
  @DisplayName("设置目标：不存在时插入并记录审计")
  void setCreates() {
    // 第一次 selectOne（set 内查重）返回 null；insert 后 get() 内再次 selectOne 返回插入结果
    SalesTarget inserted = new SalesTarget();
    inserted.setId(1L);
    inserted.setTargetMonth("2026-08");
    inserted.setTargetAmount(1000000L);
    when(targetMapper.selectOne(any())).thenReturn(null, inserted);
    when(targetMapper.insert(any(SalesTarget.class)))
        .thenAnswer(
            invocation -> {
              SalesTarget t = invocation.getArgument(0);
              t.setId(1L);
              return 1;
            });

    SalesTargetRequest req = new SalesTargetRequest();
    req.setMonth("2026-08");
    req.setTargetAmount(1000000L);
    var resp = service.set(req);

    assertThat(resp.getTargetAmount()).isEqualTo(1000000L);
    verify(targetMapper).insert(any(SalesTarget.class));
    verify(auditService).record("CREATE", "SALES_TARGET", 1L, "设置目标：2026-08=1000000");
    verify(dashboardStatsService).evict();
  }

  @Test
  @DisplayName("更新目标：已存在时更新金额并记录审计")
  void setUpdates() {
    SalesTarget existing = new SalesTarget();
    existing.setId(5L);
    existing.setTargetMonth("2026-08");
    existing.setTargetAmount(500000L);
    existing.setVersion(0);
    when(targetMapper.selectOne(any())).thenReturn(existing);
    when(targetMapper.updateById(any(SalesTarget.class))).thenReturn(1);

    SalesTargetRequest req = new SalesTargetRequest();
    req.setMonth("2026-08");
    req.setTargetAmount(2000000L);
    var resp = service.set(req);

    assertThat(resp.getTargetAmount()).isEqualTo(2000000L);
    verify(targetMapper).updateById(any(SalesTarget.class));
    verify(auditService).record("UPDATE", "SALES_TARGET", 5L, "更新目标：2026-08=2000000");
  }
}
