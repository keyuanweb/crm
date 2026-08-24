package com.crm.dto.email;

import java.util.List;
import lombok.Data;

/** 邮件群发请求（030）。 */
@Data
public class CampaignRequest {

  private String name;
  private Long templateId;

  /** SEGMENT / CUSTOMER_IDS。 */
  private String sourceType;

  /** SEGMENT 时：细分 id。 */
  private Long segmentId;

  /** CUSTOMER_IDS 时：客户 id 列表。 */
  private List<Long> customerIds;
}
