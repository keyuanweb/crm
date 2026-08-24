package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.entity.SalesOpportunity;
import com.crm.repository.SalesOpportunityMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** StageConversionService 单元测试（019 T004）：历史转化率/样本不足回退/固定概率。 */
class StageConversionServiceTest {

  private SalesOpportunityMapper soMapper;
  private StageConversionService service;

  @BeforeEach
  void setUp() {
    soMapper = mock(SalesOpportunityMapper.class);
    service = new StageConversionService(soMapper);
  }

  private SalesOpportunity so(String stage) {
    SalesOpportunity s = new SalesOpportunity();
    s.setStage(stage);
    return s;
  }

  @Test
  @DisplayName("样本充足：用历史转化率（CLOSED_WON / 总量）")
  void historicalRateWhenSampleSufficient() {
    // 6 条 INITIAL_CONTACT 活跃 + 4 条 CLOSED_WON → 样本 10，转化率 0.4
    when(soMapper.selectList(any()))
        .thenReturn(
            List.of(
                so("INITIAL_CONTACT"),
                so("INITIAL_CONTACT"),
                so("INITIAL_CONTACT"),
                so("INITIAL_CONTACT"),
                so("INITIAL_CONTACT"),
                so("INITIAL_CONTACT"),
                so("CLOSED_WON"),
                so("CLOSED_WON"),
                so("CLOSED_WON"),
                so("CLOSED_WON")));

    double rate = service.probabilityFor("INITIAL_CONTACT");

    assertThat(rate).isEqualTo(0.4);
  }

  @Test
  @DisplayName("样本不足（<10）：回退默认概率 0.2")
  void fallbackDefaultWhenSampleInsufficient() {
    // 仅 3 条 INITIAL_CONTACT + 1 赢单 → 样本 4 < 10
    when(soMapper.selectList(any()))
        .thenReturn(
            List.of(
                so("INITIAL_CONTACT"),
                so("INITIAL_CONTACT"),
                so("INITIAL_CONTACT"),
                so("CLOSED_WON")));

    double rate = service.probabilityFor("INITIAL_CONTACT");

    assertThat(rate).isEqualTo(0.2);
  }

  @Test
  @DisplayName("CLOSED_WON=1.0 / CLOSED_LOST=0.0 固定")
  void fixedProbabilityForTerminalStages() {
    assertThat(service.probabilityFor("CLOSED_WON")).isEqualTo(1.0);
    assertThat(service.probabilityFor("CLOSED_LOST")).isEqualTo(0.0);
  }

  @Test
  @DisplayName("无任何机会：所有阶段回退默认")
  void noDataFallsBack() {
    when(soMapper.selectList(any())).thenReturn(List.of());

    assertThat(service.probabilityFor("INITIAL_CONTACT")).isEqualTo(0.2);
    assertThat(service.probabilityFor("NEGOTIATING")).isEqualTo(0.5);
  }
}
