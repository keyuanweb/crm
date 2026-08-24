# 契约：登录验证码 auth-captcha

**Base**: `/api/v1/auth`（验证码端点无需认证；登录端点沿用）

## GET /auth/captcha

获取一个新的图形验证码。每次调用生成新的 captchaId 与答案，答案仅服务端暂存（Redis，5 分钟），图片以 base64 内联返回。

**Response 200**

```json
{
  "success": true,
  "data": {
    "captchaId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "imageBase64": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg..."
  },
  "error": null
}
```

- `captchaId`：本次验证码唯一标识，登录时回传。
- `imageBase64`：PNG 图片的 data URL，前端可直接置于 `<img src>`。

## POST /auth/login（扩展）

登录请求新增两个字段（验证码）：

**Body**:

```json
{
  "username": "admin",
  "password": "admin123",
  "captchaId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "captchaCode": "7gk2"
}
```

**校验规则**（按顺序）：

1. `captchaId` / `captchaCode` 缺失或为空 → `422 CAPTCHA_INVALID`（消息："请输入验证码"）。
2. 验证码不存在（未生成/已过期/已使用）→ `422 CAPTCHA_EXPIRED`（消息："验证码已过期，请重新输入"）。
3. 验证码答案不匹配（不区分大小写）→ `422 CAPTCHA_INVALID`（消息："验证码错误"），并立即失效该验证码。
4. 验证码通过后，继续原有凭证校验（`INVALID_CREDENTIALS` 401 或成功）。

**错误格式**（统一信封）：

```json
{
  "success": false,
  "data": null,
  "error": { "code": "CAPTCHA_INVALID", "message": "验证码错误", "fieldErrors": null }
}
```

## 备注

- 验证码校验独立于凭证校验，验证码错误**不**累计登录失败次数（不影响现有 5 次锁 15 分钟的防暴力破解计数）。
- 验证码一次性：无论校验结果，提交后立即删除 Redis 中的答案。
- 验证码字母数字 4~6 位，不区分大小写，排除易混淆字符（0/O/1/I/l）。
