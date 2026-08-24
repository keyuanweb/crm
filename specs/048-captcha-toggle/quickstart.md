# Quickstart: 验证码临时关闭模块验证指南

**Branch**: `048-captcha-toggle`

## 前置条件

- 后端服务运行（`SERVER_PORT=8081`），前端开发服务运行。

## 验证场景

### 1. 无验证码登录

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**预期**: 200 返回 token（无需 captchaId/captchaCode）。

### 2. /auth/captcha 兼容

```bash
curl http://localhost:8081/api/v1/auth/captcha
```

**预期**: 200 仍返回 captchaId + dataURL（前端兼容）。

### 3. 恢复验证码（如需）

```bash
# 设置环境变量后重启
CAPTCHA_ENABLED=true
```

**预期**: 登录恢复验证码校验。

## 契约核对

- 无接口变更；`/auth/captcha` 保留。
