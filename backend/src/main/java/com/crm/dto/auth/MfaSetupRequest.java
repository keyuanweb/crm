package com.crm.dto.auth;

import lombok.Data;

/**
 * 绑定 2FA 的第一步（生成密钥）请求（082，contracts/auth-mfa.md §1）。
 *
 * <p><b>整个 body 是可选的</b>：{@code quickstart.md} §b 的调用**不带 body**，所以 {@code AuthController} 上用的是
 * {@code @RequestBody(required = false)} —— 这时 Spring 传进来的是 {@code null}（不是"字段全为 null
 * 的对象"），调用方必须同时处理这两种形态。若写成必填， 那条冒烟命令会得到一个参数解析阶段的 400，错误内容与业务毫无关系。
 *
 * <p>{@code password} 传了就必须对（401）：它是"手机被人拿去、人还坐在电脑前"时的第二道确认。 不传也允许——此时唯一的凭据就是那个已经有效的 JWT。
 */
@Data
public class MfaSetupRequest {

  private String password;
}
