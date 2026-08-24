package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.dto.customer.HealthScoreDTO;
import com.crm.entity.HealthScoreConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HealthScoreService 单元测试（018 T004）：评分维度/阈值/边界。 */
class HealthScoreServiceTest {

  private HealthScoreService service;

  @BeforeEach
  void setUp() {
    service = new HealthScoreService();
  }

  private HealthScoreConfig config(String key, String label, int weight, String params) {
    HealthScoreConfig c = new HealthScoreConfig();
    c.setDimensionKey(key);
    c.setDimensionLabel(label);
    c.setWeight(weight);
    c.setParamsJson(params);
    c.setEnabled(1);
    return c;
  }

  /** 默认 5 维度配置（与 V42 种子一致）。 */
  private List<HealthScoreConfig> defaultConfigs() {
    return List.of(
        config("FOLLOWUP", "跟进活跃度", 30, "{\"activeDays\":30,\"zeroDays\":90}"),
        config("PAYMENT", "回款及时性", 25, "{}"),
        config("TICKET", "工单服务", 20, "{}"),
        config(
            "DEPTH",
            "合作深度",
            15,
            "{\"tiers\":[{\"amount\":1000000,\"score\":15},{\"amount\":100000,\"score\":10}]}"),
        config("ACTIVITY", "近期互动", 10, "{\"activeDays\":14}"));
  }

  @Test
  @DisplayName("健康活跃客户（近期跟进+无逾期+有订单）得高分绿色")
  void healthyCustomerScoresHigh() {
    HealthScoreDTO dto =
        service.score(
            HealthScoreService.HealthInput.builder()
                .lastFollowUpDays(5)
                .overduePaymentCount(0)
                .openTicketCount(0)
                .totalOrderAmount(2000000L)
                .lastActivityDays(5)
                .build(),
            defaultConfigs());

    assertThat(dto.getScore()).isGreaterThanOrEqualTo(80);
    assertThat(dto.getLevel()).isEqualTo("GREEN");
    assertThat(dto.getDeductions()).isEmpty();
  }

  @Test
  @DisplayName("久未跟进 + 逾期回款 + 未解决工单 → 低分红色")
  void atRiskCustomerScoresLow() {
    HealthScoreDTO dto =
        service.score(
            HealthScoreService.HealthInput.builder()
                .lastFollowUpDays(120)
                .overduePaymentCount(2)
                .openTicketCount(3)
                .totalOrderAmount(0L)
                .lastActivityDays(60)
                .build(),
            defaultConfigs());

    assertThat(dto.getScore()).isLessThan(60);
    assertThat(dto.getLevel()).isEqualTo("RED");
    assertThat(dto.getDeductions()).isNotEmpty();
  }

  @Test
  @DisplayName("无任何数据时给中性分（不报错）")
  void noDataGivesNeutralScore() {
    HealthScoreDTO dto =
        service.score(HealthScoreService.HealthInput.builder().build(), defaultConfigs());

    assertThat(dto.getScore()).isBetween(0, 100);
  }

  @Test
  @DisplayName("禁用维度不参与计分（权重缺失自动跳过）")
  void disabledDimensionSkipped() {
    List<HealthScoreConfig> configs = defaultConfigs();
    configs.get(0).setEnabled(0); // 跟进维度禁用

    HealthScoreDTO dto =
        service.score(
            HealthScoreService.HealthInput.builder()
                .lastFollowUpDays(120)
                .overduePaymentCount(0)
                .openTicketCount(0)
                .totalOrderAmount(0L)
                .lastActivityDays(5)
                .build(),
            configs);

    assertThat(dto.getScore()).isGreaterThan(0);
  }

  @Test
  @DisplayName("失分原因记录维度名与扣分数")
  void deductionsRecorded() {
    HealthScoreDTO dto =
        service.score(
            HealthScoreService.HealthInput.builder()
                .lastFollowUpDays(100)
                .overduePaymentCount(1)
                .openTicketCount(1)
                .totalOrderAmount(0L)
                .lastActivityDays(30)
                .build(),
            defaultConfigs());

    assertThat(dto.getDeductions()).isNotEmpty();
    assertThat(
            dto.getDeductions().stream().mapToInt(HealthScoreDTO.ScoreDeduction::getDeduct).sum())
        .isEqualTo(100 - dto.getScore());
  }
}
