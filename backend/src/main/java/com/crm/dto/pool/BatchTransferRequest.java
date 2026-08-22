package com.crm.dto.pool;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 批量转移/分配请求（FR-PL07）。 */
@Data
public class BatchTransferRequest {

  @NotEmpty(message = "请选择至少一个客户")
  @Size(max = 100, message = "单次最多转移 100 个客户")
  private List<Long> customerIds;

  @NotNull(message = "目标用户不能为空")
  private Long targetOwnerId;
}
