package com.crm.dto.portal;

import lombok.Data;

/** 门户知识库文章响应（公开，仅 PUBLISHED）。 */
@Data
public class PortalArticleResponse {

  private Long id;
  private String category;
  private String title;
  private String keywords;
  private String content;
  private java.time.LocalDateTime updatedAt;
}
