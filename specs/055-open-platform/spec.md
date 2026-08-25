# 功能规格：开放平台模块（API Key 管理 + Webhook 事件订阅）

**功能分支**: `055-open-platform`

**创建日期**: 2026-08-25

**状态**: 草稿

**输入**: 043 差距分析 P2 建议——"对外 REST API 管理（API Key/权限/限流）、Webhook 事件订阅"。对标成熟 CRM（Salesforce Connected Apps / HubSpot Private Apps）：为外部系统集成提供**服务端访问凭证**（API Key）与**事件推送**（Webhook），实现 CRM 与第三方（自有小程序/数据平台/ERP 等）的数据交互，是平台开放能力的核心差距项。

## 用户场景与测试（必填）

### 用户故事 1 - API Key 管理（优先级：P0）

管理员生成/查看/吊销 API Key（含名称、权限范围、有效期、最后使用时间）；外部系统携带 `X-API-Key` 调用开放端点。

**优先级理由**: API Key 是对外访问的基础凭证，P0 最先交付。

**独立测试**: 生成 Key → 携带调用开放端点 → 吊销后失效。

**验收场景**:

1. **Given** 管理员生成 API Key（命名"数据同步"，权限范围），**When** 查看列表，**Then** 展示 Key 前缀/权限/有效期/状态。
2. **Given** 有效 Key，**When** 外部携带 `X-API-Key` 调用开放端点，**Then** 鉴权通过返回数据。
3. **Given** Key 被吊销，**When** 再次调用，**Then** 401 拒绝。
4. **Given** 无效 Key，**When** 调用，**Then** 401 拒绝。

---

### 用户故事 2 - Webhook 事件订阅（优先级：P0）

管理员配置 Webhook（事件类型 + 回调 URL + 签名密钥）；系统在业务事件发生时推送 HTTP POST 到回调 URL（携带 HMAC 签名），可查看推送记录。

**优先级理由**: Webhook 是事件驱动的集成基础，与故事 1 共同构成 P0。

**独立测试**: 配置 Webhook → 触发事件 → 回调收到推送（签名校验）。

**验收场景**:

1. **Given** 管理员配置 Webhook（事件 LEAD_CREATED，回调 URL），**When** 保存，**Then** 订阅成功且生成签名密钥。
2. **Given** Webhook 已配置，**When** 线索创建事件发生，**Then** 系统 POST 事件负载到回调 URL（含 HMAC 签名头）。
3. **Given** 推送记录，**When** 查看，**Then** 展示事件/回调/状态/时间。

### 边界情况

- API Key 仅创建时展示完整值（后续只显示前缀），防泄露。
- Webhook 推送失败重试（最多 3 次指数退避）；记录失败原因。
- 事件类型枚举（LEAD_CREATED/LEAD_UPDATED/CUSTOMER_CREATED/OPPORTUNITY_STAGE_CHANGED 等，v1 子集）。
- 签名：`X-Signature` = HMAC-SHA256(secret, body)（回调方可验签）。

## 需求（必填）

### 功能需求

#### API Key

- **FR-O01**: 系统必须支持 API Key 创建（名称/权限范围/有效期）、列表、吊销。
- **FR-O02**: 系统必须支持 `X-API-Key` 鉴权调用开放端点（/api/v1/open/**）。
- **FR-O03**: 系统必须仅创建时返回完整 Key（列表只显前缀）。
- **FR-O04**: 系统必须记录最后使用时间与使用次数。

#### Webhook

- **FR-O05**: 系统必须支持 Webhook 订阅（事件类型 + 回调 URL + 签名密钥）。
- **FR-O06**: 系统必须在业务事件发生时异步推送事件负载到回调（HMAC 签名）。
- **FR-O07**: 系统必须支持推送记录查看与失败重试（≤3 次退避）。
- **FR-O08**: 系统必须支持 Webhook 启停/删除。

#### 权限与约束

- **FR-O09**: API Key / Webhook 管理：仅 ADMIN。
- **FR-O10**: 开放端点读取为主（v1 提供客户/线索只读 + 线索创建写入，供集成验证）。

### 关键实体（涉及数据）

- **API Key（ApiKey）**: 新增实体；名称、keyHash（存哈希防泄露）、前缀、权限范围 JSON、有效期、状态、最后使用时间。
- **Webhook 订阅（WebhookSubscription）**: 新增实体；事件类型、回调 URL、签名密钥、启用、重试次数。
- **Webhook 推送记录（WebhookDelivery）**: 新增实体；订阅 id、事件、负载、状态、响应码、错误、时间。

## 成功标准（必填）

### 可度量结果

- **SC-O01**: API Key 鉴权正确率 100%（有效/吊销/无效 各抽查 3 次）。
- **SC-O02**: Webhook 推送成功触发（抽查 3 个事件）；失败重试 ≤3 次。
- **SC-O03**: HMAC 签名可校验（抽查 3 条负载）。
- **SC-O04**: 前端 typecheck / lint / build 通过，后端 verify 通过，无回归。

## 假设

- v1 开放端点子集：`GET /open/customers`、`GET /open/leads`、`POST /open/leads`（供集成验证）。
- Webhook 推送为尽力而为（异步线程池）；失败重试 ≤3 次退避。
- 签名密钥独立于 API Key（Webhook 专用）。

## 依赖

- 安全链（SecurityConfig/JwtAuthFilter）扩展 API Key 过滤器。
