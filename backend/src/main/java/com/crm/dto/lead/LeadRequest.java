package com.crm.dto.lead;

import com.crm.dto.customfield.CustomFieldValueDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class LeadRequest {

  @NotBlank(message = "姓名不能为空")
  @Size(max = 50)
  private String name;

  @NotBlank(message = "公司不能为空")
  @Size(max = 100)
  private String company;

  @Size(max = 50)
  private String title;

  @Size(max = 30)
  private String phone;

  @Email(message = "邮箱格式不正确")
  @Size(max = 100)
  private String email;

  @Size(max = 20)
  private String source;

  @Size(max = 20)
  private String status;

  private Integer score;

  private Long ownerId;

  /** 营销归因活动（014，可选）。 */
  private Long campaignId;

  /** 自定义字段值（016，可选）。 */
  private List<CustomFieldValueDTO> customFieldValues;

  private Integer version;

  @Size(max = 500)
  private String remark;
}
