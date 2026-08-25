package com.crm.dto.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 邮件账户请求。 */
@Data
public class MailAccountRequest {

  @NotBlank(message = "邮箱不能为空")
  @Email(message = "邮箱格式不合法")
  @Size(max = 100)
  private String email;

  @NotBlank(message = "显示名不能为空")
  @Size(max = 100)
  private String displayName;

  @Size(max = 100)
  private String imapHost;

  private Integer imapPort;

  @Size(max = 100)
  private String smtpHost;

  private Integer smtpPort;

  private Boolean enabled;

  private Boolean isDefaultSender;
}
