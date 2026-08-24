package com.crm.entity;

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
  private Long createdBy;
}
