package com.crm.dto.customer;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 导入结果（FR-006，成功/失败明细）。 */
@Data
public class ImportResult {

  private int successCount;
  private int failureCount;
  private List<ImportFailure> failures = new ArrayList<>();

  @Data
  public static class ImportFailure {
    private final int row;
    private final String message;

    public ImportFailure(int row, String message) {
      this.row = row;
      this.message = message;
    }
  }
}
