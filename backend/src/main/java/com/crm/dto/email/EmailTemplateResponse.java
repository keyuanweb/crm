package com.crm.dto.email;

import lombok.Data;

/** 邮件模板响应（030）。 */
@Data
public class EmailTemplateResponse {

  private Long id;
  private String name;
  private String subject;
  private String content;
  private String category;
}
