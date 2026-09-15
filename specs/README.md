# SDD 规格驱动开发文档索引

**仓库**: `E:\code\crm` | **流程**: Spec Kit（`/speckit-*` 命令）| **版本**: v0.1.0+（Flyway V1~V88，共 **87** 个迁移脚本，**V72 不存在**；083 为工程收口，其中 V88 是补列迁移；084 含 V85/V86 两条**数据迁移**——只动 `role_permission`，无 DDL、无新表；085 为验证门禁转绿，**无迁移**、**无契约变更**；086 为前端按钮级权限收口，**无迁移**、**无后端改动**、**无契约变更**；087 为接线码的渲染层用例补课，**零生产代码改动**、**无迁移**、**无后端改动**、**无契约变更**；089 为 Java 基线升级（17 → 21，只动具有执行效力的版本声明点），**零依赖升级**、**零源码改动**、**无迁移**、**无契约变更**；090 为前端列表页内容区撑满（改造类，**无迁移、无后端改动、无契约变更**；**事后立项回填**，见该目录 `spec.md` 的由来说明）；091 为窄屏（≤767px）外壳内容区塌陷修复（改造类，**无迁移、无后端改动、无契约变更**；判据分两层——几何走浏览器实测脚本、结构走单测，理由见该目录 `research.md` §3）；094 为 088 三笔尾巴账的收口（纯前端渲染层改动，**无迁移、无后端改动、无契约变更**；只动 3 个 tsx 的 4 个属性 + 1 个门禁脚本，其余全是**订正与留痕**——`span="filled"` 那条收口办法经实测**不成立**、就地作废，FR-015 的过宽措辞收窄，并给 FR-015 补上它自己承认缺失的门禁 **R8**））

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
| 067 | 使用地图视觉样式优化 | 体验 | ✅ | [目录](./067-usage-map-ux/) | —（纯前端） |
| 068 | 部门管理页面优化（描述/排序/层级） | 体验 | ✅ | [目录](./068-department-management-optimization/) | departments |
| 070 | 个人中心（资料/安全/偏好） | 体验 | ✅ | [目录](./070-personal-center/) | personal |
| 071 | 酷炫数据大屏（Data Vision 全屏看板） | 体验 | ✅ | [目录](./071-data-vision/) | —（复用 stats） |
| 072 | 后台布局重设计（菜单/内容区/防闪烁） | 体验 | ✅ | [目录](./072-backend-layout-redesign/) | —（纯前端） |
| 073 | 首页仪表盘重设计 | 体验 | ✅ | [目录](./073-dashboard-redesign/) | —（纯前端） |
| 074 | 全项目中英文国际化补全 | 体验 | ✅ | [目录](./074-full-i18n/) | —（前端文案） |
| 075 | 全页面中英文国际化补全 | 体验 | ✅ | [目录](./075-page-i18n/) | —（前端文案） |
| 076 | ProTable 搜索表单按钮国际化 | 体验 | ✅ | [目录](./076-protable-i18n/) | —（前端文案） |
| 077 | 核心销售链路页面重设计（现代化风格） | 体验 | ✅ | [目录](./077-core-sales-redesign/) | —（纯前端） |
| 078 | 销售配额分解（逐层分解/达成率/版本/对比） | P1 | ✅ | [目录](./078-sales-quota/) | sales-quota-api |
| 079 | 定时导出订阅（Cron 调度/邮件通知/执行历史） | P1 | ✅ | [目录](./079-scheduled-export/) | scheduled-export-api |
| 080 | 数据保留策略（归档/执行历史/合规导出） | P2 | ✅ | [目录](./080-data-retention/) | data-retention-api |
| 081 | 角色权限更新（11 个预置角色 + 权限矩阵） | P2 | ✅ | [目录](./081-role-permissions-update/) | —（design/tasks，无 spec） |
| 082 | 双因素认证（TOTP 动态码/绑定向导/恢复码/管理员重置） | 安全 | ⏳ **未实施** | [目录](./082-two-factor-auth/) | auth-mfa |
| 083 | 工程收口（门禁生效/集成测试执行与覆盖率/安全修复/性能优化/部署缺陷） | 治理 | ✅ | [目录](./083-engineering-consolidation/) | —（无新端点；授权语义变更记入 055 的 open-platform 契约） |
| 084 | 菜单信息架构与授权可见性收口（撤三组硬门/名称与分组两侧统一/归属按业务域归位/单一真相源与护栏） | P1 | ✅ | [目录](./084-menu-ia-authorization/) | authorization-semantics |
| 085 | 验证门禁转绿（4 例陈旧集成测试失败背后的 **3 个生产缺陷**：登录连带自增乐观锁令牌／关闭端点缺结果时 400 与契约 422 不符／投递记录在飞窗口不可见且中断即丢失） | 治理 | ✅ | [目录](./085-verify-green/) | —（无新端点、无端点语义变更；与 003/083 同为**加固类**形制，不产 `contracts/`） |
| 086 | 前端按钮级权限收口（45 个未接线页面 + 8 个已接线页面的漏网按钮按后端**同一个码**收口；`check-perms.mjs` 护栏 + 后端 `FrontendPermissionCodeAlignmentTest` 双向机器校验） | P1 | ✅ | [目录](./086-frontend-button-gating/) | —（纯前端渲染层改动；无新端点、**无任何后端改动**、无迁移） |
| 087 | 接线码的渲染层用例补课（为 086 盘点出的「已接线但零本页用例」的权限码补双向渲染用例，逐条变异自验；**范围由 15 订正为 18**——另 3 个码被 086 的"字面量出现在测试里"口径误判为已覆盖，重扫全仓后按同类扩入） | P1 | ✅ | [目录](./087-perm-test-backfill/) | —（**零生产代码改动**，只新增/追加 `*.perm.test.tsx`；无新端点、无后端改动、无迁移） |
| 088 | 前端布局一致性（P1 把主色/字体/圆角的**真源**搬进 `src/theme/index.ts` 并落地 6 个 `components/ui/` 新增源文件（四个原语 + 两个纯函数模块）——此后主色由 antd 自己派生，那些「对抗性 CSS」才谈得上退场；P2 在 **4 个样板页**逐页收敛到原语；P3 铺开——**22 个横向表单**（写死 `labelCol` **18 处 → 0**）+ **27 个纵向表单**（纵向下限 **200** 为用户裁决）改**容器驱动栅格**、**6 处页面级表单**加 `maxCols={3}`、**4 个详情页** `Descriptions` 列数改按断点响应（**回填 FR-015**，属**代码先于规格**）；P4 退役对抗性 CSS 与圆角字面量。**判据是七道门禁 + 白名单单调收缩**：R2 由 **22 处 / 16 文件 → 0**、R3 由 **89 → 0**，两条规则翻成**默认档 error** 后 `--strict` 不再改变任何行为、遂**整条退役**（`ui:check:strict` 一并删除）——**⚠️ 真正的收益是 R3 那 89 处债此前从未进过 CI 视野**（CI 一直跑默认档）；其余读数 `borderRadius: 10` **62 处 / 53 文件 → 0**、`!important` **实体 22 → 10**、白名单冻结 **53** 处、真孤儿 `ContactsCard.tsx` 与 R7 条目一并删除。P4 四项里**两项的判词经实测不成立**（T052/T053），一律**原文保留 + ⚠️ 订正**；定向破坏逐个做、逐字节还原。**SC-005 视觉验收**（本规格唯一关口，明文由用户执行）于 **2026-09-15 由用户签字通过**——原文点名 4 个样板页而 T040–T044 改的是别的页面，本次是**扩用**、粒度是**用户自述**） | P1 | ✅ | [目录](./088-frontend-layout-consistency/) | —（纯前端渲染层改动；**无新端点、无后端改动、无迁移、无契约变更**。取证脚本 `measure-ui-baseline.mjs` 与 `baseline-output.txt` 随目录入库） |
| 089 | Java 基线升级（JDK 17 → 21；只改**具有执行效力**的版本声明点——5 处执行性声明 + 2 处 CI 步骤名 + 3 份文档——**零依赖升级、零源码改动**） | 治理 | ✅ | [目录](./089-jdk21-upgrade/) | —（无新端点、无端点语义变更；与 003/083/085/087 同为**加固类**形制，不产 `contracts/`） |
| 090 | 列表页内容区撑满（表格卡片吃满剩余高度、分页落到卡片底部；48 个 ProTable 页 + 7 个 Card 作页根页 + 3 个 Tabs 内嵌页，共用层一处改动；顺带修掉每页约 36px 幽灵滚动与查询表单末行 24px 死白。**⚠️ 事后立项回填**，另有 2 项明确划出的遗留见其 `tasks.md`） | P1 | ✅ | [目录](./090-list-page-fill-height/) | —（纯前端渲染层样式；无新端点、**无后端改动**、无迁移。取证脚本 `measure-fill-height.mjs` 随目录入库） |
| 091 | 窄屏外壳内容区塌陷修复（**≤767px 下整个内容区宽度恒为 0**——内容渲染完整（实测内容宽 141–633）却挤在一条 **24px** 的缝里，用户既看不见也点不到，且**没有横向滚动条这条退路**。根因：组件库对「含侧边栏的布局」施加的 `width: 0`，靠**横向**可伸缩把宽度补回，而外壳在窄屏把主轴改成纵向 ⇒ 补偿作用于高度、宽度停在 0（**内联样式能改方向，改不了那条作用在子节点上的规则**）。窄屏**不再渲染侧边栏组件**、改用普通容器承载既有横向菜单，一刀同时修掉菜单的 **200×200 方块**形态。判据**分两层**：几何走浏览器实测脚本、结构走单测——测试环境无布局引擎，几何在单测里量不出来） | P1 | ✅ | [目录](./091-narrow-shell-collapse/) | —（纯前端外壳的条件渲染；无新端点、**无后端改动**、无迁移、无契约变更。取证脚本 `measure-narrow-shell.mjs`、`baseline-output.txt`/`after-output.txt` 与 `falsification-evidence.md` 随目录入库） |
| 092 | 列表页与窄屏外壳几何的端到端护栏（把 090 与 091 的**几何判据**变成机器门禁——这两项的几何此前**无任何自动化用例覆盖**，因为测试环境**没有布局引擎**，几何在单测里量不出来，回归只能靠人复跑取证脚本。用 Playwright 打真实布局引擎，接进既有的 `pnpm run test:e2e`。判据一律**相对量**（页脚高度**现量**，不写死 664/624/56）；短/长页按「滚动盒是否溢出」**运行时判定**，不写死路径清单；每类不变式带**活性下限硬断言**（零样本 = 红）；窄屏的两处宽度**分成两条独立断言**——只断言两者之差会被缺陷态同时满足（`0 = 24 − 24`），那是一条会放过原缺陷的假判据。**三次定向破坏**逐个做、逐个**逐字节还原**：① 090 入口 A 只红 ProTable 桶、② 入口 B 只红 Card 桶（两个失败集合**互补**，证明报告可定位）、③ 091 的窄屏分支退回旧写法，红在内容区宽度与菜单上而**内容容器宽那条仍绿**——正好实证了上面那条假判据；还原后全量 e2e 58/58 绿） | P1 | ✅ | [目录](./092-geometry-e2e-guard/) | —（**纯新增测试**：3 个文件，**零生产代码改动**、无新端点、无后端改动、无迁移、无契约变更。留痕 `falsification-evidence.md` 随目录入库） |
| 094 | 088 三笔尾巴账的收口（**不改 088 的结论，只清算 088 自己写下的欠账**。三笔性质不同：① **「全宽项改 `span="filled"`」这条收口办法经实测不成立**——本仓库全走 children 写法，其 `span` 类型是 `number`（`descriptions/Item.d.ts`），实测报 `TS2322`，允许 `'filled'` 的是服务于 `items` prop 的**另一个**类型；**作废 + 订正记录**（6 处 ⚠️、原文一律保留），并写明三条真实路径；② **`MIN_CANDIDATES.R3 = 1` 的定性订正**——**不是「假红」，是「诊断信息错」**：红色本身是对的（一条判不到对象的规则已不再是护栏，该按 R2/T040、R3/T045 先例退役），错的只是失败信息只印了「怀疑扫描器」这一种成因，与「规则该退役」的处置**相反**；**零行为改动**，只把信息分得清 + 补 `CANDIDATE_READINGS` 实测读数和 `MIN_CANDIDATES` 的逐条实测解剖注释；③ **FR-015 的存量违规实测缩为 3 处真违规**（原报 5 处是**按「字形相似」列的清单**，`PersonalCenterPage` 的 `{ xs: 1, sm: 2 }` 本就合规——`md` 由 `DEFAULT_COLUMN_MAP` 补成 3；`CustomerPortalPage` 的 `column={1}` 是最窄档的刻意设计 ⇒ 差在**判据**、不在代码），**补齐 3 处**并**订正 FR-015 的过宽措辞**（写清适用边界与两处例外）。**新增门禁规则 R8**（默认档、零容忍无白名单）：`Descriptions` 的 `column` 不得写死为大于 1 的数字——**FR-015 从此有门禁**（它原文自认「既没有门禁规则、也没有独立的自动化用例」）；**定向破坏**写回 `column={2}` ⇒ `ui:check` **exit 1** 且只红在【R8】、逐字节还原后转绿；**候选归零**留痕证明新失败信息分得清两种成因。**⚠️ 本项必然产生新的可见变化**（`SignSection`/`SurveyBlock` 旧写 `column={2}` ⇒ **md 及以上由 2 列变 3 列**，是桌面宽度下肉眼可见的；portal 的 xs/sm 亦变），**如实记为一笔新的 SC-005 欠账、不声称已获视觉背书**；A/B 读数（jsdom 只到 3 列档）与两处定向破坏的逐字输出见 `falsification-evidence.md`） | P1 | ✅ | [目录](./094-088-debt-closeout/) | —（纯前端渲染层改动；无新端点、**无后端改动**、无迁移、无契约变更） |

> 【后记，2026-09-13，**订正：083 行"门禁生效"须加限定，原文保留**】T069 实测本仓库**无远端、无 `gh`**，`.github/workflows/ci.yml` 的全部作业（含 e2e）**一次都不会触发**，故 083 的门禁口径**正式改为以本地命令为准**——声明见 `083-engineering-consolidation/spec.md`（FR-G10 处）与 `quickstart.md`（验证 8，逐道门禁的本地等效命令 + 实测记录）。**当日实测**：后端 `mvn -B verify` 退出码 1（surefire 551 例全绿、failsafe 274 例 4 失败、`failsafe-reports` 72 份），覆盖率门禁经 `-Dmaven.test.failure.ignore=true` 实际判定**通过**（INSTRUCTION 45 035/56 169 = **0.8018** ≥ 0.73）；前端六道门禁全部退出码 0；e2e `module-page-auth.spec.ts` 14 passed。**门禁本身有牙齿，缺的是"谁在跑它"**——配置远端后应改回以 CI 为准。

> 【后记，2026-09-13，**订正：082 行的 ✅ 是登记错误，原文留痕、不静默**】082 在**本表**与 `roadmap.md` 的 `## 当前进度` 里都标为已交付，**而它从未被实施**。逐项取证：① `specs/082-two-factor-auth/tasks.md` **0 / 29**，没有一项被勾选；② 生产代码零痕迹——`mfa|two.?factor|totp|recovery.?code` 在 `backend/src/main` 与 `frontend/src` **零命中**，换中文同义词（双因素/两步验证/二次验证/动态口令/恢复码/验证器）重扫**同样零命中**；③ Flyway 从无 `*two_factor*` 迁移，`V78` 实为 `V78__sla_escalation.sql`（`72a74e0`），即计划中的文件名已被占；④ **git 全历史、全分支**中碰过 `specs/082-*/` 的唯一提交是 `b939ef7 feat(specs): 082 双因素认证（TOTP 2FA）SDD 文档`——**只提交了文档，没有任何实现提交**。故状态列由 ✅ 改为 **⏳ 未实施**，**原文留痕于本后记，不删改历史**。
> **须与另两个规格区分**（同一批排查中一并发现，但性质不同，**未改动**）：`067-usage-map-ux`（0/11）与 `068-department-management-optimization`（0/42）同样一项未勾，但它们的**代码确实在**——`frontend/src/types/usageMap.ts` 里 `shadow`/`borderRadius`/`hoverHighlight` 三个字段齐备，`frontend/src/types/department.ts` 里 `description`/`sortOrder`/`memberCount`/`childCount` 四个字段齐备。**那两件是"做了没回填勾选"，属文档缺陷；082 是"登记了没做"，属登记错误。** 两者的修法完全不同，不可合并处置。067/068 的勾选回填**有意未做**——回填必须逐条核对实现与任务的对应关系，把"看着像做了"当成"确实做了"正是本项目反复踩到的假绿形态；在此记录缺口，交归属方处置。

> 编号说明：`069` 未创建（编号空缺）；`081` 仅有 `design.md` / `tasks.md`，未按标准流程产出 `spec.md`；`082` 为标准流程（spec/plan/data-model/contracts/tasks/quickstart）产出的待实施模块（**状态列的「⏳ 未实施」即为此意**——本表此前把它误标为 ✅，与这句自述自相矛盾，已于 2026-09-13 订正，见下方后记）；`083` 为标准流程产出的**加固类**模块（不产 `contracts/`，与 `003` 同形制），原设计**不含 Flyway 迁移**，但实施 T077 时发现 078 的三张 quota 子表缺 `BaseEntity` 公共列、写端点在生产库上 500，故**新增一条补列迁移 V88**（V87 属 1.5 批 3，一并补登），下方迁移对照表**已有 083 行**；`084` 为标准流程产出的**收口类**模块，与 `083` 的差别有二——它**产出了一份最小契约** `contracts/authorization-semantics.md`（因为自定义对象端点的授权判定语义确有变更，按原则一不得静默），且**含两条数据迁移**（V85/V86），故本表有 084 行；`085` 为标准流程产出的**加固类**模块（不产 `contracts/`，与 `003`/`083` 同形制），**无 Flyway 迁移**（新增的 `PENDING` 状态复用了 `webhook_delivery.status` 这个既有的 `VARCHAR(20)` 列，实测无需 DDL），**无契约变更**（关闭端点的 422 是**回归**既有契约而非修改契约），故下方迁移对照表**没有 085 行**；`086` 是**一期 1.5 的前端半场**（后端注解半场已在 1.5 批 1~3 完成），形制上是**收口类**——**零后端改动、零迁移、无 `contracts/`**（收口只改前端渲染层的判据，端点语义一字未动），故下方迁移对照表**没有 086 行**。它的两项可交付物不是代码而是**口径记录**：`research.md` 的「不可收口清单」（5 类后端**先加码才能收**的形态，是给后端的待办工单）与 `tasks.md` 记录的三处**偏差**（`contract_template:manage` 被后端结构迫使连新建/编辑一并收口；`department:manage` / `open_platform:manage` 两处判据**当下冗余**——页面取数的读端点挂的就是同一个码，仍挂是为了码与端点逐字对应、并待后端拆分读写码时立刻生效）；`087` 为标准流程产出的**加固类**模块（与 `003`/`083`/`085` 同形制），**零生产代码改动**（`git diff` 里除 `*.perm.test.tsx` 与 `specs/087-*/` 外无文件，可机器校验）、**无迁移**、**无契约变更**，故下方迁移对照表**没有 087 行**。它的可交付物不是能力而是**证据**，附带一条方法论订正：086 的 T103 用「码字面量是否出现在测试里」当覆盖口径，而**这个口径只在被用来"排除"时才暴露缺陷**——它划出的 15 个缺口是真的，**没被划出来的是假的**；087 收尾时改用「判据落点所在页面是否有用例引用该码」重扫全仓，68 个「页面×码」判据点里仍有 4 处无本页用例（`campaign:delete`、`email:manage` 的 2 个站点、`export:scheduled`），遂把范围由 15 订正为 18 个码。`089` 为标准流程产出的**加固类**模块（与 `003`/`083`/`085`/`087` 同形制），把 Java 基线由 **17 提到 21**——**零依赖升级、零源码改动、无迁移、无契约变更**，故下方迁移对照表**没有 089 行**。它的可行性依据是一条**可测量的字节值**：class major 65（Java 21）落在 `spring-core 6.1.1` 内置 ASM 的读取上限 **66** 之内，而 JDK 25 的 **69** 在之外——这正是本项由「升 25」改立为「升 21」的原因（25 的完整调查留档于 `089-jdk21-upgrade/jdk25-investigation.md`，被放弃那批改动的原文留档于同目录 `parked-jdk25.diff`）。`090` 为标准流程产出的**改造类**模块（与 086/088 同类——**改生产代码**），**无迁移、无后端改动、无契约变更**，故下方迁移对照表**没有 090 行**。**它与上面每一项都有一处不同，必须写明**：它是**事后立项回填**——代码先以 Claude 计划落地（提交 `f75496a` + `d60ad0f`），规格后补，因此它的 `spec.md` / `tasks.md` **不是**前置规划的产物；读它时不得据此声称本项走过 spec-first 流程（该事实写在 `spec.md` 的「本规格的由来」与 `tasks.md` 顶部的两条如实告知里）。它的范围也是**先窄后全**的：Tabs 内嵌的 5 张表在计划阶段被明确划为缺口并**如实告知用户**，事后才补齐——既不是偷偷缩范围，也不是偷偷扩范围。`091` 为标准流程产出的**改造类**模块（与 086/088/090 同类——改生产代码），**无迁移、无后端改动、无契约变更**，故下方迁移对照表**没有 091 行**。它的**判据形态**最值得一读：缺陷本身是**几何**的（内容区宽度塌陷），而测试环境**没有布局引擎**（`clientWidth` / `getBoundingClientRect()` 全是假值）⇒ 几何在单测里**量不出来**。故判据**分两层**——几何走浏览器实测脚本（`measure-narrow-shell.mjs`），结构走单测（断言那条 `width: 0` 规则的**命中前提**是否成立）。本项另有一处**订正留痕**：初稿称「窄屏分支从未被自动化用例执行过」，实测**是错的**——窄屏分支每次全量测试都真的执行了（`window.innerWidth` 可写、`resize` 可派发，既有用例已在这么做），真正的问题是**断言全落在文案上**，而文案在缺陷下完全正常；该订正已就地改写并留痕于其 `research.md` §4，**未静默**。与 090 的**边界**也要看清：090 的 `tasks.md` 把「767px 以下窄屏内容区塌陷」列为遗留，本项即该遗留的处置（090 的 T012 已回填指向本项）；但**各页面的窄屏适配**（表格横向滚动、表单单列、弹窗宽度）**不在**本项范围，见其 `spec.md` 非目标第 1 条。

> 阶段：P0=核心销售链路补全；P1=成交链路延伸 / 效率与自动化；P2=扩展模块；
> P3=智能化与平台增强；P4=营销闭环与协作扩展（批次建议见 [roadmap-p0p1.md](./roadmap-p0p1.md)）；
> P5=体验优化（前端）。
> 完整阶段定义与依赖关系见 [roadmap.md](./roadmap.md)。

## 数据库迁移对照（Flyway V1~V88，共 87 个脚本，V72 不存在）

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
| V55~V58 | 045/046/047 | stage_action_template、sales_opportunity_action、contract_renewal、signature_record |
| V59~V60 | 051/052 | ticket_survey、email_unsubscribe |
| V61~V62 | 054/053 | sla_calendar_config、landing_page |
| V63~V66 | 055/056/057/058 | open_platform、field_permission、multi_currency、integration_channel |
| V67~V69 | 059/061/062 | custom_object、call_record、mail_sync |
| V70 | 068 | department.description / sort_order |
| V71 | 078 | sales_quota（配额/明细/版本） |
| V72 | — | 编号空缺（未使用） |
| V73 | 079 | scheduled_export（订阅/执行记录） |
| V74 | 080 | data_retention（策略/执行记录） |
| V75 | 081 | role_permissions_update（预置角色与权限矩阵） |
| V76 | 修复 | user.email（登录 500 修复） |
| V77 | 修复 | opportunity.amount（首页 500 修复） |
| V78 | 082 | two_factor_auth（user 扩展列 + user_recovery_code 表 + 索引） |
| V79~V84 | 修复 / 055 | opportunity.stage（V79）与权限矩阵对齐批 2~2d（V80~V84，撤门接线与死授权清理；逐条见各迁移文件注释，本表不展开） |
| V85 | 084 | role_permission：新增读码 `custom_object:read` 并授予 ADMIN/ANALYST（数据迁移，无 DDL） |
| V86 | 084 | role_permission：多币种读写分码——新增 `currency:read`、复用既有 `currency:manage`，CurrencyRateController 改按码放行（数据迁移，无 DDL） |
| V87 | 1.5 批 3 | 权限矩阵对齐批 3：九个控制器的权限接线（`role_permission` 补授，数据迁移，无 DDL） |
| V88 | 083 | 三张 quota 子表补 `BaseEntity` 公共列（`updated_at`/`deleted`/`version`），修 078 的 `PUT /{id}` 与 `POST /{id}/breakdown` 在生产库上 500（加列，无新表） |

## 使用建议

- **新功能开发**：遵循 `/speckit-specify → /speckit-plan → /speckit-tasks → /speckit-implement → /speckit-converge` 全流程，产物落在新目录 `specs/<NNN>-<name>/`。
- **查阅某模块**：从上方模块表进入，按 spec（业务）→ plan（技术）→ contracts（接口）→ tasks（实施记录）顺序阅读。
- **改动已应用迁移**：禁止编辑已应用 migration（Flyway checksum），需新增迁移号 V78+ 并同步 `backend/src/test/resources/schema-h2.sql`。
