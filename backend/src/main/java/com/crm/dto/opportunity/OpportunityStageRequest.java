package com.crm.dto.opportunity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 商机阶段创建/编辑请求（1.2-stage-configurable）。
 *
 * <p>{@code code} 只在创建时使用；编辑路径忽略它——编码是历史商机关联的键，不可改（见 Service 说明）。
 */
@Data
public class OpportunityStageRequest {

  /**
   * 阶段编码。限定大写字母开头的下划线大写串，与既有编码（INITIAL_CONTACT 等）保持同一形态。
   *
   * <p>这个约束不只是好看：编码会被前端当作看板列的 key、被原始 SQL 当作比较值，混入小写或空格会造出 「看着存在、匹配不上」的幽灵阶段。
   */
  @NotBlank(message = "阶段编码不能为空")
  @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,62}$", message = "阶段编码须为大写字母/数字/下划线，且以字母开头")
  private String code;

  @NotBlank(message = "阶段名称不能为空")
  @Size(max = 64, message = "阶段名称不超过 64 字")
  private String name;

  private Integer sortOrder;

  /** 预测赢率 0~1。仅在 {@code stageType=ACTIVE} 时被采纳。 */
  @DecimalMin(value = "0.0", message = "赢率不能小于 0")
  @DecimalMax(value = "1.0", message = "赢率不能大于 1")
  private BigDecimal probability;

  /** 创建时只接受 {@code ACTIVE}；不传按 {@code ACTIVE} 处理。终态不可增建（见 Service 说明）。 */
  private String stageType;
}
