package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 客户共享（012-data-permission，只读可见）。 */
@Getter
@Setter
@TableName("customer_share")
public class CustomerShare extends BaseEntity {

  private Long customerId;
  private Long sharedToUserId;
  private Long sharedBy;
}
