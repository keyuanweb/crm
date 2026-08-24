package com.crm.dto.announcement;

import java.time.LocalDateTime;
import lombok.Data;

/** 评论响应（037）。 */
@Data
public class CommentResponse {

  private Long id;
  private String entityType;
  private Long entityId;
  private String content;
  private Long authorId;
  private String authorName;
  private LocalDateTime createdAt;
}
