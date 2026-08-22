package com.crm.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 订单创建/编辑请求（FR-OP02/OP04）。 */
@Data
public class OrderRequest {

  @NotBlank(message = "订单标题不能为空")
  @Size(max = 200, message = "标题不能超过 200 字")
  private String title;

  @NotNull(message = "必须关联客户")
  private Long customerId;

  /** 关联合同（可选，须生效中）。 */
  private Long contractId;

  @Min(value = 0, message = "订单金额不能为负")
  private Long amount;

  @Size(max = 500, message = "说明不能超过 500 字")
  private String description;

  /** 回款计划期次（可选，为空时自动一期；金额合计须=订单金额）。 */
  @Valid private List<PlanItemRequest> plans;

  private Integer version;

  @Data
  public static class PlanItemRequest {

    @Min(value = 1, message = "期次金额必须 >0")
    private Long amount;

    @NotNull(message = "计划回款日期不能为空")
    private LocalDate dueDate;

    @Size(max = 200, message = "期次说明不能超过 200 字")
    private String description;
  }
}
