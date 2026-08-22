package com.crm.dto.pool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 公海扫描结果（FR-PL06）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoolScanResult {

  private long returnedCount;
}
