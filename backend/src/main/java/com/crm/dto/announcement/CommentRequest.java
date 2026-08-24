package com.crm.dto.announcement;

import lombok.Data;

/** 评论请求（037）。 */
@Data
public class CommentRequest {

  private String entityType;
  private Long entityId;
  private String content;
}
