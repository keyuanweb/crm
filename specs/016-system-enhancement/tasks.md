# Tasks: 系统增强模块

**Input**: Design documents from `/specs/016-system-enhancement/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V38 in `backend/src/main/resources/db/migration/V38__custom_field.sql`（custom_field 表 + uk_field_entity_name/idx_field_entity_enabled）
- [x] T002 [P] 创建 Flyway 迁移 V39 in `backend/src/main/resources/db/migration/V39__custom_field_value.sql`（custom_field_value 表 + uk_field_entity_value/idx_value_entity）
- [x] T003 [P] 创建 Flyway 迁移 V40 in `backend/src/main/resources/db/migration/V40__export_job.sql`（export_job 表 + idx_export_user）
- [x] T004 [P] 创建 Flyway 迁移 V41 in `backend/src/main/resources/db/migration/V41__notification.sql`（notification 表 + 从 workflow_notification 迁移数据 + 索引）
- [x] T005 [P] H2 测试 schema 同步（custom_field/custom_field_value/export_job/notification 表 + 索引）in `backend/src/test/resources/schema-h2.sql`
- [x] T006 [P] ErrorCode 新增 CUSTOM_FIELD_*/NOTIFICATION_*/EXPORT_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [x] T007 [P] 创建 CustomField/CustomFieldValue/ExportJob/Notification 实体 + 4 个 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 自定义字段 (P0) 🎯 MVP

**Goal**: 字段定义 CRUD + 实体值读写 + 列表筛选。

**Independent Test**: 配置字段→实体携带值→筛选命中。

### 实现

- [x] T008 [P] [US1] 创建 CustomFieldRequest/Response/CustomFieldValueRequest/CustomFieldValueResponse DTO in `backend/src/main/java/com/crm/dto/customfield/`
- [x] T009 [US1] 创建 CustomFieldService in `backend/src/main/java/com/crm/service/CustomFieldService.java`（定义 CRUD/名称唯一/类型-选项校验/删除清理值/按实体列定义）
- [x] T010 [US1] 创建 CustomFieldController in `backend/src/main/java/com/crm/controller/CustomFieldController.java`（/custom-fields CRUD；仅 ADMIN）
- [x] T011 [US1] Lead/Customer/Opportunity/Ticket 请求与响应接入 customFieldValues 读写（保存时 upsert、详情回显）in `backend/src/main/java/com/crm/service/`（LeadService/CustomerService/OpportunityService/TicketService + 对应 Request/Response DTO）
- [x] T012 [US1] 列表筛选支持 `cf_<fieldId>` 参数（文本 LIKE/下拉 EQ）in `backend/src/main/java/com/crm/service/`（LeadService/CustomerService/OpportunityService/TicketService）
- [x] T013 [US1] 创建 CustomFieldServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CustomFieldServiceTest.java`（定义 CRUD/重复 409/类型校验/必填 422）
- [x] T014 [US1] 创建 SystemEnhancementIT 集成测试（字段部分）in `backend/src/test/java/com/crm/integration/SystemEnhancementIT.java`
- [x] T015 [US1] 前端类型/服务 in `frontend/src/types/customField.ts` + `frontend/src/services/customFieldService.ts`
- [x] T016 [US1] 自定义字段配置页 in `frontend/src/pages/settings/CustomFieldListPage.tsx`（按实体 CRUD；仅 ADMIN 菜单）
- [x] T017 [US1] 线索/客户/商机/工单创建弹窗与详情接入自定义字段 in `frontend/src/pages/`（LeadListPage/CustomerListPage/OpportunityListPage/TicketListPage + 详情页）

**Checkpoint**: US1 可用——自定义字段

---

## Phase 3: 用户故事 2 - 通知中心 (P1)

**Goal**: 通知列表/已读/未读计数 + 工单通知接入。

**Independent Test**: 分配工单→通知出现→标记已读→角标更新。

### 实现

- [x] T018 [P] [US2] 创建 NotificationResponse/NotificationType DTO in `backend/src/main/java/com/crm/dto/notification/`
- [x] T019 [US2] 创建 NotificationService in `backend/src/main/java/com/crm/service/NotificationService.java`（列表未读优先/单条已读/全部已读/未读计数/写入通知/保留 100 条清理）
- [x] T020 [US2] 创建 NotificationController in `backend/src/main/java/com/crm/controller/NotificationController.java`（/notifications 列表/unread-count/read/read-all；仅本人）
- [x] T021 [US2] TicketService 分配/回复时写入通知（TICKET_ASSIGN/TICKET_REPLY，本人除外）in `backend/src/main/java/com/crm/service/TicketService.java`
- [x] T022 [US2] 删除/替换 WorkflowNotification 引用到 Notification in `backend/src/main/java/com/crm/service/`（WorkflowEngine 写入点改造）
- [x] T023 [US2] 创建 NotificationServiceTest 单元测试 in `backend/src/test/java/com/crm/service/NotificationServiceTest.java`（列表/已读/未读计数/清理）
- [x] T024 [US2] SystemEnhancementIT 增加通知用例 in `backend/src/test/java/com/crm/integration/SystemEnhancementIT.java`
- [x] T025 [US2] 前端类型/服务 in `frontend/src/types/notification.ts` + `frontend/src/services/notificationService.ts`
- [x] T026 [US2] 顶栏通知角标 + 通知抽屉（列表/已读/全部已读）in `frontend/src/components/NotificationCenter.tsx` + `frontend/src/App.tsx`

**Checkpoint**: US2 可用——通知中心

---

## Phase 4: 用户故事 3 - 数据导出 (P1)

**Goal**: 导出任务异步生成 Excel + 历史与下载。

**Independent Test**: 发起导出→任务 DONE→下载行数一致。

### 实现

- [x] T027 [P] [US3] 创建 ExportRequest/ExportJobResponse DTO in `backend/src/main/java/com/crm/dto/export/`
- [x] T028 [US3] 创建 ExportJobService in `backend/src/main/java/com/crm/service/ExportJobService.java`（创建任务/状态查询/下载鉴权/保留 50 条清理/线程池执行）
- [x] T029 [US3] 创建 ExportExecutor in `backend/src/main/java/com/crm/service/ExportExecutor.java`（POI 生成 xlsx，按类型查询数据 + 自定义字段列）
- [x] T030 [US3] 创建 ExportController in `backend/src/main/java/com/crm/controller/ExportController.java`（POST /exports、GET 列表、GET /{id}/download）
- [x] T031 [US3] 创建 ExportJobServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ExportJobServiceTest.java`（创建/状态/下载鉴权/清理）
- [x] T032 [US3] SystemEnhancementIT 增加导出用例 in `backend/src/test/java/com/crm/integration/SystemEnhancementIT.java`
- [x] T033 [US3] 前端类型/服务 in `frontend/src/types/export.ts` + `frontend/src/services/exportService.ts`
- [x] T034 [US3] 导出中心页 in `frontend/src/pages/exports/ExportCenterPage.tsx`（发起导出/历史/下载）+ 各列表页导出按钮

**Checkpoint**: US3 可用——数据导出

---

## Phase 5: 收尾与验证

- [x] T035 前端路由与菜单 in `frontend/src/App.tsx`（设置-自定义字段仅 ADMIN、导出中心、通知角标）
- [x] T036 移动端响应式优化（窄屏表单/列表布局）in `frontend/src/App.tsx` + `frontend/src/`（antd responsive）
- [x] T037 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T038 单独运行 `mvn test "-Dtest=SystemEnhancementIT"` 通过
- [x] T039 Frontend typecheck / lint / test / build 通过
- [x] T040 线上端点验证（字段配置→值读写→筛选→通知→导出下载→权限 403）
- [x] T041 更新契约文档（按实现校正）与 roadmap 016 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖，可并行
- **Phase 2 (US1)**: 依赖 Phase 1；值读写需接入 4 个既有 Service
- **Phase 3 (US2)**: 依赖 Phase 1；通知写入需改造 TicketService/WorkflowEngine
- **Phase 4 (US3)**: 依赖 Phase 1；导出含自定义字段列（依赖 US1 的字段定义）
- **Phase 5**: 依赖全部用户故事完成

### Parallel Opportunities

- Phase 1 的 T001~T007 全部 [P] 可并行
- US1 字段定义服务与 US2/US3 独立实体任务可并行
- 同一故事内 DTO/测试（[P]）可并行

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 数据模型
2. 完成 Phase 2: US1 自定义字段（定义 CRUD + 值读写 + 筛选）
3. **STOP and VALIDATE**: 字段配置/填写/筛选可独立使用
4. 继续 US2 通知中心、US3 数据导出

### 增量交付

1. Phase 1 → 数据模型就绪
2. US1 自定义字段 → 独立验证（MVP）
3. US2 通知中心 → 独立验证
4. US3 数据导出 → 独立验证
5. Phase 5 收尾：移动端响应式/路由/菜单/全量验证/文档

## Notes

- 自定义字段值字符串存储；必填校验在 Service 层按字段定义执行
- 通知统一表迁移 013 workflow_notification 数据（V41 含 INSERT SELECT）
- 导出任务同 JVM 线程池异步执行；文件落盘 backend/contract-files/exports/
- 提交规范：每个逻辑组提交一次（Conventional Commits）

---

## Phase 6: Convergence

**来源**: /speckit-converge 评估（016-system-enhancement，2026-08-22）

- [x] T042 实现列表按自定义字段筛选（`cf_<fieldId>` 参数：文本 LIKE / 下拉 EQ，join custom_field_value）于 LeadService/CustomerService/OpportunityService/TicketService 列表查询，并在前端列表页（线索/客户/商机/工单）接入自定义字段筛选控件 per FR-S03 / US1-AC3 / SC-S02 (missing)
- [x] T043 更名或补充类注释说明 `WorkflowNotificationService`（实际写入统一 notification 表，type=WORKFLOW，命名易误导）per 016 plan: 统一 notification 表 (unrequested)

---

## Phase 7: 系统布局增强

- [x] T044 前端：`frontend/src/App.tsx` 左侧菜单分组由 `type:'group'` 改为 `type:'submenu'`（带 key），点击分组标签可收起/展开；所有分组默认收起；「首页」置顶为首位独立菜单项；首页默认路由指向 `/stats`；移动端横向模式仍拍平为普通项 per FR-S14 (missing)

---

## Phase 8: 界面密度与交互反馈

- [x] T045 前端：表格统一 `size="small"`（21 个 ProTable + 详情/统计 Table 显式设置）；表单输入控件恢复默认尺寸（移除 ConfigProvider componentSize 全局小号）per FR-S15 (missing)
- [x] T046 前端：为异步操作补齐 loading——DashboardPage「刷新」按钮（isFetching）、各列表页弹窗保存 confirmLoading、客户页导入/导出/公海扫描/转移按钮 loading per FR-S16 (missing)
- [x] T047 前端：表单布局统一——确认全部弹窗表单为垂直布局、标签与字段宽度一致、按钮右对齐（antd 默认），Modal 宽度统一 per FR-S17 (missing)
- [x] T048 前端：首页（/stats 统计仪表盘）卡片等高校对——销售漏斗 Card 设 `height:100%` 与同行列等高，消除卡片下方与下一行之间的空白；表格类卡片 body padding 置 0 以紧凑展示（注：最近跟进的 height:100% 后由 T049 取消改为自然高度） per FR-S18 (missing)
- [x] T049 前端：首页（/stats 统计仪表盘）对称双列布局——第一行 销售漏斗 | 销售预测+业绩达成，第二行 客户分析+跟进活动 | 最近跟进+停滞商机预警；各卡片占页面 50% 宽度（lg=12）；停滞商机预警由全宽改为右列 50% 宽、置于最近跟进下方；最近跟进取消 `height:100%` 拉伸改为自然高度，避免卡片内部空白 per FR-S18 (missing)
- [x] T050 前端：首页间距统一优化——各业务行 `Row` 加 `marginBottom: 12`，生成时间段落 `marginTop: 12`，消除 antd Row vertical gutter 负 margin 导致的行间 gap=0（业绩达成紧贴客户分析）问题；全页垂直间距统一 12px per FR-S18 (missing)
- [x] T051 前端：首页销售漏斗可视化——将纯 Table 改为可视化漏斗视图（每阶段渐变进度条按金额占比 + 阶段标签 + 数量/金额/转化率/占比），body padding 16；移除未使用的 funnelColumns 与 FunnelStageStat import per FR-S18 (missing)
- [x] T052 前端：全站列表页弹窗表单 2 列对齐——OpportunityListPage/SalesOpportunityListPage/OrderListPage/QuoteListPage/TicketListPage/ContractListPage/TaskListPage/CampaignListPage 共 8 页弹窗 Form 由 `layout="vertical"` 改为 `layout="horizontal"` + `labelCol={{flex:'100px'}}` + `wrapperCol={{flex:1}}` + `<Row gutter={16}><Col span={12}>` 两列并排（长文本/明细表格/自定义字段保持 `span={24}` 全宽）；移除无效 `style={{flex:1}}` 残留与混乱缩进；Modal 宽度统一 640~760 per FR-S19 (missing)
- [x] T053 前端：全站列表页 ProTable 卡片圆角统一——为 15 个缺失圆角的列表页 ProTable 加 `cardProps={{ style: { borderRadius: 10 } }}`（Contract/Contact/KnowledgeArticle/Lead/ContractTemplate/Campaign/Order/Product/Quote/WorkflowRule/CustomField/WorkflowLog/SlaPolicy/Ticket/Task），与既有 Customer/Opportunity/SalesOpportunity/AuditLog/UserManagement/Department 一致，全站列表页圆角统一 10px per FR-S19 (missing)
- [x] T054 前端：main.tsx 引入 `./index.css`（此前未引入，`html,body{margin:0}` 未生效导致 body 8px 默认 margin 使根 Layout 下移 8px、总高 1008 溢出 1000 出现滚动条）；修复后后台无默认滚动条 per FR-S19 (missing)
- [x] T055 前端：LeadListPage 操作列宽度 240→300，修复"编辑+分配给我+转化+删除"4 操作换行 per FR-S19 (missing)
- [x] T056 前端测试：新增 DashboardPage.test.tsx（4 用例：漏斗可视化渲染/空数据 Empty/错误 Result/停滞预警卡片）+ OrderListPage.render.test.tsx（渲染冒烟），前端测试 14→19 per 章程原则四（测试优先） (missing)
- [x] T057 后端测试：新增 CustomFieldFilterSupportTest（8 用例：cf_ 参数解析/ SELECT 精确匹配/TEXT LIKE/多条件交集/字段跳过/短路空）；修复 ContractServiceTest+QuoteServiceTest 3 处硬编码日期编号（20260822）导致的跨天 flaky 失败（改为 LocalDate.now() 动态期望），后端测试 190→198 per 章程原则四 (missing)
- [x] T058 前端：全站详情页 UI 统一优化（6 页：Customer/Lead/Ticket/Order/Quote/Contract）——① TicketDetailPage/LeadDetailPage 加载态由 Spin 改为 Card loading、错误态统一 Result 404 + 返回按钮；② 返回按钮统一 `Link + Button type="link"` 风格 + 页面标题行（Title+描述+操作按钮）模板；③ Descriptions 统一 bordered + size="small" + column=2（Lead/Ticket 原 column=3 改 2）；④ TicketDetailPage Descriptions/时间线用 Card 包裹（borderRadius 10 + marginBottom 16 + styles.header）；⑤ 全站详情页 deprecated bodyStyle/headStyle 改 styles.body/styles.header per FR-S17（界面整齐一致） (missing)
- [x] T059 前端：全站面包屑导航——新增 `BreadcrumbNav` 组件（首页/分组/页面/详情四级），基于菜单分组路由表（客户管理/销售管理/交易管理/基础资料/营销与服务/数据分析/系统管理）匹配当前路径生成；首页(/stats)仅显示"首页"；详情页追加"详情"层级；中间项（首页/列表页）可点击跳转；挂载于 Content 顶部（Outlet 前） per FR-S14（导航增强） (missing)
- [x] T060 后端性能修复：Redis 缓存序列化——① `RedisConfig` 的 GenericJackson2JsonRedisSerializer 注入带 JavaTimeModule 的 ObjectMapper（原默认无 jsr310 模块导致 DashboardStats 等含 LocalDateTime 对象缓存写入失败、每次全量重算；修复后首页第二次请求 25ms→0ms 缓存命中）；② `UserStateCache` 改用专用 StringRedisTemplate + ObjectMapper JSON 字符串存储（与全局对象序列化器解耦，避免类型包装 WRAPPER_ARRAY 格式脆弱性与旧数据不兼容），消除每次 JWT 请求的缓存读取降级 per FR-S19（性能优化） (missing)
- [x] T061 后端性能优化：KPI 大屏健康度分布 N+1→批量——`Customer360Service` 新增 `healthLevelsBatch(List<Long>)`（订单/回款计划/回款记录/工单/跟进按 IN 一次查询 + 内存分组装配，健康配置一次加载，按客户计算 HealthInput 并评分，返回 customerId→level）；`KpiBoardService.computeHealthDistribution` 改调批量方法（原 200 客户逐个 aggregate≈1400 次查询 → 6 次 IN 查询）；新增 Customer360ServiceTest（3 用例：空输入不查库/多客户批量/无关联数据兜底），后端测试 243→246；实测 kpi-board 首次 184ms（含缓存 miss 全量聚合）、缓存命中 13ms per FR-S19（性能优化） (missing)
- [x] T062 后端可观测性：接入 Spring Boot Actuator——① pom 加 spring-boot-starter-actuator；② application.yml 暴露 health/info/metrics 端点 + 启用 liveness/readiness 探针（show-details when-authorized，匿名只见总体状态）；③ SecurityConfig 放行 /actuator/health（匿名可读，info/metrics 需认证）；实测 /actuator/health 匿名 200 UP（含 liveness/readiness 探针）、metrics/info 认证后可读 per FR-S19（可观测性基础，后续可接 Prometheus） (missing)
- [x] T063 前端测试补强：新增 CustomerListPage.test.tsx 交互测试（2 用例：ProTable 列表渲染加载数据、点击"新增客户"打开弹窗→填写必填字段→提交调用 createCustomer 并断言 payload），前端测试 30→32 per 章程原则四（测试优先） (missing)
- [x] T064 后端安全加固：登录 IP 维度限流——`AuthService` 新增 IP 失败计数器（Redis `auth:ip-fail:<ip>`，失败 10 次锁 15 分钟，与既有用户名 5 次锁定构成双防线）；`AuthController.login` 接收 HttpServletRequest 经 `AuthService.resolveClientIp`（X-Forwarded-For 首个地址优先，回退 remoteAddr）提取客户端 IP 传入；登录成功清 IP 计数；新增 AuthServiceTest（6 用例：IP 锁定/未达阈值记失败/用户名锁定回归/IP 解析优先与回退/成功清计数），后端测试 246→252；冒烟实测：不同用户名+同一 IP 前 10 次放行、第 11 次"登录尝试过于频繁" per FR-H（安全） (missing)
- [x] T065 前端表单优化：工作流规则编辑表单——① layout vertical→horizontal（labelCol 110px，与全站 FR-S17 统一）；② "目标用户 ID"手输数字改用户选择器（fetchUsers 加载 displayName 下拉，showSearch）；③ 弹窗宽度 620→640（统一档位）；④ 条件字段 compact Input.Group 改 Row/Col 2 列对齐；⑤ 动作类型切换时 setFieldsValue 清空其他动作参数残留（ASSIGN→NOTIFY 切换不再残留 targetUserId）；⑥ 字段加 placeholder/extra 提示；⑦ 按用户反馈修复"截止天数"布局：Col span=12（半行留白）→span=24（独占一行全宽，与标题模板等宽 592px）；typecheck/lint 通过，冒烟实测：表单标签顺序正确、切"站内通知"显示通知内容且隐藏目标用户、截止天数全宽无留白 per FR-S17（界面一致） (missing)
- [x] T066 前端 UI 修复：用户管理操作列宽度 200→280——5 项中文操作（编辑/数据权限/重置密码/启用停用）在 200px 下密集拥挤，按 LeadListPage 4 操作 300px 的先例提升至 280；typecheck/lint 通过，冒烟实测：操作列宽 280、singleLine=true（操作不换行） per FR-S19（视觉统一） (missing)
- [x] T067 前端首页重新设计（DashboardPage）：基于 UI 设计经验重构布局与样式——① 页面头部：加个性化欢迎语（"你好，{用户名} 👋"）+ 中文日期星期副标题；② KPI 卡：4 张渐变底色卡改白底 + 彩色渐变圆角图标容器（52px 图标卡 + 阴影），统一品牌感、可读性提升；③ AI 建议条：朴素横条改琥珀渐变浅底 + 渐变灯泡图标卡 + 4 统计点；④ 卡片统一 borderRadius 12 + 轻投影 boxShadow + 间距 16；⑤ 左右上下对齐：中间区右列两卡（预测/业绩）与底部区两列各两卡包 flex column + flex:1 等高（gap 16 替代 marginBottom）；⑥ 按用户反馈修复空态不对齐：停滞预警/最近跟进空态由矮表格（78px 留白）改为居中 Empty 填满卡体（flex body + 居中容器），与有数据卡等高对齐；⑦ 按用户反馈修复表格溢出：最近跟进/停滞预警有数据时包 `flex:1 + minHeight:0 + overflow:auto` 滚动容器（原 body overflow visible，跟进记录多时表格溢出卡片底部），实测 table bottom = body bottom 不溢出、滚动容器撑满 body 161px；实测四卡等高、左右列逐行 top/bottom 完全对齐；全部卡片 title 文本保留（DashboardPage 4 测试不破坏），typecheck/lint/32 tests 通过 per FR-S18/FR-S19（首页布局与视觉统一） (missing)
- [x] T068 前端 UI 修复：首页"最近跟进"表格跟进人列宽 80→120——"系统管理员"等 4 字中文在 80px 下显示拥挤/截断；实测表头 120px、"系统管理员"完整显示不截断、5 列宽 70/144/110/120/150 正常 per FR-S19（视觉统一） (missing)
