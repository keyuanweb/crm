package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.stats.LeaderboardItem;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.SalesTarget;
import com.crm.entity.User;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.SalesTargetMapper;
import com.crm.repository.UserMapper;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 团队排行服务（020-sales-targets，FR-003/004/005）：按月统计每位销售的目标、赢单金额与达成率。 赢单按 sales_opportunity.created_by
 * 归属；实时聚合，无缓存。
 */
@Service
public class TeamLeaderboardService {

  private static final Logger log = LoggerFactory.getLogger(TeamLeaderboardService.class);

  private final SalesTargetMapper targetMapper;
  private final SalesOpportunityMapper soMapper;
  private final UserMapper userMapper;

  public TeamLeaderboardService(
      SalesTargetMapper targetMapper, SalesOpportunityMapper soMapper, UserMapper userMapper) {
    this.targetMapper = targetMapper;
    this.soMapper = soMapper;
    this.userMapper = userMapper;
  }

  /**
   * 生成某月团队排行。
   *
   * @param month YYYY-MM
   * @param sortBy rate（达成率降序，默认）/ amount（赢单金额降序）
   */
  public List<LeaderboardItem> leaderboard(String month, String sortBy) {
    YearMonth ym = YearMonth.parse(month);
    LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
    LocalDateTime monthEnd = ym.atEndOfMonth().plusDays(1).atStartOfDay();

    // 当月个人目标（user_id 非空）
    Map<Long, SalesTarget> targetsByUser =
        targetMapper
            .selectList(
                new LambdaQueryWrapper<SalesTarget>()
                    .eq(SalesTarget::getTargetMonth, month)
                    .isNotNull(SalesTarget::getUserId))
            .stream()
            .collect(Collectors.toMap(SalesTarget::getUserId, Function.identity(), (a, b) -> a));

    // 当月赢单金额按创建人归属
    Map<Long, Long> wonByUser =
        soMapper
            .selectList(
                new LambdaQueryWrapper<SalesOpportunity>()
                    .eq(SalesOpportunity::getStage, "CLOSED_WON")
                    .ge(SalesOpportunity::getClosedAt, monthStart)
                    .lt(SalesOpportunity::getClosedAt, monthEnd))
            .stream()
            .filter(so -> so.getCreatedBy() != null)
            .collect(
                Collectors.groupingBy(
                    SalesOpportunity::getCreatedBy,
                    Collectors.summingLong(so -> so.getAmount() == null ? 0L : so.getAmount())));

    List<Long> userIds = new ArrayList<>();
    userIds.addAll(targetsByUser.keySet());
    userIds.addAll(wonByUser.keySet());
    List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();

    Map<Long, User> users =
        distinct.isEmpty()
            ? Map.of()
            : userMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

    List<LeaderboardItem> items = new ArrayList<>();
    for (Long userId : distinct) {
      LeaderboardItem item = new LeaderboardItem();
      item.setUserId(userId);
      User u = users.get(userId);
      item.setDisplayName(u == null ? ("用户#" + userId) : u.getDisplayName());
      SalesTarget t = targetsByUser.get(userId);
      item.setTargetAmount(t == null ? null : t.getTargetAmount());
      long won = wonByUser.getOrDefault(userId, 0L);
      item.setWonAmount(won);
      if (t != null && t.getTargetAmount() != null && t.getTargetAmount() > 0) {
        item.setAchievementRate((double) won / t.getTargetAmount());
      } else {
        item.setAchievementRate(null);
      }
      items.add(item);
    }

    Comparator<LeaderboardItem> comparator;
    if ("amount".equalsIgnoreCase(sortBy)) {
      comparator =
          Comparator.comparing(
                  LeaderboardItem::getWonAmount, Comparator.nullsLast(Long::compareTo).reversed())
              .thenComparing(LeaderboardItem::getUserId);
    } else {
      // 达成率降序；null（无目标）排最后
      comparator =
          Comparator.comparing(
                  LeaderboardItem::getAchievementRate,
                  (a, b) -> {
                    if (a == null && b == null) return 0;
                    if (a == null) return 1; // null 排后
                    if (b == null) return -1;
                    return Double.compare(b, a); // 降序
                  })
              .thenComparing(
                  LeaderboardItem::getWonAmount,
                  (a, b) -> Long.compare(b == null ? 0L : b, a == null ? 0L : a))
              .thenComparing(LeaderboardItem::getUserId);
    }
    items.sort(comparator);
    log.debug("Leaderboard for {} generated: {} items", month, items.size());
    return items;
  }
}
