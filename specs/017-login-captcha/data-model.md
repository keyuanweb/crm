# 数据模型：登录验证码

## 验证码会话（Captcha Session）

验证码会话是临时内存对象，**不持久化到数据库**（无 Flyway 迁移、无实体类）。

### 存储表示（Redis）

| 字段 | 类型 | 说明 |
|---|---|---|
| key | String | `auth:captcha:<captchaId>`，`captchaId` 为 UUID |
| value | String | 大写字母数字答案（如 `7GK2`），4~6 位 |
| TTL | Long | 300 秒（5 分钟），过期自动删除 |

### 生命周期

1. **生成**：`GET /auth/captcha` → 生成 UUID captchaId + 随机答案 → 答案写入 Redis（TTL 300s）→ 返回 captchaId + 图片 base64。
2. **校验**：`POST /auth/login` → 按 captchaId 取答案 → 比对（不区分大小写）→ 无论结果删除 Redis key（一次性）。
3. **过期清理**：Redis TTL 自动删除，无需额外任务。

### 约束

- 答案仅存服务端，绝不出现在日志、响应体或前端（前端只拿到图片与 captchaId）。
- 答案字符集：`A-Z`、`2-9`（排除 0/O/1/I/l），不区分大小写。
- captchaId 不可预测（UUID），防止枚举。
