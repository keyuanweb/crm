package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 客户-标签关联（031-customer-tags）。 */
@Getter
@Setter
@TableName("customer_tag")
public class CustomerTag {

  private Long id;
  private Long customerId;
  private Long tagId;
}
