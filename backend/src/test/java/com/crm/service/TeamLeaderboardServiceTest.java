package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.dto.stats.LeaderboardItem;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.SalesTarget;
import com.crm.entity.User;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.SalesTargetMapper;
import com.crm.repository.UserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TeamLeaderboardService 单元测试（020 T003）：赢单统计/达成率/排序。 */
class TeamLeaderboardServiceTest {

  private SalesTargetMapper targetMapper;
  private SalesOpportunityMapper soMapper;
  private UserMapper userMapper;
  private TeamLeaderboardService service;

  @BeforeEach
  void setUp() {
    targetMapper = mock(SalesTargetMapper.class);
    soMapper = mock(SalesOpportunityMapper.class);
    userMapper = mock(UserMapper.class);
    service = new TeamLeaderboardService(targetMapper, soMapper, userMapper);
  }

  private SalesTarget target(Long userId, Long amount) {
    SalesTarget t = new SalesTarget();
    t.setUserId(userId);
    t.setTargetMonth("2026-08");
    t.setTargetAmount(amount);
    return t;
  }

  private SalesOpportunity won(Long createdBy, Long amount) {
    SalesOpportunity so = new SalesOpportunity();
    so.setCreatedBy(createdBy);
    so.setAmount(amount);
    so.setStage("CLOSED_WON");
    return so;
  }

  private User user(Long id, String name) {
    User u = new User();
    u.setId(id);
    u.setDisplayName(name);
    return u;
  }

  @Test
  @DisplayName("达成率 = 赢单/目标，按达成率降序；无目标排最后")
  void leaderboardSortsByRate() {
    when(targetMapper.selectList(any()))
        .thenReturn(List.of(target(1L, 1000000L), target(2L, 500000L)));
    when(soMapper.selectList(any()))
        .thenReturn(List.of(won(1L, 800000L), won(2L, 100000L), won(3L, 300000L)));
    when(userMapper.selectBatchIds(List.of(1L, 2L, 3L)))
        .thenReturn(List.of(user(1L, "张三"), user(2L, "李四"), user(3L, "王五")));

    List<LeaderboardItem> items = service.leaderboard("2026-08", "rate");

    // 张三 80% 第一，李四 20% 第二，王五无目标最后
    assertThat(items).hasSize(3);
    assertThat(items.get(0).getUserId()).isEqualTo(1L);
    assertThat(items.get(0).getAchievementRate()).isEqualTo(0.8);
    assertThat(items.get(1).getUserId()).isEqualTo(2L);
    assertThat(items.get(1).getAchievementRate()).isEqualTo(0.2);
    assertThat(items.get(2).getUserId()).isEqualTo(3L);
    assertThat(items.get(2).getTargetAmount()).isNull();
    assertThat(items.get(2).getWonAmount()).isEqualTo(300000L);
  }

  @Test
  @DisplayName("按赢单金额降序排序")
  void leaderboardSortsByAmount() {
    when(targetMapper.selectList(any())).thenReturn(List.of());
    when(soMapper.selectList(any())).thenReturn(List.of(won(1L, 300000L), won(2L, 800000L)));
    when(userMapper.selectBatchIds(List.of(1L, 2L)))
        .thenReturn(List.of(user(1L, "张三"), user(2L, "李四")));

    List<LeaderboardItem> items = service.leaderboard("2026-08", "amount");

    assertThat(items.get(0).getUserId()).isEqualTo(2L);
    assertThat(items.get(1).getUserId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("目标为 0 时达成率视为 null（避免除零）")
  void zeroTargetRateNull() {
    when(targetMapper.selectList(any())).thenReturn(List.of(target(1L, 0L)));
    when(soMapper.selectList(any())).thenReturn(List.of(won(1L, 100000L)));
    when(userMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(user(1L, "张三")));

    List<LeaderboardItem> items = service.leaderboard("2026-08", "rate");

    assertThat(items.get(0).getAchievementRate()).isNull();
  }
}
