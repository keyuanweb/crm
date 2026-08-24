package com.crm.dto.merge;

import java.util.List;
import lombok.Data;

/** 查重分组响应（034）。 */
@Data
public class DuplicateGroupResponse {

  private Long id;
  private Long primaryId;
  private String primaryName;
  private List<DuplicateItem> duplicates;

  @Data
  public static class DuplicateItem {
    private Long customerId;
    private String name;
    private String company;
    private int similarity;
    private long relatedCount;
  }
}
