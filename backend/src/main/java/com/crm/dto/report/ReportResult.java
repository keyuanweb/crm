package com.crm.dto.report;

import java.util.List;
import lombok.Data;

/** 报表结果（021-custom-reports）。 */
@Data
public class ReportResult {

  private List<ReportRow> rows;
  private long totalCount;
  private long totalAmount;
  private String dimension;
  private String metric;
  private String granularity;
}
