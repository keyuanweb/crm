package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 公告（037-announcements）。 */
@Getter
@Setter
@TableName("announcement")
public class Announcement extends BaseEntity {

  private String title;
  private String content;
  private Boolean pinned;
  private LocalDateTime expiresAt;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
