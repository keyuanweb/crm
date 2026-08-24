package com.crm.dto.announcement;

import java.time.LocalDateTime;
import lombok.Data;

/** 公告响应（037，含已读状态）。 */
@Data
public class AnnouncementResponse {

  private Long id;
  private String title;
  private String content;
  private Boolean pinned;
  private LocalDateTime expiresAt;
  private Boolean read;
  private LocalDateTime createdAt;
}
