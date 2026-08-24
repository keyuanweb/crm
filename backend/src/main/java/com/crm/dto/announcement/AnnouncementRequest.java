package com.crm.dto.announcement;

import java.time.LocalDateTime;
import lombok.Data;

/** 公告请求（037）。 */
@Data
public class AnnouncementRequest {

  private String title;
  private String content;
  private Boolean pinned;
  private LocalDateTime expiresAt;
}
