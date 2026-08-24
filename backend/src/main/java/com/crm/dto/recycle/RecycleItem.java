package com.crm.dto.recycle;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 回收站条目（025-recycle-bin）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecycleItem {

  /** CUSTOMER / LEAD / CONTACT / OPPORTUNITY。 */
  private String type;

  private Long id;

  private String name;
  private LocalDateTime deletedAt;
  private Long deletedBy;

  /** 操作请求便捷构造（恢复/彻底删除用，仅 type+id）。 */
  public RecycleItem(String type, Long id) {
    this.type = type;
    this.id = id;
  }
}
