package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 线索（销售链路入口，可转化为客户+联系人+商机）。 */
@Getter
@Setter
@TableName("`lead`")
public class Lead extends BaseEntity {

  private String name;
  private String company;
  private String title;
  private String phone;
  private String email;

  /** WEBSITE / AD / EXHIBITION / REFERRAL / COLD_CALL / OTHER。 */
  private String source;

  /** NEW / WORKING / QUALIFIED / DISQUALIFIED。 */
  private String status;

  /** 评分 0-100。 */
  private Integer score;

  /** 负责人（空=线索池）。 */
  private Long ownerId;

  /** 转化后的客户 ID。 */
  private Long convertedCustomerId;

  /** 转化时间。 */
  private LocalDateTime convertedAt;

  /** 营销归因活动（014，可选）。 */
  private Long campaignId;

  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
