package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 知识库文章（015-customer-service）。 */
@Getter
@Setter
@TableName("knowledge_article")
public class KnowledgeArticle extends BaseEntity {

  /** PRODUCT_USAGE / FAULT_TROUBLESHOOTING / PROCESS_CONSULT / AFTER_SALES_POLICY / OTHER。 */
  private String category;

  private String title;
  private String content;

  /** 关键词（逗号分隔）。 */
  private String keywords;

  /** DRAFT / PUBLISHED。 */
  private String status;

  private Long authorId;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
