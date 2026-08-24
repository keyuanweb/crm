package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.SalesOpportunityMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 销售预测阶段转化率（019-lead-scoring，FR-005/006）：按历史销售机会统计各阶段转化率， 样本不足回退默认概率；CLOSED_WON/LOST 固定语义不校准。 */
@Service
public class StageConversionService {

  private static final Logger log = LoggerFactory.getLogger(StageConversionService.class);
  private static final int MIN_SAMPLE = 10;
  private static final Map<String, Double> DEFAULT_PROBABILITY =
      Map.of("INITIAL_CONTACT", 0.2, "NEGOTIATING", 0.5, "CLOSED_WON", 1.0, "CLOSED_LOST", 0.0);

  private final SalesOpportunityMapper soMapper;

  public StageConversionService(SalesOpportunityMapper soMapper) {
    this.soMapper = soMapper;
  }

  /** 返回某阶段的预测概率（历史转化率或默认回退）。 */
  public double probabilityFor(String stage) {
    if ("CLOSED_WON".equals(stage)) {
      return 1.0;
    }
    if ("CLOSED_LOST".equals(stage)) {
      return 0.0;
    }
    Stats stats = statsFor(stage);
    return stats == null ? DEFAULT_PROBABILITY.getOrDefault(stage, 0d) : stats.rate;
  }

  /** 该阶段概率是否来自历史统计（样本充足）。 */
  public boolean isHistorical(String stage) {
    if ("CLOSED_WON".equals(stage) || "CLOSED_LOST".equals(stage)) {
      return false;
    }
    return statsFor(stage) != null;
  }

  /** 阶段统计快照：样本充足返回转化率，否则 null。 */
  private Stats statsFor(String stage) {
    List<SalesOpportunity> all =
        soMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .in(SalesOpportunity::getStage, List.of(stage, "CLOSED_WON")));
    long active = all.stream().filter(s -> stage.equals(s.getStage())).count();
    long won = all.stream().filter(s -> "CLOSED_WON".equals(s.getStage())).count();
    long sampleL = active + won;
    if (sampleL < MIN_SAMPLE) {
      log.debug(
          "Stage {} sample insufficient ({}<{}), fallback default", stage, sampleL, MIN_SAMPLE);
      return null;
    }
    return new Stats((double) won / sampleL, (int) sampleL);
  }

  private record Stats(double rate, int sample) {}
}
