package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 商机阶段字典（1.2-stage-configurable，表见 {@code V79__opportunity_stage.sql}）。
 *
 * <p>此前阶段词汇是代码里的常量，本类是它落表后的实体。{@code sales_opportunity.stage} 是自由文本，
 * 本表是它的取值字典，两者刻意不加外键——阶段可停用/改名，历史商机必须仍能显示。
 */
@Getter
@Setter
@TableName("opportunity_stage")
public class OpportunityStage extends BaseEntity {

  /** 阶段编码（如 INITIAL_CONTACT）。历史商机的 stage 列按它关联，故一经使用不可改。 */
  private String code;

  /** 显示名，可随时改（改它不影响历史数据）。 */
  private String name;

  /** 看板列序与漏斗顺序，小者在前。 */
  private Integer sortOrder;

  /** 预测赢率 0~1，历史样本不足时的回退值。 */
  private BigDecimal probability;

  /** {@code ACTIVE} 进行中 / {@code WON} 赢单 / {@code LOST} 输单。 */
  private String stageType;

  /** 停用后不再出现于下拉与看板列，历史商机仍照常显示。 */
  private Integer enabled;
}
