package com.crm.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 知识库文章创建/编辑请求（FR-C06）。 */
@Data
public class ArticleRequest {

  @NotBlank(message = "分类不能为空")
  @Pattern(
      regexp = "^(PRODUCT_USAGE|FAULT_TROUBLESHOOTING|PROCESS_CONSULT|AFTER_SALES_POLICY|OTHER)$",
      message = "分类不合法")
  private String category;

  @NotBlank(message = "标题不能为空")
  @Size(max = 200, message = "标题不能超过 200 字")
  private String title;

  private String content;

  @Size(max = 500, message = "关键词不能超过 500 字")
  private String keywords;

  private Integer version;
}
