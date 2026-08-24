package com.crm.dto.visit;

import lombok.Data;

/** 签到请求（035）。 */
@Data
public class CheckInRequest {

  private Double latitude;
  private Double longitude;
  private String locationText;
  private String summary;
}
