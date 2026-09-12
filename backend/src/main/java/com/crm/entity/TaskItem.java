package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 任务（010-task-reminder，个人待办）。 */
@Getter
@Setter
@TableName("task_item")
public class TaskItem extends BaseEntity {

  private String title;
  private LocalDateTime dueAt;

  /** HIGH / MEDIUM / LOW。 */
  private String priority;

  /** TODO / DONE。 */
  private String status;

  /** CUSTOMER / LEAD / CONTRACT / ORDER。 */
  private String linkedType;

  private Long linkedId;
  private String remark;

  /** 归属用户（数据隔离）。 */
  private Long ownerId;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
