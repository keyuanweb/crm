package com.crm.dto.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 产品创建/编辑请求（FR-P02/P03）。 */
@Data
public class ProductRequest {

  @NotBlank(message = "产品编码不能为空")
  @Size(max = 50, message = "编码不能超过 50 字")
  private String code;

  @NotBlank(message = "产品名称不能为空")
  @Size(max = 100, message = "名称不能超过 100 字")
  private String name;

  @Size(max = 255, message = "规格不能超过 255 字")
  private String spec;

  @Size(max = 20, message = "单位不能超过 20 字")
  private String unit;

  @Min(value = 0, message = "标准售价不能为负")
  private Long standardPrice;

  @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "状态不合法")
  private String status;

  private Integer version;
}
