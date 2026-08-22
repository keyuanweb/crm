package com.crm.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 分页信封（契约 README：items/total/page/pageSize）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

  private List<T> items;
  private long total;
  private long page;
  private long pageSize;

  public static <T> PageResult<T> of(List<T> items, long total, long page, long pageSize) {
    return new PageResult<>(items, total, page, pageSize);
  }
}
