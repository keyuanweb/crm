package com.crm.dto.email;

import lombok.Data;

/** 邮件模板请求（030）。 */
@Data
public class EmailTemplateRequest {

  private String name;
  private String subject;
  private String content;
  private String category;
}
