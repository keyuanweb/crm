package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.opportunity.CloseRequest;
import com.crm.dto.opportunity.SalesOpportunityRequest;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.CustomerMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** SalesOpportunity 状态机单元测试（T032）。 */
@ExtendWith(MockitoExtension.class)
class SalesOpportunityStateTest {

  private SalesOpportunityMapper mapper;
  private OpportunityMapper opportunityMapper;
  private CustomerMapper customerMapper;
  private SalesOpportunityService service;

  @BeforeEach
  void setUp() {
    mapper = mock(SalesOpportunityMapper.class);
    opportunityMapper = mock(OpportunityMapper.class);
    customerMapper = mock(CustomerMapper.class);
    service =
        new SalesOpportunityService(
            mapper,
            opportunityMapper,
            customerMapper,
            mock(OpportunityStatsService.class),
            mock(AuditService.class));
  }

  private SalesOpportunity active(String stage) {
    SalesOpportunity so = new SalesOpportunity();
    so.setId(1L);
    so.setOpportunityId(10L);
    so.setStage(stage);
    so.setAmount(100000L);
    return so;
  }

  @Test
  @DisplayName("更新已关闭的销售机会抛出 ALREADY_CLOSED")
  void updateClosedThrows() {
    SalesOpportunity closed = active("CLOSED_WON");
    closed.setClosedAt(LocalDateTime.now());
    when(mapper.selectById(1L)).thenReturn(closed);

    SalesOpportunityRequest req = new SalesOpportunityRequest();
    req.setStage("NEGOTIATING");
    req.setOpportunityId(10L);

    assertThatThrownBy(() -> service.update(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ALREADY_CLOSED);
  }

  @Test
  @DisplayName("更新为终态阶段抛出 STAGE_INVALID")
  void updateToTerminalStageThrows() {
    when(mapper.selectById(1L)).thenReturn(active("INITIAL_CONTACT"));
    SalesOpportunityRequest req = new SalesOpportunityRequest();
    req.setStage("CLOSED_WON");
    req.setOpportunityId(10L);
    assertThatThrownBy(() -> service.update(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.STAGE_INVALID);
  }

  @Test
  @DisplayName("关闭缺少结果抛出 CLOSE_RESULT_REQUIRED")
  void closeWithoutResultThrows() {
    when(mapper.selectById(1L)).thenReturn(active("NEGOTIATING"));
    CloseRequest req = new CloseRequest();
    req.setVersion(0);
    assertThatThrownBy(() -> service.close(1L, req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CLOSE_RESULT_REQUIRED);
  }

  @Test
  @DisplayName("关闭 WON 后写入终态、结果与关闭时间")
  void closeWonUpdatesFields() {
    SalesOpportunity so = active("NEGOTIATING");
    when(mapper.selectById(1L)).thenReturn(so);
    when(mapper.updateById(any(SalesOpportunity.class))).thenReturn(1);

    // 更新后重新查询返回关闭结果（第一次调用返回活动态，第二次返回关闭态）
    SalesOpportunity saved = active("CLOSED_WON");
    saved.setCloseResult("WON");
    saved.setClosedAt(LocalDateTime.now());
    when(mapper.selectById(1L)).thenReturn(so, saved);

    CloseRequest req = new CloseRequest();
    req.setCloseResult("WON");
    req.setVersion(1);
    var resp = service.close(1L, req);

    assertThat(resp.getStage()).isEqualTo("CLOSED_WON");
    assertThat(resp.getCloseResult()).isEqualTo("WON");
    assertThat(resp.getClosedAt()).isNotNull();
  }

  @Test
  @DisplayName("创建时父商机不存在抛出 OPPORTUNITY_NOT_FOUND")
  void createMissingParentThrows() {
    when(opportunityMapper.selectById(99L)).thenReturn(null);
    SalesOpportunityRequest req = new SalesOpportunityRequest();
    req.setOpportunityId(99L);
    req.setStage("INITIAL_CONTACT");
    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_NOT_FOUND);
  }
}
