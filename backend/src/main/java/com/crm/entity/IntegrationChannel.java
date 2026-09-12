package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 集成通道（058-integration-hub）。 */
@Getter
@Setter
@TableName("integration_channel")
public class IntegrationChannel {

  private Long id;

  /** WECHAT_WORK / DINGTALK / CUSTOM。 */
  private String channelType;

  private String name;
  private String webhookUrl;
  private Integer enabled;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
