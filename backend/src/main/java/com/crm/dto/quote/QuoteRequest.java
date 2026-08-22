package com.crm.dto.quote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 报价单创建/编辑请求（FR-P05/P06）。 */
@Data
public class QuoteRequest {

  @NotNull(message = "必须关联客户")
  private Long customerId;

  private Long opportunityId;

  private LocalDate validUntil;

  @Size(max = 500, message = "备注不能超过 500 字")
  private String remark;

  @NotEmpty(message = "报价单至少需要一个产品行")
  @Valid
  private List<QuoteItemRequest> items;

  private Integer version;

  @Data
  public static class QuoteItemRequest {

    private Long productId;

    @Min(value = 1, message = "数量必须 ≥1")
    private Integer quantity;

    @DecimalMin(value = "0", message = "折扣不能小于 0")
    @DecimalMax(value = "1", message = "折扣不能大于 1")
    private BigDecimal discount;
  }
}
