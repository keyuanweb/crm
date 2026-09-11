package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 导出任务（016-system-enhancement）。 */
@Getter
@Setter
@TableName("export_job")
public class ExportJob extends BaseEntity {

  /** LEAD / CUSTOMER / OPPORTUNITY / TICKET。 */
  private String exportType;

  /** 筛选条件（JSON）。 */
  private String filter;

  /** PENDING / RUNNING / DONE / FAILED。 */
  private String status;

  private String filePath;
  private String exportFormat;
  private Long rowCount;
  private String errorMessage;
  private Long createdBy;
  private LocalDateTime completedAt;
}
