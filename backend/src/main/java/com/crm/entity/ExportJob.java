package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
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

  // 刻意不声明 exportFormat：V40 建表没有这一列。MyBatis-Plus 按**实体**生成列清单，
  // 声明了它就等于把 export_format 编进每条 SELECT，于是 create() 第一步的 cleanup() 查询
  // 必抛 Unknown column 'export_format'，导出中心整体 500（2026-09-12 在真实 MySQL 实测）。
  // 该字段全仓无任何读取方（唯一写入方在 ExportExecutor 一个从不落库的临时对象上），
  // 属残留，故删除而非补迁移——补迁移只会留下一列永远没人读的列。
  private Long rowCount;
  private String errorMessage;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;

  private LocalDateTime completedAt;
}
