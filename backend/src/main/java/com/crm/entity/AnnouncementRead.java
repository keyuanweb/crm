package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 公告已读（037-announcements）。 */
@Getter
@Setter
@TableName("announcement_read")
public class AnnouncementRead {

  private Long id;
  private Long announcementId;
  private Long userId;
  private LocalDateTime readAt;
}
