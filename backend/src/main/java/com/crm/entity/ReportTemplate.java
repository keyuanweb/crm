package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 报表模板（021-custom-reports，P3）：保存常用报表配置。 */
@Getter
@Setter
@TableName("report_template")
public class ReportTemplate extends BaseEntity {

  private String name;
  private String dimension;
  private String metric;
  private String granularity;
  private LocalDate startDate;
  private LocalDate endDate;
  private String stageFilter;
  private Long createdBy;
}
