package com.crm.dto.landing;

import java.util.List;
import java.util.Map;
import lombok.Data;

/** 落地页公开渲染响应。 */
@Data
public class LandingPagePublicResponse {

  private Long id;
  private String title;
  private String subtitle;
  private String description;
  private String themeColor;
  private Boolean enabled;
  private FormMeta form;

  @Data
  public static class FormMeta {
    private Long id;
    private String name;
    private String successMessage;
    private List<Map<String, Object>> fields;
  }
}
