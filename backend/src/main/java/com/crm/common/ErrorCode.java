package com.crm.common;

import lombok.Getter;

/** 统一错误码（与契约 README 错误格式对齐）。 */
@Getter
public enum ErrorCode {
  BAD_REQUEST(400, "BAD_REQUEST", "请求参数错误"),
  INVALID_CREDENTIALS(401, "INVALID_CREDENTIALS", "用户名或密码错误"),
  UNAUTHORIZED(401, "UNAUTHORIZED", "未认证或令牌无效"),
  REFRESH_TOKEN_INVALID(401, "REFRESH_TOKEN_INVALID", "刷新令牌无效或已过期"),
  FORBIDDEN(403, "FORBIDDEN", "无权限执行该操作"),
  CUSTOMER_NOT_FOUND(404, "CUSTOMER_NOT_FOUND", "客户不存在"),
  OPPORTUNITY_NOT_FOUND(404, "OPPORTUNITY_NOT_FOUND", "商机不存在"),
  SALES_OPPORTUNITY_NOT_FOUND(404, "SALES_OPPORTUNITY_NOT_FOUND", "销售机会不存在"),
  FOLLOW_UP_NOT_FOUND(404, "FOLLOW_UP_NOT_FOUND", "跟进记录不存在"),
  LEAD_NOT_FOUND(404, "LEAD_NOT_FOUND", "线索不存在"),
  USER_NOT_FOUND(404, "USER_NOT_FOUND", "用户不存在"),
  USER_DUPLICATE(409, "USER_DUPLICATE", "用户名已存在"),
  CUSTOMER_DUPLICATE(409, "CUSTOMER_DUPLICATE", "相同名称与公司的客户已存在"),
  VERSION_CONFLICT(409, "VERSION_CONFLICT", "数据已被他人修改，请刷新后重试"),
  AMOUNT_RANGE_INVALID(422, "AMOUNT_RANGE_INVALID", "金额范围不合法"),
  AMOUNT_INVALID(422, "AMOUNT_INVALID", "金额不合法"),
  STAGE_INVALID(422, "STAGE_INVALID", "阶段不合法"),
  ALREADY_CLOSED(422, "ALREADY_CLOSED", "该销售机会已关闭"),
  CLOSE_RESULT_REQUIRED(422, "CLOSE_RESULT_REQUIRED", "关闭时必须提供赢单/输单结果"),
  OPPORTUNITY_CUSTOMER_MISMATCH(422, "OPPORTUNITY_CUSTOMER_MISMATCH", "商机与客户不匹配"),
  LEAD_ALREADY_CONVERTED(422, "LEAD_ALREADY_CONVERTED", "线索已转化，不可重复操作"),
  LEAD_INVALID_STATE(422, "LEAD_INVALID_STATE", "线索状态不允许该操作"),
  INTERNAL_ERROR(500, "INTERNAL_ERROR", "服务器内部错误");

  private final int status;
  private final String code;
  private final String message;

  ErrorCode(int status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }
}
