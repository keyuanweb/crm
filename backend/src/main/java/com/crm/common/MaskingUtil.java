package com.crm.common;

/** 敏感字段脱敏工具（FR-016，选项 A：列表脱敏、详情完整）。 */
public final class MaskingUtil {

  private MaskingUtil() {}

  /** 电话脱敏：保留前 3 位与后 4 位，如 138****0000。 */
  public static String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }

  /** 邮箱脱敏：保留首字符与域名，如 z***@example.com。 */
  public static String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return email;
    }
    int at = email.indexOf('@');
    String local = email.substring(0, at);
    String domain = email.substring(at);
    if (local.isEmpty()) {
      return "*" + domain;
    }
    if (local.length() == 1) {
      return "*" + domain;
    }
    return local.substring(0, 1) + "***" + local.substring(local.length() - 1) + domain;
  }
}
