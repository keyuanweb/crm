package com.crm.dto.form;

import java.time.LocalDateTime;
import lombok.Data;

/** 表单响应（036）。 */
@Data
public class FormResponse {

  private Long id;
  private String name;
  private String fields;
  private String successMessage;
  private String source;
  private String status;
  private Integer submissionCount;
  private LocalDateTime createdAt;
}
