package com.crm.dto.merge;

import lombok.Data;

/** 合并请求（034）。 */
@Data
public class MergeRequest {

  private Long primaryId;
  private Long duplicateId;
}
