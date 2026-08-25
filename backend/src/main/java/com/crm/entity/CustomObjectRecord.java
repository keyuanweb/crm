package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 自定义对象记录（059-custom-object）。 */
@Getter
@Setter
@TableName("custom_object_record")
public class CustomObjectRecord {

  private Long id;
  private Long objectId;

  /** 记录值 JSON 键值对（字段名规避 H2 保留字 values）。 */
  private String recordValues;

  private Long createdBy;
  private LocalDateTime createdAt;
}
