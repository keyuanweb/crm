package com.crm.dto.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 报表查询请求（021-custom-reports，FR-001/002/003）。 */
@Data
public class ReportQuery {

  /** SALES / PRODUCT / SOURCE / STAGE / TIME。 */
  @NotBlank(message = "维度不能为空")
  private String dimension;

  /** COUNT / AMOUNT。 */
  @NotBlank(message = "指标不能为空")
  private String metric;

  /** DAY / MONTH（dimension=TIME 时必填）。 */
  private String granularity;

  private String startDate;

  private String endDate;

  /** 阶段过滤（可选）：INITIAL_CONTACT/NEGOTIATING/CLOSED_WON/CLOSED_LOST。 */
  private String stageFilter;

  public static final String DIMENSION_SALES = "SALES";
  public static final String DIMENSION_PRODUCT = "PRODUCT";
  public static final String DIMENSION_SOURCE = "SOURCE";
  public static final String DIMENSION_STAGE = "STAGE";
  public static final String DIMENSION_TIME = "TIME";
  public static final String METRIC_COUNT = "COUNT";
  public static final String METRIC_AMOUNT = "AMOUNT";
}
