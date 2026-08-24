package com.crm.dto.search;

import java.util.List;
import lombok.Data;

/** 全局搜索结果（032）。 */
@Data
public class SearchResponse {

  private String keyword;
  private List<SearchGroup> groups;
  private long total;

  @Data
  public static class SearchGroup {
    private String type;
    private String label;
    private List<SearchItem> items;
  }

  @Data
  public static class SearchItem {
    private Long id;
    private String title;
    private String subtitle;
    private String path;
  }
}
