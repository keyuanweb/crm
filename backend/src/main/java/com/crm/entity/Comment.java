package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 业务记录评论（037-announcements，多实体通用）。 */
@Getter
@Setter
@TableName("comment")
public class Comment {

  private Long id;

  /** CUSTOMER / LEAD / OPPORTUNITY / TICKET。 */
  private String entityType;

  private Long entityId;
  private String content;
  private Long authorId;
  @TableLogic private Integer deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
