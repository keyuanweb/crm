package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.entity.SlaCalendarConfig;
import com.crm.repository.SlaCalendarConfigMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SlaCalendarService 单元测试（054 T009）：跨周末/跨夜/节假日/回退。 */
class SlaCalendarServiceTest {

  private SlaCalendarConfigMapper configMapper;
  private SlaCalendarService service;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, SlaCalendarConfig.class);
  }

  @BeforeEach
  void setUp() {
    configMapper = mock(SlaCalendarConfigMapper.class);
    service = new SlaCalendarService(configMapper);
  }

  /** 配置：09:00-18:00，周一至周五，无节假日。 */
  private SlaCalendarConfig week9to18() {
    SlaCalendarConfig c = new SlaCalendarConfig();
    c.setId(1L);
    c.setEnabled(1);
    c.setWorkSlots("[{\"start\":\"09:00\",\"end\":\"18:00\"}]");
    c.setWorkDays("[1,2,3,4,5]");
    c.setHolidays("[]");
    return c;
  }

  @Test
  @DisplayName("无启用配置：回退 plusHours")
  void fallbackWhenDisabled() {
    SlaCalendarConfig c = week9to18();
    c.setEnabled(0);
    when(configMapper.selectOne(any())).thenReturn(c);

    LocalDateTime from = LocalDateTime.of(2026, 8, 25, 10, 0); // 周二 10:00
    assertThat(service.advanceWorkingTime(from, 2)).isEqualTo(from.plusHours(2));
  }

  @Test
  @DisplayName("工作时间内推进：10:00 + 2h = 12:00")
  void withinWorkHours() {
    when(configMapper.selectOne(any())).thenReturn(week9to18());
    LocalDateTime from = LocalDateTime.of(2026, 8, 25, 10, 0); // 周二
    assertThat(service.advanceWorkingTime(from, 2)).isEqualTo(LocalDateTime.of(2026, 8, 25, 12, 0));
  }

  @Test
  @DisplayName("跨非工作时间：17:00 + 2h = 次日 10:00（跳过 18:00 后）")
  void crossesEvening() {
    when(configMapper.selectOne(any())).thenReturn(week9to18());
    LocalDateTime from = LocalDateTime.of(2026, 8, 25, 17, 0); // 周二 17:00
    // 17:00-18:00 消耗 1h，剩 1h → 周三 09:00 起再 1h = 10:00
    assertThat(service.advanceWorkingTime(from, 2)).isEqualTo(LocalDateTime.of(2026, 8, 26, 10, 0));
  }

  @Test
  @DisplayName("跨周末：周五 17:00 + 2h = 周一 10:00")
  void crossesWeekend() {
    when(configMapper.selectOne(any())).thenReturn(week9to18());
    LocalDateTime from = LocalDateTime.of(2026, 8, 28, 17, 0); // 周五 17:00
    // 周五 17:00-18:00 消耗 1h，剩 1h → 周一 09:00 起 1h = 10:00
    assertThat(service.advanceWorkingTime(from, 2)).isEqualTo(LocalDateTime.of(2026, 8, 31, 10, 0));
  }

  @Test
  @DisplayName("跨节假日：节假日当天不计入工时")
  void crossesHoliday() {
    SlaCalendarConfig c = week9to18();
    c.setHolidays("[\"2026-10-01\"]");
    when(configMapper.selectOne(any())).thenReturn(c);
    // 9/30（周三）17:00 + 4h：17:00-18:00 消耗 1h，剩 3h → 10/1 假日跳过 → 10/2（周五）09:00 起 3h = 12:00
    LocalDateTime from = LocalDateTime.of(2026, 9, 30, 17, 0);
    assertThat(service.advanceWorkingTime(from, 4)).isEqualTo(LocalDateTime.of(2026, 10, 2, 12, 0));
  }

  @Test
  @DisplayName("周末提交：直接从周一 09:00 起算")
  void weekendStart() {
    when(configMapper.selectOne(any())).thenReturn(week9to18());
    LocalDateTime from = LocalDateTime.of(2026, 8, 30, 10, 0); // 周日
    assertThat(service.advanceWorkingTime(from, 2))
        .isEqualTo(LocalDateTime.of(2026, 8, 31, 11, 0)); // 周一 09:00+2h
  }
}
