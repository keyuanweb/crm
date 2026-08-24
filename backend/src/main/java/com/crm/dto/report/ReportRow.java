package com.crm.dto.report;

import lombok.Data;

/** 报表聚合行（021-custom-reports）。 */
@Data
public class ReportRow {

  private String dimensionValue;
  private long count;
  private long amount;

  /** 金额占比 0-1。 */
  private double ratio;
}
