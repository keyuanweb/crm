package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.dto.customer.HealthScoreDTO;
import com.crm.entity.HealthScoreConfig;
import com.crm.entity.SalesOrder;
import com.crm.repository.ContractMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.HealthScoreConfigMapper;
import com.crm.repository.PaymentPlanMapper;
import com.crm.repository.PaymentRecordMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.repository.TicketMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Customer360Service.healthLevelsBatch 单元测试（性能优化）：批量装配健康度等级。 */
class Customer360ServiceTest {

  private SalesOrderMapper orderMapper;
  private PaymentPlanMapper planMapper;
  private PaymentRecordMapper recordMapper;
  private ContractMapper contractMapper;
  private TicketMapper ticketMapper;
  private FollowUpMapper followUpMapper;
  private HealthScoreConfigMapper configMapper;
  private HealthScoreService healthScoreService;
  private Customer360Service service;

  @BeforeEach
  void setUp() {
    orderMapper = mock(SalesOrderMapper.class);
    planMapper = mock(PaymentPlanMapper.class);
    recordMapper = mock(PaymentRecordMapper.class);
    contractMapper = mock(ContractMapper.class);
    ticketMapper = mock(TicketMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    configMapper = mock(HealthScoreConfigMapper.class);
    healthScoreService = mock(HealthScoreService.class);
    service =
        new Customer360Service(
            orderMapper,
            planMapper,
            recordMapper,
            contractMapper,
            ticketMapper,
            followUpMapper,
            configMapper,
            healthScoreService);
  }

  private SalesOrder order(Long id, Long customerId, Long amount) {
    SalesOrder o = new SalesOrder();
    o.setId(id);
    o.setCustomerId(customerId);
    o.setAmount(amount);
    o.setUpdatedAt(LocalDateTime.now());
    return o;
  }

  private HealthScoreDTO health(String level) {
    HealthScoreDTO h = new HealthScoreDTO();
    h.setLevel(level);
    return h;
  }

  @Test
  @DisplayName("空输入返回空 Map 且不查库")
  void emptyInputNoQuery() {
    Map<Long, String> result = service.healthLevelsBatch(List.of());

    assertThat(result).isEmpty();
    verify(orderMapper, never()).selectList(any());
  }

  @Test
  @DisplayName("多客户批量装配：按客户分别评分")
  void batchLevelsPerCustomer() {
    when(orderMapper.selectList(any()))
        .thenReturn(List.of(order(1L, 10L, 100000L), order(2L, 11L, 200000L)));
    when(planMapper.selectList(any())).thenReturn(List.of());
    when(recordMapper.selectList(any())).thenReturn(List.of());
    when(ticketMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(configMapper.selectList(any())).thenReturn(List.of(new HealthScoreConfig()));
    when(healthScoreService.score(any(), any()))
        .thenReturn(health("GREEN"))
        .thenReturn(health("RED"));

    Map<Long, String> result = service.healthLevelsBatch(List.of(10L, 11L));

    assertThat(result).hasSize(2);
    assertThat(result.get(10L)).isEqualTo("GREEN");
    assertThat(result.get(11L)).isEqualTo("RED");
  }

  @Test
  @DisplayName("无关联数据客户仍评分（健康输入全默认）")
  void noRelationDataStillScores() {
    when(orderMapper.selectList(any())).thenReturn(List.of());
    when(planMapper.selectList(any())).thenReturn(List.of());
    when(recordMapper.selectList(any())).thenReturn(List.of());
    when(ticketMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(configMapper.selectList(any())).thenReturn(List.of(new HealthScoreConfig()));
    when(healthScoreService.score(any(), any())).thenReturn(health("YELLOW"));

    Map<Long, String> result = service.healthLevelsBatch(List.of(99L));

    assertThat(result.get(99L)).isEqualTo("YELLOW");
  }
}
