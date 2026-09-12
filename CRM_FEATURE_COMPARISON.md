# CRM 功能对比分析报告

> **版本**: v2.0（2026-09-12 重写）
> **项目侧依据**: 工作区代码实测（`backend/`、`frontend/`、`specs/`、`db/migration/`），非文档转述
> **对标侧依据**: 公开资料（Salesforce / HubSpot / Zoho / Dynamics 365、纷享销客 / 销售易 / 神州云动、SuiteCRM / EspoCRM / Odoo），见文末来源
> **v1.0 的问题**: 原版为「全 ✅ 清单式」描述，把"有页面/有表"等同于"能力达成"，且遗漏了多项代码层可验证的缺口。本版改为**逐项给出证据与判定**，并新增失实更正章节（第六节）。

---

## 一、评估框架与对标对象

### 1.1 评估维度（10 域）

参考 Gartner《Magic Quadrant for Sales Force Automation Platforms 2025》的能力项划分、中国信通院 T/ISC 0108-2026《客户关系管理系统智能化能力成熟度模型》的四域划分（营销/销售/服务/分析 + 平台底座），以及开源自托管 CRM 的评估项，归并为 10 个能力域：

客户主数据 · 销售过程管理 · CPQ 与成交回款 · 营销获客 · 客户服务 · 效率协作 · 分析与洞察 · 平台与扩展 · 安全与合规 · AI 能力

### 1.2 本项目实测规模

| 维度 | 实测值 | 依据 |
|---|---|---|
| 后端 Controller | 66 | `backend/src/main/java/com/crm/controller/**` |
| 实体类 | 82（81 业务实体 + `BaseEntity`） | `.../com/crm/entity/` |
| 数据库表 | 84 | `db/migration/*.sql` 的 `CREATE TABLE` 去重 |
| Flyway 迁移 | 76（V1–V77，缺 V72） | `backend/src/main/resources/db/migration/` |
| 后端测试类 | 148 | `backend/src/test/**` |
| 前端路由 / 页面 | 88 条 `<Route>` / 83 个懒加载页面 | `frontend/src/App.tsx`、`frontend/src/pages` |
| 前端 service | 55 | `frontend/src/services/` |
| 业务能力点（`@Scheduled`） | **仅 2 个** | 全仓 grep（保留策略 + 定时导出） |

### 1.3 对标对象与定位

| 类型 | 产品 | 定位 |
|---|---|---|
| 国际企业级标杆 | Salesforce Sales/Service Cloud | 深度定制 + 高级分析 + 生态（AppExchange 7000+ 应用） |
| 国际易用型标杆 | HubSpot | 开箱即用 + 原生营销销售服务一体 |
| 国际性价比 | Zoho CRM / Dynamics 365 | 功能广度 + 微软栈协同 |
| 国产第一梯队 | 纷享销客、销售易 | 连接型 CRM / B2B CPQ + 企微钉钉生态 |
| 自建开源参照 | SuiteCRM、EspoCRM、Odoo | 同为本项目最接近的对标物（自部署、可二开） |

**本项目的实际定位**：一套**单租户、自部署、业务广度优先**的企业内部 CRM，能力特征最接近"国产中端 CRM + 开源 CRM 深度二开版"，与 Salesforce / 纷享销客这类平台型产品仍在**深度与生态**上有代际差。

---

## 二、逐域能力对比

图例：✅ 完整（达到业界主流可用水准） · ⚠️ 部分（可用但存在明确缺口） · ❌ 缺失

### 2.1 客户主数据管理

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 客户 / 联系人 CRUD | 全部产品 | Customer + Contact + 分页搜索筛选 + 逻辑删除 | ✅ | `CustomerController`、`ContactController` |
| 360° 客户视图 | 全部产品 | 详情页 7 Tab（基本/标签/概览/订单/回款/合同/工单）+ 跟进时间线 | ✅ | `frontend/src/pages/customers/CustomerDetailPage.tsx`(754 行) |
| 线索→转化 | Salesforce/HubSpot/纷享 | 线索池、分配、认领、评分、**一键转客户+联系人+商机** | ✅ | `LeadController` `/{id}/convert`、`LeadConvertModal.tsx` |
| 查重与合并 | Salesforce/HubSpot | `/customers/duplicates` 识别 + `/customers/merge` 合并 | ✅ | `CustomerMergeController` |
| 公海池 | 国产 CRM 标配 | 公海池 + 领取 + 滞留扫描（默认 30 天）+ 批量转移 | ✅ | `CustomerPoolController`、`crm.pool.stale-days` |
| 标签与分群 | 全部产品 | Tag + Segment（JSON 条件动态求值 + 成员/计数） | ✅ | `TagController`、`SegmentController` |
| 客户健康度 / 流失预警 | HubSpot、销售易 | `health_score_config` 可配权重，风险客户清单 | ✅ | `HealthScoreService`、`/customers/health/at-risk` |
| 客户共享（行级授权补充） | Salesforce Sharing Rules | `customer_share` + `/shared-to-me` | ✅ | `CustomerShareController` |
| 账户层级（Account Hierarchy） | Salesforce/HubSpot/Dynamics | — | ❌ | 无父子客户/集团-子公司关系模型 |
| 自动数据补全 / 第三方数据增强 | HubSpot Breeze Intelligence、Salesforce Data Cloud | — | ❌ | 无第三方数据源接入 |

**小结**：客户域是本项目最扎实的一块，除"账户层级"和"数据增强"外基本对齐业界。

### 2.2 销售过程管理

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 商机与阶段管道 | 全部产品 | `sales_opportunity` 阶段 + 赢单/输单关闭 + 金额 | ✅ | `SalesOpportunityController` |
| 销售 Playbook | Salesforce、销售易 | `stage_action_template`（必做项 + 完成打钩） | ✅ | `PlaybookController`、`sales_opportunity_action` |
| 销售配额与分解 | Salesforce Quota、纷享 | `sales_quota` **parentId 层级分解** + 版本快照 + 达成率 + 排行 | ✅ | `SalesQuotaController`、`sales_quota_version/breakdown/achievement` |
| 管道加权预测 | Salesforce Forecasting | `computeForecast`：管道金额 × 阶段概率，**概率由历史转化率校准**（样本不足回退默认） | ⚠️ | `DashboardStatsService:193`、`StageConversionService` |
| 预测的按人/团队提交与调整（rollup） | Salesforce 核心能力 | — | ❌ | 仅有系统单向计算，无"销售提交→经理调整→锁定"链路 |
| 多场景预测（承诺/最佳/最差） | Salesforce | — | ❌ | 无 |
| 区域管理（Territory） | Salesforce/SAP/Dynamics | — | ❌ | 全仓无 territory 概念；配额、客户分配、数据权限均无法按区域建模 |
| 看板拖拽（Kanban Pipeline） | HubSpot/Zoho/销售易 | — | ❌ | 前端无拖拽库；商机以 ProTable 呈现，漏斗仅为统计图表 |
| 赢单/输单原因分析 | 全部产品 | 有 `closeResult` 字段 | ⚠️ | 无结构化输单原因字典与归因分析报表 |

**小结**：过程管理"骨架齐全、神经末梢缺"。最突出的两处——**无区域管理**（导致配额/权限/分配无法按地理或行业维度组织）与**无预测 rollup**（管理者看不到"人报的数"与"系统算的数"的差异）——恰是 Salesforce 与国产第一梯队销售管理的核心卖点。

### 2.3 CPQ 与成交回款

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 产品目录 | 全部产品 | `product` + 标准售价 + 多币种价表 | ✅ | `ProductController`、`ProductPriceController` |
| 报价单（CPQ） | Salesforce CPQ、销售易 | `quote` + `quote_item`（行小计/折扣）+ 状态机 DRAFT→待审→已批→已签/驳回 | ✅ | `QuoteController` |
| 报价 PDF | 全部产品 | OpenPDF + 内置中文字体（`wqy-microhei.ttc`） | ✅ | `QuotePdfService` |
| 电子签署 | DocuSign / 法大大 | 自建 Canvas 签名 + 签署记录 | ⚠️ | `SignatureController`；**非第三方 CA，无法律效力存证/时间戳** |
| 价格手册 / 折扣矩阵 | Salesforce CPQ | — | ❌ | 只有单一"标准售价"，无按客户/数量/区间的价格表与折扣审批矩阵 |
| 产品捆绑与配置器 | Salesforce CPQ | — | ❌ | 无 bundle / configurator / 规则约束 |
| 合同全生命周期 | 全部产品 | 6 态状态机（提交/审批/驳回/生效/完成/终止）+ 附件 + 模板 | ✅ | `ContractController`、`ContractAttachmentController` |
| 合同续约 | Salesforce、纷享 | `renewedFromId` 续约链 + `/renewal-overview` 漏斗 | ✅ | `ContractRenewalController` |
| 订单 | 全部产品 | `sales_order` + `payment_plan` 分期回款 + `payment_record` + 应收账款提醒 | ⚠️ | **订单无行项目表**（金额仅在订单头）；无发货/履约、无退货 RMA |
| 发票 | 全部产品 | 开票 + 作废 + 统计 | ✅ | `InvoiceController` |
| 订阅计费 | Salesforce Revenue Cloud、Chargebee | — | ❌ | 无周期性计费/用量计费模型 |

**小结**：CPQ 的"标准路径"完整（产品→报价→审批→签署→合同→订单→回款），且电子签与续约链是超出同规模产品的加分项。缺口在**复杂定价**（价格手册/折扣矩阵/配置器）和**订单行项目**——后者是 B2B 多产品订单的硬伤。

### 2.4 营销获客

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 营销活动与 ROI 归因 | HubSpot、纷享 | `marketing_campaign`（预算/成本）+ `/channel-roi` + 客户 `campaignId` 归因 | ✅ | `MarketingController`、`MarketingRoiService` |
| 邮件营销 | 全部产品 | 模板 + 群发 + A/B 主题测试 + 发送日志 + 打开/点击追踪 + 退订 | ✅ | `EmailController`、`EmailTrackController` |
| 表单获客 | HubSpot | 自定义表单 + 公开提交页 `/f/:id`，**提交自动建线索 + 邮箱/手机防重** | ✅ | `FormController` |
| 落地页 + UTM 归因 | HubSpot | 托管页 `/lp/:id` + `/landing-pages/{id}/stats` | ✅ | `LandingPageController` |
| 线索评分 | HubSpot、销售易 | `lead_score_config` 可配权重（来源/完整度/跟进/新鲜度） | ✅ | `LeadScoreService` |
| 退订合规 | CAN-SPAM / GDPR | 公开退订端点 + 退订名单 | ✅ | `EmailUnsubscribeController` |
| 客户旅程编排（Journey / Drip） | HubSpot Workflows、Marketo | — | ❌ | 无多步骤 nurturing 流程、无延迟节点、无分支编排 |
| 短信 / 多渠道触达 | 纷享、销售易、HubSpot | — | ❌ | **全仓 grep 无任何 SMS 代码** |
| ESP 连接器（SES/SendGrid/Mailchimp） | HubSpot、EspoCRM | — | ❌ | 仅用 Spring Mail 直发，无投递率/退信/webhook 回执管理 |
| 硬退信与投诉处理 | 业界标配 | — | ❌ | 无 bounce/complaint 处理，发信域名 SPF/DKIM 无管理 |
| 营销日历 | HubSpot | — | ❌ | 无 |

**小结**：**"获客-追踪-归因"链条完整，但"持续培育"缺失**。当前定位是"能发起一次群发并看到打开率"，而非 HubSpot 式的"把线索养到成熟再交给销售"。无短信是国内场景的明显短板（企微/短信触达是国内营销主渠道）。

### 2.5 客户服务

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 工单管理 | 全部产品 | 4 状态流转 + 优先级 + 指派 + 回复 | ✅ | `TicketController` |
| SLA 策略与日历 | Zendesk、Salesforce | `sla_policy`（按优先级的响应/解决时限）+ `sla_calendar_config`（工作时间/节假日/工作日） | ⚠️ | 策略与日历模型完整，但见下行 |
| **SLA 到期扫描与自动升级** | 业界标配 | — | ❌ | **全库仅 2 个 `@Scheduled`（数据保留、定时导出），无任何 SLA 扫描作业；`slaStatus` 字段无人定时更新** |
| 知识库 | 全部产品 | 文章分类/关键词/发布下架 | ⚠️ | 无版本、无附件、无多语言、无有用性投票；检索为 LIKE 而非全文索引 |
| 客户自助门户 | Salesforce Community | 免登录门户：知识库浏览 + 在线提单 + 进度查询 | ✅ | `CustomerPortalController`（`/public/portal`） |
| 满意度调查 | 全部产品 | `ticket_survey` 评分 + `/surveys/stats` | ⚠️ | 实体仅有 `rating` + `comment`，**无 NPS 字段**（前端页面名与实体不符） |
| 全渠道建单（邮件/电话/聊天/IM） | Zendesk、Service Cloud | — | ❌ | 仅手动建单 + 门户提单；无邮箱转工单、无在线聊天、无企微/钉钉建单 |
| 工单队列 / 团队 / 宏与快捷回复 | Zendesk 核心 | — | ❌ | 无分类队列、无宏、无快捷回复模板 |
| 现场服务派工（FSM） | Salesforce Field Service | — | ❌ | 有"外勤拜访"记录，但无派工调度/路线优化 |

**小结**：这是**"字段都有了，但引擎没转起来"的典型域**。SLA 策略与日历已建模，却因为没有调度作业而形同虚设——工单不会因超时而升级，SLA 合规率也无从统计。这是一处**对外承诺与实现不符**的风险点。

### 2.6 效率与协作

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 任务与待办 | 全部产品 | `task_item`（多态关联 linkedType/linkedId）+ 完成切换 + 到期提醒摘要 | ✅ | `TaskController` |
| 日历视图 | 全部产品 | `/tasks/calendar` | ⚠️ | 仅任务日历；无统一 Event/会议实体、无参会人邀请、无重复规则 |
| 日历双向同步 | Google/Outlook | — | ❌ | 无 |
| 站内通知 | 全部产品 | 通知 + 未读数 + WebSocket 实时推送（含指数退避重连 + 5 秒降级轮询） | ✅ | `NotificationController`、`useNotificationSocket.ts` |
| 公告与 @提及 | 国产 CRM | 公告发布 + 已读 + 评论 @提及 | ✅ | `AnnouncementController`、`CommentSection.tsx` |
| 审批中心 | 全部产品 | 待我审批/我发起 + 同意/驳回/转交/重新发起 | ✅ | `ApprovalController` |
| 外勤拜访与签到 | 纷享、销售易 | 拜访计划 + 签到（经纬度）+ 统计 | ✅ | `FieldVisitController` |
| 通话记录（CTI） | Salesforce CTI、销售易 | 通话记录数据模型 + 统计 | ⚠️ | **无任何 CTI/软电话对接**，纯手工数据模型 |
| 邮件同步 | Gmail/Outlook 插件 | `mail_account` + 同步记录 | ❌ | **`MailSyncRecordService.simulateSync` 为模拟实现**，代码注释明示真实 IMAP 待接——**不可对客户宣称已具备邮件同步** |
| 企微 / 钉钉深度集成 | 国产 CRM 标配 | `integration_channel`（WECHAT_WORK/DINGTALK/CUSTOM）**仅作为通知投递通道** | ⚠️ | 无企微会话存档、无助企微客户运营、无审批流嵌入企微/钉钉工作台 |
| 原生移动 App | 全部产品 | PWA（可安装 + 离线缓存） | ⚠️ | 无 iOS/Android 原生 App；PWA 无离线编辑与写回 |
| 全局搜索 | 全部产品 | 跨 6 实体 LIKE 搜索 + 数据权限过滤 | ⚠️ | LIKE 非全文索引，规模上来后性能与相关性受限 |

### 2.7 分析与洞察

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 仪表盘 | 全部产品 | KPI 卡 + 销售漏斗 + 成交预测 + 客户分析 + 业绩趋势 + 停滞商机 | ✅ | `DashboardPage.tsx`(942 行)、`DashboardStatsService` |
| 数据大屏 | 国产 CRM | 全屏暗色大屏（漏斗/排行/健康分布/趋势/粒子背景） | ✅ | `pages/dataVision/`（ECharts） |
| 自定义报表 | Salesforce Report Builder | 维度 + 指标 + 粒度 + 阶段筛选 + 日期区间 + 模板保存 + 导出 | ⚠️ | **维度/指标为固定枚举**，无自定义公式字段、无交叉表/透视 |
| 团队排行 | 全部产品 | `/stats/leaderboard` | ✅ | `TeamLeaderboardService` |
| KPI 看板缓存 | — | Redis 5 分钟缓存 | ✅ | `KpiBoardService` |
| 智能建议 | HubSpot Breeze | 4 类**规则型**建议（停滞商机/流失预警等）+ 忽略 | ⚠️ | 规则触发，非模型预测；`SuggestionService`(254 行) |
| 报表订阅推送 | Salesforce/HubSpot | 定时导出（cron）可作部分替代 | ⚠️ | 无"按报表定时发邮件给我"的订阅语义 |
| 收入智能（对话/通话分析） | Gong、Chorus、Salesforce | — | ❌ | 无 |

### 2.8 平台与扩展

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 自定义字段 | 全部产品 | `custom_field` + 动态表单/筛选列（前端三件套复用） | ✅ | `CustomFieldController`、`useCustomFieldFilters.ts` |
| 自定义对象（低代码） | Salesforce / 纷享 PaaS | `custom_object` + `custom_object_record` CRUD + 启停 | ✅ | `CustomObjectController` |
| 工作流自动化 | Salesforce Flow | `workflow_rule`：**6 事件 × 5 动作** + 条件 JSON + 执行日志 | ⚠️ | `WorkflowEngine`(254 行)；**单条规则单动作、无条件分支/多步编排、无可视化设计器、无版本管理** |
| 审批流引擎 | 全部产品 | `approval_flow` 多节点 + 按角色/指定人 + **按金额条件分支** + 转交 + 独立事务启动 | ⚠️ | `ApprovalEngineService`(387 行)；**实测仅 `ContractService` 一处接入**（报价走自建单级状态机）；无会签/并行网关/图形化设计器 |
| 开放平台 | 全部产品 | API Key（scopes/有效期/用量/吊销）+ `/open/customers`、`/open/leads` | ✅ | `OpenPlatformController` |
| Webhook | 全部产品 | 订阅 + **HMAC 签名 + ≤3 次退避重试** + 投递记录 + SSRF 防护 | ✅ | `WebhookDeliverer`、`OutboundUrlValidator` |
| 多币种 | Salesforce/Dynamics | `currency_rate`（基准 CNY）+ 折算 + 产品多币种价 | ✅ | `CurrencyRateController` |
| 国际化 | 全部产品 | zh-CN / en 双语，各约 2400 行资源 | ⚠️ | **83 个页面仅 53 个接入 i18n，约 46 个页面硬编码中文**（配额、数据保留、营销、工作流、大屏等整模块） |
| 应用市场 / 生态 | AppExchange 7000+ | — | ❌ | 无应用市场、无 iPaaS（Zapier/集成云）、无官方连接器库 |
| OAuth 2.0 第三方授权 | 全部产品 | — | ❌ | 仅 API Key（服务端到服务端），无授权码模式/无第三方应用授权 |
| 多租户 | SaaS CRM 前提 | — | ❌ | **迁移脚本 grep `tenant` 零命中**；单租户模型 |
| 沙箱 / 环境版本管理 | Salesforce Sandbox | — | ❌ | 无 |

### 2.9 安全与合规

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 认证 | 全部产品 | JWT + refresh token + `tokenVersion` 强制失效 + 图形验证码（可开关） | ✅ | `AuthService`、`JwtUtil` |
| RBAC 角色权限 | 全部产品 | 角色-菜单-权限点 + `@RequirePermission`（**63 处，17 个权限点**） | ✅ | `PermissionAspect`、`RoleController` |
| 数据权限（行级） | Salesforce Sharing | 4 档 SELF/DEPT/DEPT_AND_CHILD/ALL + 客户共享 + 可见归属人缓存 | ⚠️ | 生效于 Customer/Contact/Lead/Export/Ticket；**非全局拦截器，靠各 Service 手动调用，新增模块易漏** |
| 字段级权限（FLS） | Salesforce/SAP 硬指标 | `field_permission`（HIDDEN/READ_ONLY/EDITABLE，按 roleCode+entityType） | ❌ | **仅被 `CustomFieldService` 调用，只作用于自定义字段；内置字段与 API 出参完全未过滤** |
| 按钮级权限 | 全部产品 | `usePermission` hook + `PermissionGuard` 组件已具备 | ❌ | **`PermissionGuard` 与 3 个 hook 零引用（死代码）；86 个页面仅 2 个（客户列表、角色列表）真正接入，约 2.3%** |
| SSO（SAML/OIDC） | 企业采购硬指标 | — | ❌ | 无 |
| 双因素认证（2FA/MFA） | 企业采购硬指标 | — | ❌ | `specs/082-two-factor-auth/` 已写规格（TOTP），但 **tasks.md 0/29 完成，后端无任何实现代码** |
| OAuth 2.0 | 全部产品 | — | ❌ | 无 |
| IP 白名单 / 登录风控 | Salesforce/Dynamics | 仅图形验证码 | ❌ | 无 IP 白名单、无失败锁定、无异地登录检测 |
| 速率限制 | 全部产品 | **仅 `EmailTrackController` 有内存桶限流** | ❌ | 登录、开放 API、导出等**均无限流**，存在暴力破解与资源耗尽风险 |
| 审计日志 | 全部产品 | `audit_log`，25+ Service 主动写入 + 查询页 | ⚠️ | 无登录/认证事件审计、无日志导出、无哈希链防篡改、无日志自身保留策略 |
| 数据保留策略 | GDPR/个保法 | `data_retention_policy` 覆盖 9 类实体 + 每日 2 点调度 + 执行历史 | ⚠️ | **实现为"置 `deleted=1` 软删除"，非归档库/冷存储；无匿名化、无硬删除、无策略优先级** |
| 合规导出（GDPR 被遗忘权/可携带权） | GDPR | 按 userId 导出 6 类实体 CSV/XLSX | ⚠️ | **仅"可携带权"；无"被遗忘权"删除工作流、无同意管理（consent）、无 DSR 请求跟踪、导出文件无加密** |
| 数据加密 | AES-256 / TDE | JWT 签名；`MaskingUtil` 脱敏 | ❌ | **数据库字段明文存储**，无列级加密/TDE；无密钥管理 |
| 合规认证 | SOC 2 / ISO 27001 / 等保 | — | ❌ | 无第三方审计；国内投标常需"等保三级"，未见支撑材料 |
| 其他工程安全 | — | 乐观锁 + 逻辑删除统一基类、SSRF 防护、生产默认值防护（`SecurityDefaultsGuard`）、CORS | ✅ | 实现质量良好，优于多数同规模自建系统 |

### 2.10 AI 能力（2025 起为 Gartner 强制评估项）

| 能力项 | 业界标准 | 本项目 | 判定 | 证据 / 缺口 |
|---|---|---|---|---|
| 线索/商机评分 | Salesforce Einstein、Zoho Zia | `LeadScoreService` / `HealthScoreService`，**权重可配置** | ⚠️ | 加权规则打分，非机器学习模型 |
| 预测性分析 | Einstein、Breeze Intelligence | `StageConversionService` 用历史阶段转化率校准预测概率 | ⚠️ | 统计校准，是项目中"最接近 ML"的一处 |
| 下一步最佳行动 | HubSpot Breeze | 规则型智能建议 | ⚠️ | 无个性化推荐模型 |
| 生成式 AI（邮件/文案/摘要） | Salesforce Einstein GPT、HubSpot Breeze Copilot | — | ❌ | 无任何 LLM 接入 |
| 对话式 AI / 聊天机器人 | Salesforce Agentforce | — | ❌ | 无 |
| 智能体（Agentic AI） | Agentforce、Breeze Agents | — | ❌ | 无 |
| 自然语言查询（NL2SQL） | Einstein、Zoho | — | ❌ | 无 |
| 语音识别 / 通话转录 | Gong、Chorus | — | ❌ | 通话记录仅存文本字段 |

**小结**：本项目 AI 能力实质为**"规则引擎 + 统计"**，与 2025 年业界定义的"AI 原生 CRM"（Einstein/Agentforce/Breeze Agent 可自主执行多步任务）存在**代际差**。考虑到 Gartner 2025 已将"原生 AI/ML 覆盖至少 3 项核心功能"列为评估门槛、国内信通院亦发布智能化成熟度团体标准，这是**未来 12 个月最需要补的能力域**。

---

## 三、能力域评分对比

**打分说明**：5 分制，**由本次调研依据代码证据与公开资料作出的主观判定**，非厂商基准测试。评分标准：5 = 达到国际标杆水准；4 = 主流可用且有亮点；3 = 骨架完整但深度不足；2 = 有明显功能或运维缺口；1 = 基本空白。

| 能力域 | 本项目 | Salesforce | HubSpot | 纷享销客 | Zoho | EspoCRM | 差距诊断（本项目 vs 业界主流） |
|---|---|---|---|---|---|---|---|
| 客户主数据 | **4.5** | 5 | 4.5 | 4.5 | 4 | 4 | 基本对齐，缺账户层级与数据增强 |
| 销售过程管理 | **3.5** | 5 | 4 | 4.5 | 4 | 3.5 | 缺区域管理、预测 rollup、看板拖拽 |
| CPQ 与成交回款 | **3.5** | 5 | 4 | 4.5 | 4 | 3 | 缺价格手册/配置器、订单行项目、订阅计费 |
| 营销获客 | **3.0** | 5 | 5 | 4.5 | 4 | 3.5 | 缺旅程编排、短信、ESP 连接器、退信处理 |
| 客户服务 | **2.5** | 5 | 4.5 | 4.5 | 4 | 3.5 | SLA 无调度升级、无全渠道建单、无队列/宏 |
| 效率协作 | **3.0** | 5 | 4.5 | 4.5 | 4 | 3.5 | 邮件同步为模拟、无日历同步、无原生 App |
| 分析与洞察 | **3.5** | 5 | 4 | 4.5 | 4 | 3 | 报表维度固定、无透视/自定义公式 |
| 平台与扩展 | **3.5** | 5 | 4 | 4.5 | 4 | 3.5 | 引擎偏轻、无应用市场、无 OAuth2、无多租户 |
| 安全与合规 | **2.5** | 5 | 4.5 | 4.5 | 4 | 4.5 | **FLS 只覆盖自定义字段、按钮级权限 2.3%、无 SSO/2FA/加密/限流** |
| AI 能力 | **1.5** | 5 | 4.5 | 4 | 3.5 | 3 | 仅规则与统计，无 LLM/Agent |
| **加权均值** | **3.1** | **5.0** | **4.4** | **4.4** | **3.9** | **3.6** | — |

**读法**：本项目**广度接近国产中端产品，深度落后约一个代际**。与自建开源标杆（EspoCRM 3.6）相比，本项目在**业务广度上明显超越**（配额、CPQ、续约、开放平台、数据保留等 EspoCRM 都没有），但在**安全合规深度上（2.5 vs 4.5）反而落后**——这是自建系统最典型的失衡。

---

## 四、核心结论

### 4.1 三句话结论

1. **广度优秀**：84 表 / 66 Controller 覆盖业界 CRM 全部 10 大能力域，标准功能项覆盖率高；配额分解、定时导出、数据保留/GDPR 合规导出、开放平台、使用地图、数据大屏等能力**超出同规模自建系统与开源 CRM**。
2. **深度不足**：差距集中在三处——**引擎偏轻**（工作流/审批/报表/预测均为"够用即可"的轻量实现）、**运维缺环**（SLA 无调度、邮件同步为模拟、数据保留仅软删）、**AI 空白**（无任何 LLM/Agent 能力）。
3. **合规短板最紧迫**：字段级权限只覆盖自定义字段、按钮级权限落地率 2.3%、无 SSO/2FA/OAuth2/字段加密/限流——**这几项恰是政企与中大型企业采购的一票否决项**，也是本项目当前最难通过正式招投标的部分。

### 4.2 与三类对标物的差异化定位

| 对标 | 本项目胜出 | 本项目落后 |
|---|---|---|
| vs 开源 CRM（EspoCRM/SuiteCRM） | 业务广度（CPQ/配额/续约/开放平台）、工程规范（乐观锁/SSRF/Webhook 签名）、国产化（中文 PDF、企微钉钉通知通道、i18n） | 安全合规深度（FLS 覆盖、SSO、2FA）、社区生态（无插件市场） |
| vs 国产第一梯队（纷享/销售易） | 数据保留/GDPR、使用地图等特色模块；自部署可控 | 多租户、企微钉钉深度集成、旅程编排、短信、移动端体验、PaaS 成熟度 |
| vs 国际标杆（Salesforce/HubSpot） | 私有化部署、成本可控、数据主权 | 区域管理、预测 rollup、可配置定价、报表引擎、AI/Agent、生态与沙箱 |

---

## 五、差距清单与建议优先级

### P0 — 阻断企业级采购 / 合规红线（建议 3 个月内）

| # | 差距 | 影响 | 建议动作 |
|---|---|---|---|
| 1 | **字段级权限只作用于自定义字段** | 安全承诺与实现不符；核心字段（客户金额、合同价）无法对角色隐藏 | 在序列化层（DTO 出参）做统一过滤 + 写前校验，覆盖内置字段 |
| 2 | **按钮级权限落地率 2.3%** | 有权限码定义却未接线，越权操作风险 | 接 `PermissionGuard` 到各列表/详情页操作按钮；接入率纳入验收标准 |
| 3 | **无 SSO（SAML/OIDC）与 2FA** | 中大型企业安全基线，招投标硬指标 | 082（TOTP）已规格化，先落地 2FA（0/29 任务）；SSO 单独立项 |
| 4 | **SLA 无扫描与自动升级** | 工单 SLA 字段形同虚设，客服模块对外承诺不实 | 新增 `@Scheduled` 扫描作业：到期提醒 + 超时升级 + `slaStatus` 更新 + 合规率统计 |
| 5 | **全局限流缺失（仅 1 处）** | 登录暴力破解、开放 API 资源耗尽 | 网关/过滤器层统一限流：登录、开放 API、导出、邮件追踪 |
| 6 | **邮件同步为模拟实现** | 对客户宣称与实际不符的诚信风险 | 要么接真实 IMAP（Gmail/Outlook/Exchange），要么从功能清单与宣传中移除 |

### P1 — 竞争力核心（建议 6 个月内）

| # | 差距 | 影响 |
|---|---|---|
| 7 | **AI 能力空白** | Gartner 2025 已将原生 AI 列为评估门槛；建议先做"AI 邮件内容生成 + 商机摘要 + NL2SQL 查询"三个高价值低风险场景 |
| 8 | **无区域管理（Territory）** | 配额、客户分配、数据权限均无法按区域/行业建模，中大型销售组织无法落地 |
| 9 | **无预测 rollup 与提交** | 管理者看不到"人报的数"vs"系统算的数"，预测无法用于决策 |
| 10 | **数据权限靠手动调用而非全局拦截** | 新增模块易漏，是长期安全债 |
| 11 | **无看板拖拽（Kanban）** | 销售日常体验刚需，国产 CRM 标配 |
| 12 | **订单无行项目** | B2B 多产品订单无法表达 |
| 13 | **无旅程编排 / 短信 / ESP 连接器** | 营销只能"群发一次"，无法持续培育；国内触达主渠道缺失 |
| 14 | **审计日志无防篡改、无认证事件审计** | 合规审计不完整 |

### P2 — 中长期 / 战略选项

15. 多租户化（若计划做 SaaS 或集团版，须提前重构，代价大）
16. 应用市场 / iPaaS 集成平台 / OAuth2 授权码模式
17. 原生移动 App（当前 PWA 已可覆盖多数场景，优先级可后置）
18. 订阅计费、价格手册与配置器（视是否切入订阅制商业模式）
19. i18n 补齐（46 个硬编码页面）——工作量确定、风险低，可作为穿插任务持续推进
20. 合规认证（等保三级 / SOC 2）——国内投标的实际门槛

---

## 六、对 v1.0 报告的失实更正

原 `CRM_FEATURE_COMPARISON.md`（v1.0）为"全 ✅ 清单"，本次代码核对发现以下判定需要更正：

| v1.0 描述 | 代码实测 | 性质 |
|---|---|---|
| "核心 CRM 功能覆盖率 **100%**" | 广度覆盖成立，但**深度普遍不足**（见第二、三节）；且存在多项"字段已建模、引擎未实现"的情况 | **误导性表述** |
| "AI/预测功能覆盖率 **20%**" | 实为**规则 + 统计**，无任何 ML/LLM/Agent，**偏高估** | 高估 |
| "审计日志 ✅ 已实现" | 表与查询页存在，但无认证事件审计、无防篡改、无导出 | 高估（应为 ⚠️） |
| "数据保留策略 ✅ 已实现" | 实现为**软删除**，非归档/匿名化，无硬删除 | 高估（应为 ⚠️） |
| "GDPR 合规 ✅ 已实现" | 仅"数据可携带权"，**无被遗忘权删除流、无同意管理、导出无加密** | 高估（应为 ⚠️） |
| "字段权限 ✅ 已实现" | **仅作用于自定义字段**，内置字段与 API 出参未过滤 | 严重高估（应为 ❌） |
| "双因素认证 ❌ 未实现" | 判断正确，但**未提及 `specs/082` 已有完整 TOTP 规格（0/29 任务）** | 信息不全 |
| 未提及 | **SLA 无定时扫描与自动升级**（全库仅 2 个 `@Scheduled`） | 遗漏关键缺口 |
| 未提及 | **邮件同步为 `simulateSync` 模拟实现** | 遗漏关键缺口 |
| 未提及 | **多租户缺失**、**无区域管理**、**无限流**、**字段明文存储** | 遗漏关键缺口 |
| 未提及 | 按钮级权限仅 2/86 页面接入，`PermissionGuard` 为死代码 | 遗漏关键缺口 |

---

## 七、调研来源

**国际产品与分析师框架**
- [Gartner Magic Quadrant for Sales Force Automation Platforms 2025: The Rundown — CX Today](https://www.cxtoday.com/marketing-sales-technology/gartner-magic-quadrant-for-sales-force-automation-platforms-sfa-2025-the-rundown/)
- [HubSpot CRM vs. Salesforce Sales Cloud in 2025 — Taloflow](https://www.taloflow.ai/guides/comparisons/hubspotcrm-vs-salesforcesalescloud-crm)
- [HubSpot vs Salesforce: Complete CRM Comparison — Capital S](https://capitalsconsulting.com/resources/hubspot-vs-salesforce)
- [HubSpot Breeze vs Salesforce Agentforce vs Einstein — Octave](https://www.octavehq.com/post/hubspot-breeze-vs-salesforce-agentforce-vs-einstein)
- [Enterprise AI CRM Platforms Compared for Large Companies — Coffee](https://blog.coffee.ai/enterprise-ai-crm-platforms-comparison)
- [How to choose the right CRM software — TechTarget](https://www.techtarget.com/enterprise-software/tip/How-to-choose-the-right-CRM-software-for-your-organization)
- [B2B CRM: A buyer's guide for 2025 — Capsule](https://capsulecrm.com/blog/b2b-crm/)
- [Buyers' Guide: Enterprise CRM Platforms — Everest Group](https://www.everestgrp.com/report/egr-2026-43-o-8112/)

**国内厂商与标准**
- [CRM系统有哪些常见功能模块？— Zoho CRM](https://zdblogs.zoho.com.cn/crm/articles/functional-module1204.html)
- [CRM系统功能详解：助力企业客户全生命周期管理 — Zoho CRM](https://zdblogs.zoho.com.cn/crm/articles/life0618.html)
- [国内主流CRM厂商对比：SaaS通用、纯本地部署与ERP生态，三条路线怎么选？— 搜狐](https://www.sohu.com/a/1065212617_122909730)
- [深度解析：销售易CRM、神州云动CRM与纷享销客CRM的品牌特色与核心优势 — CSDN](https://adg.csdn.net/6970862c437a6b40336a88d3.html)
- [国产 CRM 系统推荐指南：三大梯队、10款产品深度对比 — 掘金](https://juejin.cn/post/7583615094362751030)
- [十大CRM厂商生态能力对比：API开放度与集成难度 — Worktile](https://worktile.com/kb/p/3962236)
- [迈富时联合中国信通院发布国内首份CRM智能化能力成熟度模型](https://www.marketingforce.com/about/newsshow/1845.html?lang=cn)

**开源与自托管参照**
- [Self-Hosted CRM in 2026: SuiteCRM vs EspoCRM vs Odoo Community vs Twenty — DEV](https://dev.to/enfernandes/self-hosted-crm-in-2026-suitecrm-vs-espocrm-vs-odoo-community-vs-twenty-3p3b)
- [2026 开源 CRM 系统盘点：6 款主流方案功能与二开能力横评 — CSDN](https://blog.csdn.net/zhouzhongyan/article/details/161706990)
- [顶级开源CRM软件，适用于团队和初创公司 — Lark](https://www.larksuite.com/zh_cn/blog/open-source-crm)
- [企业级自托管 CRM 推荐（支持 RBAC、AI 和 API）— NocoBase](https://www.cnblogs.com/nocobase/p/19926168)

---

*本报告由工作区代码实测 + 公开资料调研生成，评分部分为主观判定，供选型与规划参考。*
