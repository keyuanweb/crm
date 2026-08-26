# SDD 规格驱动开发文档索引

**仓库**: `E:\code\crm` | **流程**: Spec Kit（`/speckit-*` 命令）| **版本**: v0.1.0+（V1~V54 迁移）

> 本文档是全部 Spec-Driven Development 产物的导航入口。每个模块按统一流程
> `spec → plan → tasks → implement → verify` 迭代，文档遵守 [章程](../../.specify/memory/constitution.md) 的
> 契约优先、分层架构、测试优先等原则。

## 工作流与文档类型

| 产物 | 命令 | 内容 | 位置 |
|---|---|---|---|
| 规格 spec.md | `/speckit-specify` | 用户故事/验收场景/功能需求/成功标准（面向业务） | `specs/<NNN>-<name>/spec.md` |
| 计划 plan.md | `/speckit-plan` | 技术方案/技术上下文/章程检查/项目结构 | `specs/<NNN>-<name>/plan.md` |
| 研究 research.md | `/speckit-plan` | 关键技术决策（Decision/Rationale/Alternatives） | `specs/<NNN>-<name>/research.md` |
| 数据模型 data-model.md | `/speckit-plan` | 实体/字段/索引/枚举/状态机 | `specs/<NNN>-<name>/data-model.md` |
| 契约 contracts/ | `/speckit-plan` | REST API 请求/响应/错误码/权限矩阵 | `specs/<NNN>-<name>/contracts/*.md` |
| 任务 tasks.md | `/speckit-tasks` | 按用户故事分组的实施清单（勾选=完成） | `specs/<NNN>-<name>/tasks.md` |
| 验证指南 quickstart.md | `/speckit-plan` | 端到端验证场景与预期 | `specs/<NNN>-<name>/quickstart.md` |
| 规格质量检查 checklists/ | `/speckit-specify` | 规格完整性自检 | `specs/<NNN>-<name>/checklists/requirements.md` |
| 路线图 roadmap.md | 人工维护 | 全局实施顺序与完成状态 | `specs/roadmap.md` |

## 契约总入口

- **全局契约约定**（Base URL / 认证 / 分页信封 / 错误格式 / 状态码 / 角色矩阵）：[001-crm-core/contracts/README.md](./001-crm-core/contracts/README.md)
- 各模块契约见下方模块表 `contracts/` 列；后端以 springdoc-openapi（Swagger 3.0）暴露同一契约。

## 模块清单（按实施顺序）

| # | 模块 | 阶段 | 状态 | 文档 | 契约 |
|---|---|---|---|---|---|
| 001 | 客户核心（客户/商机/销售机会/跟进/统计/认证） | P0 | ✅ | [目录](./001-crm-core/) | auth/customers/opportunities/sales-opportunities/follow-ups/stats |
| 002 | 用户管理（用户 CRUD/角色/密码/令牌失效） | P0 | ✅ | [目录](./002-user-management/) | users |
| 003 | 系统加固（唯一约束/缓存/CORS/认证性能） | P0 | ✅ | [目录](./003-system-hardening/) | —（配置与约束，无新端点） |
| 004 | 线索管理（线索池/分配/转化/Excel） | P1 | ✅ | [目录](./004-lead-management/) | leads |
| 005 | 联系人管理（联系人 CRUD/角色/客户详情集成） | P1 | ✅ | [目录](./005-contact-management/) | contacts |
| 006 | 销售仪表盘（指标卡/漏斗/预测/业绩/客户分析） | P1 | ✅ | [目录](./006-sales-dashboard/) | stats |
| 007 | 产品与报价 CPQ（产品/报价单/PDF/审批） | P1 | ✅ | [目录](./007-product-cpq/) | products-quotes |
| 008 | 合同管理（合同/审批/附件/模板） | P1 | ✅ | [目录](./008-contract-management/) | contracts |
| 009 | 订单与回款（订单/分期回款/应收账款） | P1 | ✅ | [目录](./009-order-payment/) | orders |
| 010 | 任务与提醒（待办/日历/跟进计划） | P1 | ✅ | [目录](./010-task-reminder/) | tasks |
| 011 | 客户公海（公海规则/领取/批量转移） | P1 | ✅ | [目录](./011-customer-pool/) | customer-pool |
| 012 | 数据权限（部门/行级权限/客户共享） | P1 | ✅ | [目录](./012-data-permission/) | data-permission |
| 013 | 工作流自动化（规则/触发/通知/日志） | P1 | ✅ | [目录](./013-workflow-automation/) | workflow |
| 014 | 市场营销（活动/归因/渠道 ROI） | P2 | ✅ | [目录](./014-marketing/) | marketing |
| 015 | 客户服务（工单/知识库/SLA） | P2 | ✅ | [目录](./015-customer-service/) | tickets/knowledge/sla |
| 016 | 系统增强（自定义字段/通知中心/导出/移动端） | P2 | ✅ | [目录](./016-system-enhancement/) | custom-fields/notifications/exports |
| 017 | 登录验证码（图形验证码/Redis 一次性） | P3 | ✅ | [目录](./017-login-captcha/) | auth-captcha |
| 018 | 客户360（全景视图/健康度） | P3 | ✅ | [目录](./018-customer-360/) | customer-360 |
| 019 | 线索评分（规则配置/自动评分） | P3 | ✅ | [目录](./019-lead-scoring/) | lead-scoring |
| 020 | 销售目标（个人/全局） | P3 | ✅ | [目录](./020-sales-targets/) | sales-targets |
| 021 | 自定义报表（模板/聚合） | P3 | ✅ | [目录](./021-custom-reports/) | custom-reports |
| 022 | AI 助手（智能建议） | P3 | ✅ | [目录](./022-ai-assistant/) | smart-suggestions |
| 023 | KPI 看板（大屏/排行榜） | P3 | ✅ | [目录](./023-kpi-dashboard/) | kpi-board |
| 024 | 批量导入（多实体 Excel） | P3 | ✅ | [目录](./024-bulk-import/) | bulk-import |
| 025 | 回收站（软删恢复） | P3 | ✅ | [目录](./025-recycle-bin/) | recycle-bin |
| 026 | 实时通知（WebSocket） | P3 | ✅ | [目录](./026-realtime-notify/) | realtime-notify |
| 027 | 移动端 PWA（离线/安装/推送） | P3 | ✅ | [目录](./027-mobile-pwa/) | pwa |
| 028 | 角色权限（角色-菜单-权限点） | P3 | ✅ | [目录](./028-role-permissions/) | role-permissions |
| 029 | 使用地图（系统使用视图） | P3 | ✅ | [目录](./029-usage-map/) | usage-map |
| 030 | 邮件营销（模板/群发/追踪） | P4 | ✅ | [目录](./030-email-marketing/) | email-marketing |
| 031 | 客户标签（标签/动态细分） | P4 | ✅ | [目录](./031-customer-tags/) | customer-tags |
| 032 | 全局搜索（跨实体统一搜索） | P4 | ✅ | [目录](./032-global-search/) | global-search |
| 033 | 审批流（多级条件审批） | P4 | ✅ | [目录](./033-approval-flow/) | approval-flow |
| 034 | 客户合并（查重/合并） | P4 | ✅ | [目录](./034-customer-merge/) | customer-merge |
| 035 | 外勤拜访（计划/签到） | P4 | ✅ | [目录](./035-field-visit/) | field-visit |
| 036 | 在线表单（官网线索收集） | P4 | ✅ | [目录](./036-online-forms/) | online-forms |
| 037 | 公告协作（公告/评论 @提及） | P4 | ✅ | [目录](./037-announcements/) | announcements |
| 038 | 发票管理（开票/状态跟踪） | P4 | ✅ | [目录](./038-invoice/) | invoice |
| 039 | 前端体验优化（菜单切换防闪烁/客户分析卡布局） | P5 | ✅ | [目录](./039-dashboard-ux/) | —（纯前端，无契约变更） |
| 040 | 菜单分类重设计（8 组业务域/权限归位） | P5 | ✅ | [目录](./040-menu-redesign/) | —（纯前端，无契约变更） |
| 041 | 系统管理二级子组（组织权限/流程配置/审计维护） | P5 | ✅ | [目录](./041-menu-system-split/) | —（纯前端，无契约变更） |
| 042 | 系统管理扁平化（子组提升一级/组织权限更名系统管理） | P5 | ✅ | [目录](./042-menu-system-flatten/) | —（纯前端，无契约变更） |
| 043 | 差距分析（对标成熟 CRM 的补齐方案） | 规划 | ✅ | [目录](./043-crm-gap-analysis/) | —（评审稿，未实施） |
| 044 | 菜单对标调整（命名/分类 + 规划中占位） | P5 | ✅ | [目录](./044-menu-crm-alignment/) | —（纯前端，无契约变更） |
| 045 | 销售 Playbook（阶段动作模板/动作引导/必做校验） | P0 | ✅ | [目录](./045-sales-playbook/) | playbook |
| 046 | 合同续约（到期提醒/续约链/漏斗视图） | P0 | ✅ | [目录](./046-contract-renewal/) | renewal |
| 047 | 电子签署（报价/合同在线签署/签署记录） | P0 | ✅ | [目录](./047-e-signature/) | signature |
| 048 | 登录验证码默认关闭（可环境变量恢复） | P5 | ✅ | [目录](./048-captcha-toggle/) | —（配置变更，无契约变更） |
| 049 | 营销自动化（评分阈值/标签变更事件 + 邮件/标签/任务动作） | P1 | ✅ | [目录](./049-marketing-automation/) | —（复用 013 工作流，无新契约） |
| 051 | 工单满意度调查（CSAT/NPS 统计） | P1 | ✅ | [目录](./051-csat-nps/) | survey |
| 050 | 客户自助门户（知识库浏览/在线提单/进度查询） | P1 | ✅ | [目录](./050-customer-portal/) | portal |
| 052 | 邮件高级能力（退订管理/送达统计/A-B 主题测试） | P1 | ✅ | [目录](./052-email-advanced/) | email |
| 054 | SLA 工作时间与节假日日历 | P1 | ✅ | [目录](./054-sla-calendar/) | sla-calendar |
| 053 | 托管落地页 + UTM 跟踪归因 | P1 | ✅ | [目录](./053-landing-page/) | landing-pages |
| 055 | 开放平台（API Key 管理 + Webhook 事件订阅） | P2 | ✅ | [目录](./055-open-platform/) | platform |
| 056 | 字段级读写权限（隐藏/只读/可编辑按角色） | P2 | ✅ | [目录](./056-field-permission/) | field-permissions |
| 057 | 多币种（汇率管理/产品多币种价格/折算） | P2 | ✅ | [目录](./057-multi-currency/) | currencies |
| 058 | 集成中心（第三方通知通道/事件推送） | P2 | ✅ | [目录](./058-integration-hub/) | integration-hub |
| 059 | 自定义对象（低代码元数据驱动建模） | P3 | ✅ | [目录](./059-custom-object/) | custom-objects |
| 060 | 国际化（语言切换/核心文案资源化） | P3 | ✅ | [目录](./060-i18n/) | —（纯前端，无契约变更） |
| 061 | 通话记录管理（CTI 数据模型/接口就绪） | P3 | ✅ | [目录](./061-call-center/) | call-records |
| 062 | 邮件账户配置/同步记录框架（模拟同步） | P3 | ✅ | [目录](./062-email-sync/) | mail-sync |
| 063 | 权限体系加固（行级数据权限/导出安全/异常） | 安全 | ✅ | [目录](./063-security-hardening/) | —（行为加固） |
| 064 | 性能与数据完整性（预警批量聚合/默认负责人/只读事务） | 安全 | ✅ | [目录](./064-performance-integrity/) | —（行为加固） |
| 065 | 体验优化批次（预测去重/详情编辑/多币种价/布局/文案） | 体验 | ✅ | [目录](./065-ux-optimizations/) | —（行为/展示优化） |
| 066 | 更多页面国际化（7 列表页 + 公共文案） | 体验 | ✅ | [目录](./066-i18n-pages/) | —（前端文案） |

> 阶段：P0=核心销售链路补全；P1=成交链路延伸 / 效率与自动化；P2=扩展模块；
> P3=智能化与平台增强；P4=营销闭环与协作扩展（批次建议见 [roadmap-p0p1.md](./roadmap-p0p1.md)）；
> P5=体验优化（前端）。
> 完整阶段定义与依赖关系见 [roadmap.md](./roadmap.md)。

## 数据库迁移对照（Flyway V1~V54）

| 迁移 | 模块 | 内容 |
|---|---|---|
| V1~V5 | 001/002 | 初始 schema、用户审计字段、审计日志、认证字段、跟进软删除 |
| V6 | 003 | customer 唯一约束（生成列 active_key） |
| V7~V9 | 004 | lead 表、follow_up.lead_id、customer_id 可空 |
| V10 | 005 | contact 表 |
| V11~V15 | 006/007 | sales_target、product、quote、quote_item（含版本） |
| V16~V18 | 008 | contract、contract_attachment、contract_template |
| V19~V21 | 009 | sales_order、payment_plan、payment_record |
| V22 | 010 | task_item |
| V23~V26 | 011/012 | customer.owner_id、department、user.data_scope、customer_share |
| V27~V31 | 013 | workflow_rule、workflow_execution_log（软删除/updated_at）、workflow_notification |
| V32~V33 | 014 | marketing_campaign、lead/customer.campaign_id |
| V34~V37 | 015 | ticket、ticket_reply、knowledge_article、sla_policy |
| V38~V41 | 016 | custom_field、custom_field_value、export_job、notification（含 013 数据迁移） |
| V42~V43 | 017-019 | health_score_config、lead_score_config |
| V44~V45 | 020/021 | sales_target.user_id（个人目标）、report_template |
| V46 | 028 | role、role_menu、role_permission |
| V47~V49 | 031/030/033 | tag、customer_tag、segment；email_campaign/send_log/template/track；approval_* |
| V50~V53 | 035-038 | field_visit；form、form_submission；announcement、announcement_read、comment；invoice |
| V54 | 032 | 全局搜索索引 |

## 使用建议

- **新功能开发**：遵循 `/speckit-specify → /speckit-plan → /speckit-tasks → /speckit-implement → /speckit-converge` 全流程，产物落在新目录 `specs/<NNN>-<name>/`。
- **查阅某模块**：从上方模块表进入，按 spec（业务）→ plan（技术）→ contracts（接口）→ tasks（实施记录）顺序阅读。
- **改动已应用迁移**：禁止编辑已应用 migration（Flyway checksum），需新增迁移号 V55+ 并同步 `backend/src/test/resources/schema-h2.sql`。
