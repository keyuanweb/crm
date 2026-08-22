package com.crm.dto.marketing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 营销活动创建/编辑请求（FR-M01）。 */
@Data
public class CampaignRequest {

  @NotBlank(message = "活动名称不能为空")
  @Size(max = 100, message = "名称不能超过 100 字")
  private String name;

  @NotBlank(message = "渠道不能为空")
  @Pattern(regexp = "^(WEBSITE|AD|EXHIBITION|REFERRAL|EMAIL|SOCIAL|OTHER)$", message = "渠道不合法")
  private String channel;

  @Min(value = 0, message = "预算不能为负")
  private Long budget;

  @Min(value = 0, message = "成本不能为负")
  private Long cost;

  private LocalDate startDate;

  private LocalDate endDate;

  private Integer version;
}
