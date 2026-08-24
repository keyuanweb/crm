package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 审批流定义（033-approval-flow）。 */
@Getter
@Setter
@TableName("approval_flow")
public class ApprovalFlow extends BaseEntity {

  private String name;

  /** CONTRACT / QUOTE。 */
  private String businessType;

  /** JSON 节点序列。 */
  private String nodes;

  /** JSON 条件分支（金额阈值 + 追加节点）。 */
  private String conditionJson;

  private Boolean enabled;
}
