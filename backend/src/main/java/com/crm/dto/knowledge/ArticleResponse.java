package com.crm.dto.knowledge;

import java.time.LocalDateTime;
import lombok.Data;

/** 知识库文章响应。 */
@Data
public class ArticleResponse {

  private Long id;
  private String category;
  private String title;
  private String content;
  private String keywords;
  private String status;
  private Long authorId;
  private String authorName;
  private Integer version;
  private LocalDateTime createdAt;
}
