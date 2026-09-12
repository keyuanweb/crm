package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.SalesOpportunityMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 销售预测阶段转化率（019-lead-scoring，FR-005/006）：按历史销售机会统计各阶段转化率， 样本不足回退默认概率；CLOSED_WON/LOST 固定语义不校准。 */
@Service
public class StageConversionService {

  private static final Logger log = LoggerFactory.getLogger(StageConversionService.class);
  private static final int MIN_SAMPLE = 10;

  private final SalesOpportunityMapper soMapper;
  private final OpportunityStageService stageService;

  public StageConversionService(
      SalesOpportunityMapper soMapper, OpportunityStageService stageService) {
    this.soMapper = soMapper;
    this.stageService = stageService;
  }

  /**
   * 返回某阶段的预测概率（历史转化率或默认回退）。
   *
   * <p>回退值来自阶段字典的 {@code probability} 列（1.2 起），不再是代码里的常量表—— 样本不足（&lt;10）的阶段会落到它，
   * 所以改字典里的赢率等于直接改预测数字。
   */
  public double probabilityFor(String stage) {
    if (stageService.isTerminal(stage)) {
      // 终态语义确定（1 / 0），不参与历史校准——让「赢单」的概率随历史漂移毫无意义。
      return stageService.probabilityOf(stage);
    }
    Stats stats = statsFor(stage);
    return stats == null ? stageService.probabilityOf(stage) : stats.rate;
  }

  /** 该阶段概率是否来自历史统计（样本充足）。 */
  public boolean isHistorical(String stage) {
    if (stageService.isTerminal(stage)) {
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
