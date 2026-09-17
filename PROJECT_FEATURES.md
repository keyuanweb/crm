# CRM 项目功能全景整理

> 核对基准：**当前工作区代码**（`backend/`、`frontend/`、`specs/`），非既有文档描述。
> 与 README / specs/README.md 不一致之处见「八、文档与代码的偏差」。
> **规模数字于 2026-09-16 按下表「依据」列的口径重新实测**（上一版为 2026-09-15，此后 082/093/094/095 等批次落地，多行已滞后）。

## 一、规模速览

| 维度 | 实测值（2026-09-16，**含 096 与 097**——同日的两次交付与本次重测；⚠️ **同日第三次重测（由 099 执行）**见下方 ⚠️ 段，改变了 5 行） | 依据 |
|---|---|---|
| 后端 REST Controller | **66** | `backend/src/main/java/**/*Controller.java` |
| 数据库表 | **86** | `db/migration/*.sql` 中 `CREATE TABLE` 去重（**注意用 `src/main/resources`，`target/classes` 会让计数翻倍**） |
| Flyway 迁移 | **89 个（V1–V90，缺 V72）** | `backend/src/main/resources/db/migration` |
| 后端测试类 | **188 个含用例的类**（`src/test` 共 194 个 `.java`）⚠️ **2026-09-17 第四次重测（由 100 执行）：182/188 → 188/194**（+6 = 100 新增的 5 个单测类 + `RateLimitIT`）；**旧值逐字在本行内仍可 grep 到**（见下方 ⚠️ 段） | `grep -rlE "@Test\|@ParameterizedTest" backend/src/test/java` |
| 前端页面组件 | **100 个非测试 tsx**（含测试共 176） | `frontend/src/pages` 下 `*.tsx` 且非 `*.test.tsx` |
| 前端路由定义 | **87 个 `<Route>`** | `frontend/src/App.tsx` 的 `<Route` 88 处 − `<Routes>` 1 处 |
| 前端 service | **56 个 `*Service.ts`**（+`apiClient.ts`；`services/` 非测试 `.ts` 共 60） | `frontend/src/services` |
| 前端单测 / E2E | **92 / 7** | `*.test.ts(x)`、`frontend/e2e/*.spec.ts` |
| i18n 资源 | zh-CN 3453 行 / en 3426 行 | `frontend/src/i18n`（⚠️ **行数不等 ≠ 键集不等**：现值相差 **27** 行，根因已查明 —— **098 只在 `zh-CN.ts` 一侧写了说明注释**：098 对本表两文件是**纯新增、删除 0 行**，`zh-CN.ts` **+60 行（其中 30 行是注释）**、`en.ts` **+31 行（注释 0 行）**（`git diff --numstat 7356eb8 HEAD -- src/i18n/*.ts`）。**键集双向一致性由 `pnpm i18n:check` 判定**，不以行数为准；098 交付实测 **2963 / 2963 键**。⚠️ **2026-09-16 第三次重测（由 099 执行）**：099 删掉配额创建页后**两侧各减 1 行**（死键 `pages.quotaCreate.btnBack`），故 **3453/3426 → 3452/3425**、键 **2963/2963 → 2962/2962**（`git diff --numstat 63ea0e7 HEAD -- src/i18n/*.ts` 两文件均 `0 1`）；**行数差仍是 27**（099 是两侧同删，不改变那个差）。⚠️ **2026-09-17（由 101 执行）**：101 收信侧诚实化前端收口——删死键 `pages.mail.btnSimulateSync`（-1 键，两侧各 -1 行）、加 `pages.mail.btnSyncInbox` 与 `pages.mail.tagSimulated`（+2 键，两侧各 +1 行）⇒ 净 **+1 键 / +1 行**，故 **3452/3425 → 3453/3426**、键 **2962/2962 → 2963/2963**（`pnpm i18n:check` 实测 **2963/2963**；`wc -l frontend/src/i18n/*.ts` 实测 **3453 / 3426**）；**行数差仍是 27**（两侧同增同减，不改变那个差）。**旧值逐字在上句与本行内仍可 grep 到**） |
| Spec 模块 | **99 个（001–100，缺 069）**⚠️ **2026-09-17（由 100 执行）：98（001–099）→ 99（001–100，缺 069）**；**旧值逐字在本行内仍可 grep 到** | `specs/NNN-*` |

> ⚠️ **本次重测相对上一版的变动（逐行列出，不静默改数）**：数据库表 **85 → 86**（+`user_recovery_code`，082）；
> Flyway **87（V1–V88）→ 88（V1–V89）**（+`V89__two_factor_auth.sql`，082）；
> 后端测试类 **163/167 → 181/187**；前端页面组件（含测试）**170 → 174**；
> service **55/59 → 56/60**；前端单测 **83 → 89**（+6：082 三件、095 两件、093 一件）；
> i18n **3348/3348 → 3393/3395**；Spec 模块 **91（001–092）→ 94（001–095）**。
> **未变的三行**：Controller 66、前端路由 88、E2E 7。
> 口径说明：本表**只报实测**、不追认变动归属；上表「依据」列即为复现命令。
> 数据库表一行的读法**经自证**——`grep -hioE 'create table( if not exists)? +\`?[a-zA-Z_0-9]+\`?'`
> 去重后 **86**，且每个表名**恰好出现 1 次**（无重复计数），其中 `user_recovery_code` 见于 `V89`。

> ⚠️ **2026-09-16 第二次重测（由 097 执行；上表已按本次读数改）——为什么改的是 5 行、不是 4 行**
>
> 上表表头写的是 2026-09-16，但**096 也在同一天交付**（含一条迁移 `V90`、两个新前端用例文件、一个
> 模块目录），它的读数**没有进上表**；097 自己又加了一个后端测试类与一个模块目录。故按**上表自己的
> 「依据」口径**逐行重测，**改的是 5 行不是 4 行**（097 的 `spec.md` FR-008 计划的是 4 行——**计划值
> 与实际读数差 1，此处以实测为准并留痕**）：
>
> | 行 | 旧值（逐字见下方留痕） | 新值 | 变动来源 |
> |---|---|---|---|
> | Flyway 迁移 | 88（V1–V89） | **89（V1–V90）** | 096 的 `V90__…`（纯授权、无 DDL） |
> | Spec 模块 | 94（001–095） | **96（001–097）** | 096 的目录 + **097 自己的目录**（见下） |
> | 前端单测 | 89 | **91** | 096 的两个新用例文件 |
> | 页面组件括注 | 含测试共 174 | **含测试共 176** | 同上（两个 `.test.tsx` 落在 `pages/` 下） |
> | 后端测试类 | 181 / 187 | **182 / 188** | **097 自己**的 `UnwiredPermissionCodeTest.java` |
>
> **未变的 5 行**：Controller 66、数据库表 86（`V90` 纯授权、不建表）、前端路由 88（`<Route` 89 处 −`<Routes>` 1 处）、
> service 56/60、i18n 3393/3395。（**E2E 的 7 也没变**——但它是「前端单测 / E2E」这一行的**后半格**，
> 而该行的前半格 **89 → 91** 变了，故**整行算已改**，不重复计入未变的 5 行。）
>
> **一处要说清的口径**：「Spec 模块」数的是 `specs/NNN-*` 的**目录数**，**097 自己也在这个数里**
> （它不给自己豁免）——所以是 **96（001–097，缺 069）**，而不是 95。95 是 `097/research.md` §5.2 的读数，
> **取于 097 目录建立之前**；两者不矛盾，但**引用时以本节为准**。
>
> **旧值逐字留痕（仍在上一段与本节里 grep 得到，不是静默改写）**：
> `**88 个（V1–V89，缺 V72）**`、`**94 个（001–095，缺 069）**`、`**89 / 7**`、
> `**101 个非测试 tsx**（含测试共 174）`、``**181 个含用例的类**（`src/test` 共 187 个 `.java`）``。
>
> **这一节还是「迁移计数：一个数字住在 6 个地方」的第 6 处**：096 交付时改了 5 处
> （`specs/README.md` 标题与版本行、`INSTALL.md` ×2、根 `README.md` 的目录树），**漏了本节**。
> 补上后六处一致（现值**均为 V1~V90 / 89 个**）。
>
> 复算命令即上表「依据」列，逐行可跑；迁移数：`ls backend/src/main/resources/db/migration/*.sql | wc -l` → **89**。

> ⚠️ **2026-09-16 第三次重测（由 099 执行；上表已按本次读数改）——为什么改的是 5 行**
>
> 099 是**纯前端**批次：把「创建配额」从独立路由页 `/quotas/create`（`QuotaCreatePage.tsx`，126 行）
> 改成 `/quotas` 列表页内的 `FormModal` 弹窗（`lg` 档 800px），**同批删除**旧页面与旧路由。
> 无迁移、无端点、无 DTO、无权限码、**`backend/` 一个文件都没动**（`git diff --stat 63ea0e7 HEAD`
> 里没有 `backend/` 路径）⇒ 上表只有前端那 5 行会动，按**上表自己的「依据」列**逐行复测：
>
> | 行 | 旧值（逐字见下方留痕） | 新值 | 变动来源 |
> |---|---|---|---|
> | 前端页面组件 | 101 个非测试 tsx | **100** | 删掉 `src/pages/quotas/QuotaCreatePage.tsx`；同批新增的 `QuotaListPage.form.test.tsx` **也是 `pages/` 下的 tsx**，故「含测试共」**仍是 176**（一进一出） |
> | 前端路由定义 | 88 个 `<Route>`（`<Route` 89 处 − 1） | **87 个**（`<Route` 88 处 − 1） | 删掉 `<Route path="quotas/create" .../>` 那一行（`App.tsx` 本批 `2 deletions` = lazy import 行 + 这条路由） |
> | 前端单测 / E2E | 91 / 7 | **92 / 7** | 099 的 `QuotaListPage.form.test.tsx`（本项唯一的行为层证据） |
> | i18n 资源 | zh-CN 3453 行 / en 3426 行、键 2963 | **3452 / 3425 行、键 2962** | 删死键 `pages.quotaCreate.btnBack`，**两侧各减 1 行**（`git diff --numstat 63ea0e7 HEAD -- src/i18n/*.ts` 均为 `0 1`）；**行数差仍是 27** |
> | Spec 模块 | 97（001–098） | **98（001–099）** | 099 自己的目录（**它不给自己豁免**，同 097 的处置） |
>
> **未变的 5 行**：Controller 66、数据库表 86、Flyway 89（V1–V90）、后端测试类 182/188、service 56/60。
> （**E2E 的 7 也没变**——但它是该行的后半格，前半格 91 → 92 变了，故整行算已改，不重复计入未变的 5 行；
> 这与 097 那次「改 5 行 / 未变 5 行」的分法同一口径，只是这次变的是另外 5 行。）
>
> **两处口径要说清**：① 「前端路由定义」是**本文件自己的**计数（数 `App.tsx` 里的 `<Route`），
> 与 `pnpm i18n:check` 打印的「路由 **58** 条」**不是一个东西**——后者要求**单引号且同行有 `name:`**，
> 而删掉的那条是**双引号、无 `name:`**，所以 **58 不动**（099 的 `research.md` §3 据此逐门禁核过）。
> 同一个「删了一条路由」在两处一个变、一个不变，**不是矛盾，是两把尺子**。
> ② 「前端页面组件」的**非测试**数减 1、**含测试共**不变——两个数分别对应上表「依据」列的两半，
> 只看其中一个会以为另一个也该动。
>
> **旧值逐字留痕（仍可在本段与本文件表头 grep 到，不是静默改写）**：
> `**101 个非测试 tsx**（含测试共 176）`、`**88 个 <Route>**`、`**91 / 7**`、
> `zh-CN 3453 行 / en 3426 行`、`**97 个（001–098，缺 069）**`、`2963 / 2963 键`。
>
> 复算命令即上表「依据」列，逐行可跑：`find frontend/src/pages -name '*.tsx' ! -name '*.test.tsx' | wc -l` → **100**；
> `grep -c '<Route' frontend/src/App.tsx` → **88**（再减 `<Routes>` 1 处）；`find frontend/src -name '*.test.ts*' | wc -l` → **92**；
> `ls -d specs/[0-9]* | wc -l` → **98**。

> ⚠️ **2026-09-17 第四次重测（由 100 = 100-rate-limit-consolidation 执行）——本次只动 2 行**
>
> **本项零前端改动**（`frontend/` 一个文件不动，见 100 的 SC-100-010）⇒ 与 099 那次相反，
> **这次动的是后端两行**，前端 5 行与其余后端行**逐字未变**。按上表「依据」列逐行复测：
>
> | 行 | 旧值（逐字见下方留痕） | 新值 | 变动来源 |
> |---|---|---|---|
> | 后端测试类 | 182 含用例 / 188 个 `.java` | **188 / 194** | 100 新增 **5 个单测类**（`RateLimitStoreTest` · `RateLimiterShapeTest` · `RateLimitIdentityTest` · `ClientIpResolverTest` · `RateLimitCoverageTest`）+ **1 个 IT**（`RateLimitIT`） |
> | Spec 模块 | 98（001–099） | **99（001–100，缺 069）** | 100 自己的目录（**同 097/099 的处置：它不给自己豁免**） |
>
> **未变的行（逐条点名，不写「若干行」）**：Controller **66**、数据库表 **86**、Flyway **89（V1–V90，缺 V72）**、
> 前端页面组件 **100（含测试共 176）**、前端路由定义 **87**、前端 service **56（+`apiClient.ts`，非测试 60）**、
> 前端单测/E2E **92 / 7**、i18n **zh-CN 3452 行 / en 3425 行（键 2962/2962，行数差 27）**。
> 复算命令即上表「依据」列；本次实跑读数：`grep -rlE "@Test|@ParameterizedTest" backend/src/test/java | wc -l` → **188**；
> `find backend/src/test/java -name '*.java' | wc -l` → **194**；`find backend/src/main/java -name '*Controller.java' | wc -l` → **66**；
> `ls -d specs/[0-9]* | wc -l` → **99**；`find frontend/src -name '*.test.ts*' | wc -l` → **92**。
>
> ⚠️ **口径一处要说清（本表自己的计数 vs 命令的计数）**：「后端测试类」的两半是**两把尺子** ——
> 前半（含用例的类）数的是**文件里出现过 `@Test`/`@ParameterizedTest`**，后半（`.java` 总数）数的是**文件数**；
> 本项两者**同增 6**（新增的 6 个文件每一个都带用例）⇒ 两半一起动。
> 若日后新增一个**没有用例**的测试类（纯 `@SpringBootTest` 基类、纯 fixture），**只有后半会动** —— 那时别把它读成漏改。
>
> **旧值逐字留痕（仍可在本段与本文件表头 grep 到，不是静默改写）**：
> `**182 个含用例的类**（`src/test` 共 188 个 `.java`）`、`**98 个（001–099，缺 069）**`、`| Spec 模块 | 98（001–099） |`。

## 二、技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.2、MyBatis-Plus 3.5、MySQL 8、Redis 7、Spring Security + JWT、Flyway、springdoc-openapi |
| 前端 | React 18、TypeScript、Vite 5、React Router、React Query、Zustand、Ant Design v5 + Pro Components、ECharts、G6 |
| 测试 | JUnit 5 / Spring Boot Test、Vitest + React Testing Library、Playwright |
| 构建部署 | Maven、pnpm、Docker Compose（frontend + backend + MySQL + Redis + Nginx） |

## 三、功能地图（左侧菜单：首页置顶 + 10 个业务分组）

### 0. 首页（置顶独立项）
| 菜单 | 路由 | 说明 |
|---|---|---|
| 首页/统计仪表盘 | `/stats` | KPI 指标卡、销售漏斗、业绩达成、销售预测、客户分析、跟进活动，内嵌「使用地图」入口 |

### 1. 客户管理（g-customer）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 线索 | `/leads`、`/leads/:id` | 线索池、分配、评分规则、转化客户/联系人/商机、Excel 导入导出 |
| 客户 | `/customers`、`/customers/:id` | CRUD（逻辑删除）、分页搜索筛选、查重、360 全景详情、公海池 |
| 联系人 | `/contacts` | 联系人 CRUD、角色、关联客户 |
| 查重合并 | `/customer-merge` | 重复识别 + 合并（`CustomerMergeController`） |
| 流失预警 | `/customers/at-risk` | 健康评分配置、风险客户清单 |

### 2. 销售管理（g-sales）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 商机 | `/opportunities` | 阶段管道、赢单/输单关闭、金额 |
| 销售机会 | `/sales-opportunities` | 子实体、阶段动作 |
| 报价单 | `/quotes`、`/quotes/:id` | CPQ、报价明细、版本、PDF、审批、电子签署 |
| 外勤拜访 | `/visits` | 拜访计划、签到、位置记录 |
| 产品 | `/products` | 产品目录、标准售价、多币种价格折算 |
| 销售 Playbook | `/playbook` | 阶段动作模板、必做项校验 |

### 3. 成交与回款（g-deal）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 合同 | `/contracts`、`/contracts/:id` | 合同 CRUD、审批、附件、签署记录 |
| 合同续约 | `/contract-renewal` | 到期提醒、续约链、续约漏斗 |
| 订单 | `/orders`、`/orders/:id` | 订单、分期回款计划、回款记录、应收账款 |
| 发票 | `/invoices` | 开票、状态跟踪 |

### 4. 营销管理（g-marketing）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 营销活动 | `/marketing` | Campaign 管理、归因；渠道 ROI 子页 `/marketing/roi` |
| 邮件营销 | `/marketing/email` | 邮件模板、群发、发送日志、打开/点击追踪、A-B 主题测试 |
| 邮件退订 | `/email-unsubscribes` | 退订名单管理、公开退订接口 |
| 在线表单 | `/online-forms` | 自定义表单；公开提交页 `/f/:id` |
| 落地页 | `/landing-pages` | 托管落地页 + UTM 归因；公开渲染页 `/lp/:id` |

### 5. 客户服务（g-service）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 客户服务 | `/tickets`、`/tickets/:id` | 工单池、回复、状态流转 |
| 知识库 | `/knowledge` | 文章分类、发布 |
| 公告管理 | `/announcements` | 公告发布、已读、评论 @提及 |
| 我的审批 | `/approvals` | 待我审批/我发起的、审批中心 |
| 客户门户 | `/portal` | 自助门户（知识库浏览、在线提单、进度查询，公开路由） |
| 满意度调查 | `/satisfaction` | CSAT / NPS 统计 |
| SLA 日历 | `/sla-calendar` | 工作时间、节假日配置 |

### 6. 工作台（g-workbench）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 任务 | `/tasks`、`/tasks/calendar` | 待办、日历视图、提醒、跟进计划 |
| 智能建议 | `/suggestions` | 规则型建议、停滞商机/流失预警 |
| 酷炫大屏 | `/data-vision` | 全屏数据大屏（独立路由，隐藏菜单/顶栏） |
| 通话记录 | `/call-records` | CTI 数据模型与记录 |
| 邮件同步 | `/mail-sync` | 邮件账户配置、同步记录框架 |

### 7. 数据分析（g-data）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 自定义报表 | `/reports` | 报表模板、多维聚合、报表中心 |
| 团队排行 | `/stats/leaderboard` | 销售业绩排行榜 |
| 导出中心 | `/exports` | 导出任务（Excel）；定时导出见「四」 |

### 8. 系统管理（g-admin）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 用户管理 | `/users` | 用户 CRUD、启停、密码重置、令牌失效、**重置双因素认证**（082） |
| 角色权限 | `/roles` | 角色-菜单-权限点（RBAC，含 081 更新） |
| 部门 | `/departments` | 组织架构、层级、排序 |
| 字段权限 | `/field-permissions` | 字段级隐藏/只读/可编辑 |
| 多币种 | `/currencies` | 汇率管理、自动折算 |

### 9. 流程与配置（g-config）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 工作流 | `/workflows`、`/workflows/logs` | 规则引擎、触发器、执行日志 |
| 审批流配置 | `/approval-flows` | 多级条件审批模板 |
| SLA 策略 | `/sla-policies` | 响应/解决时限、升级策略 |
| 合同模板 | `/contract-templates` | 模板管理、合同生成 |
| 自定义字段 | `/settings/custom-fields` | 动态字段、动态表单 |
| 自定义对象 | `/custom-objects`、`/custom-objects/:id/records` | 低代码元数据建模 |
| 开放平台 | `/open-platform` | API Key 管理、Webhook 事件订阅 |
| 集成中心 | `/integration-hub` | 第三方通知通道、事件推送 |

### 10. 审计与维护（g-audit）
| 菜单 | 路由 | 后端能力 |
|---|---|---|
| 标签与细分 | `/tags` | 标签、客户分群、动态细分 |
| 审计日志 | `/audit-logs` | 操作追踪、合规查询 |
| 回收站 | `/recycle-bin` | 软删恢复、彻底删除 |

## 四、不在左侧菜单的功能（页面内或直连入口）

| 功能 | 路由 | 入口方式 |
|---|---|---|
| 销售配额分解（078） | `/quotas`、`/quotas/:id/breakdown`、`/quotas/:id/achievement`、`/quotas/:id/versions`、`/quotas/comparison` | 仅路由可达；页面间互跳 |
| 定时导出订阅（079） | `/exports/scheduled`、`/exports/scheduled/create`、`/exports/scheduled/:id/executions` | 仅路由可达 |
| 数据保留策略（080） | `/data-retention`、`/data-retention/create`、`/data-retention/:id/executions` | 仅路由可达 |
| 合规导出（GDPR） | `/data-retention/compliance-export` | 页面内跳转 |
| 个人中心（含**双因素认证安全卡**，082） | `/personal-center` | 顶栏头像下拉菜单；卡内可绑定/关闭 TOTP、查看与重新生成恢复码 |
| 修改密码 | `/account/password` | 顶栏头像下拉菜单 |
| 使用地图 | `/usage-map` | 头像下拉 + 首页卡片 |
| 全局搜索 | `/search` | 顶栏渲染（`App.tsx:656`），回车进入结果页 |

## 五、免登录公开页面

| 页面 | 路由 |
|---|---|
| 登录（含图形验证码，可开关；启用 2FA 的账号在此切**二次验证视图**，可按动态码或恢复码验证，082） | `/login` |
| 在线表单提交 | `/f/:id` |
| 托管落地页 | `/lp/:id` |
| 客户自助门户 | `/portal` |
| 公开 API 域 | `/api/v1/public/portal`、`/api/v1/public/track`、`/api/v1/public/email` |

## 六、后端 API 域（66 个 Controller 归组）

- **认证与组织**：Auth、User、Role、Department、PersonalCenter、FieldPermission、CustomerShare
- **客户域**：Customer、CustomerPool、CustomerMerge、Contact、Lead、FollowUp、Tag、Segment、Comment
- **销售域**：Opportunity、SalesOpportunity、Product、ProductPrice、Quote、Playbook、SalesQuota、CurrencyRate
- **成交域**：Contract、ContractAttachment、ContractTemplate、ContractRenewal、Signature、Order、Invoice
- **营销域**：Marketing、Email、EmailTrack、EmailUnsubscribe、Form、LandingPage
- **服务域**：Ticket、TicketSurvey、KnowledgeArticle、SlaPolicy、SlaCalendar、CustomerPortal
- **协作与工作台**：Task、Announcement、Approval、Workflow、Notification、CallRecord、MailAccount、FieldVisit
- **数据与分析**：Stats、Report、Suggestion、Search、Export、ScheduledExport、AuditLog、RecycleBin、DataRetentionPolicy、ComplianceExport
- **平台与扩展**：CustomField、CustomObject、IntegrationChannel、OpenPlatform

统一前缀 `/api/v1`，Swagger UI：`http://localhost:8081/swagger-ui.html`。

## 七、非功能能力

- **权限体系**：JWT 认证 + RBAC（角色-菜单-权限点）+ 字段级权限（056）+ 行级数据权限/客户共享（012、063）+ 登录验证码可开关（048）+ **登录双因素认证（TOTP 2FA，082）**。
- **安全加固**：唯一约束（生成列）、导出安全、Redis 缓存、异常统一处理（003、063）；**2FA**（082）——密钥以 AES-256-GCM 密文落库、恢复码只存哈希、动态码防重放（Redis 记已用时间步）、5 次失败锁定 15 分钟、Redis 不可用时**拒绝登录（503）而非降级为单因素**；管理员可用 `user:manage` 重置他人 2FA。
- **性能与完整性**：预警批量聚合、默认负责人、只读事务（064）。
- **国际化**：zh-CN / en 双语，菜单、列表页、ProTable 搜索表单均已资源化（060、066、074–076）。
- **PWA**：Service Worker、离线缓存、安装提示（027）。
- **前端体验**：路由懒加载 + chunk 预加载、骨架屏、滚动复位、淡入过渡、移动端横向菜单（039、072）。
- **可视化**：ECharts（仪表盘/大屏/报表）、G6（使用地图状态机）。
- **部署**：Docker Compose 一键启动（前端 80、后端 8081、Swagger、MySQL 3306、Redis 6379），Nginx 反代。

## 八、文档与代码的偏差（2026-09-11 已同步修正）

| 位置 | 原描述 | 修正后 |
|---|---|---|
| `README.md` | 「Flyway 会自动创建全部 75 张表」 | 84 张表（迁移 V1–V77） |
| `README.md` | 「specs/（573 个功能模块）」 | 81 个模块目录（001–081，缺 069） |
| `specs/README.md` 模块表 | 收录到 066 | 补录 067–081 |
| `specs/README.md` 迁移对照 | 收录到 V54 | 补录 V55–V77（含 V72 空缺标注） |
| `specs/roadmap.md` 进度 | 收录到 066 | 补录 067–081 |

> ⚠️ **读法（2026-09-16 追加）**：上表「修正后」列记的是 **2026-09-11 当时**改成了什么，**不是今天的值**——
> 例如「84 张表（迁移 V1–V77）」与「81 个模块目录」此后都又变过（现为 **86 张表 / V1–V90**、**96 个模块目录 / 001–097**；⚠️ **2026-09-16 由 097 订正**——本行原写「V1–V89」「94 个模块目录 / 001–095」，**那在写下时是对的**，此后 096 与 097 各自加了迁移与模块目录，故同步到本节原句里；**订正前的读数仍可在本行引号内 grep 到**）。
> **本文件的当前值一律以「一、规模速览」为准**（那一节有「依据」列与重测日期）；本节**作为历史留痕保留，不回写**。

## 九、代码核对发现的可达性缺口

**已修复（2026-09-11）**

1. **全局搜索已接入顶栏**：`GlobalSearch` 现由 `App.tsx` 的 Header 渲染，回车进入 `/search` 结果页。
2. **菜单补齐**：销售配额、定时导出（数据分析组）、渠道 ROI（营销中心组）、工作流日志（流程与配置组）、数据保留（审计与维护组）。
3. **子页入口补齐**：配额列表页新增「配额对比」按钮；数据保留列表页新增「合规导出」按钮。
4. **DashboardPage hooks 违规**：三个 `useMemo` 已上移至 early return 之前，错误的 React 渲染问题消除，被 skip 的错误态测试恢复为通过（该文件 eslint 0 problems）。
5. **文档索引滞后**：见第八节。
6. **配额列表页占位数据**：新增 `GET /api/v1/sales-quota/summary` 年度汇总端点，三张 KPI 卡接入真实数据；新增 `QuotaCreatePage` 与 `/quotas/create` 路由，「创建配额」按钮接通跳转。
   ⚠️ **2026-09-16 订正（由 099 执行）**：上句**原文逐字保留、不删改**（写下时（2026-09-11）它是事实）。**其中「`QuotaCreatePage` 与 `/quotas/create` 路由」这一半已不再成立** —— 099 把创建配额改成 **`/quotas` 列表页内的 `FormModal` 弹窗**（点工具栏「创建配额」打开，`lg` 档 800px），并**同批删除**了 `frontend/src/pages/quotas/QuotaCreatePage.tsx`（126 行）与 `App.tsx` 里那条 `<Route>`。**同句的前半（summary 端点 + 三张 KPI 卡接真实数据）仍然成立、一字未动**；「创建配额」按钮也仍在、只是 onClick 由 `navigate` 改成开弹窗。⇒ **本条的处置是「部分过时」，不是「已废弃」**。099 的形制：纯前端、无端点、无迁移；行为层证据见 `specs/099-quota-create-modal/`。
7. **数据保留编辑入口失效**：新增 `DataRetentionPolicyEditPage` 与 `/data-retention/:id/edit` 路由，编辑链接不再落到 404。

**已修复（2026-09-15，由 093 交付）**

8. **仪表盘名实相符**：`/stats` 此前有三类问题，均已处置。
   ① **两块凭空捏造的卡片已删**——「待办事项」的三条截止日 `2026-08-30 / 08-31 / 09-01` 是写死的常量（代码注释自认「模拟待办数据（实际应从 API 获取）」），人名为 `张三/李四/王五/赵六`；「最近活动」的时间戳用 `dayjs().subtract(...)` **现算**，故它永远看起来刚刚发生。两者在 006 里**没有任何验收要件**，由真实的「跟进活动」与「最近跟进」两卡取代。
   ② **4 个硬编码同比已删**——KPI 行上 `trend={{ value: 5 | 8 | 12 | 3 }}` 挂着「较上月」，而后端**从不下发任何环比字段**，四个百分比是字面量。
   ③ **两处名实不符已修**——FR-D02 要求的**赢单率**此前缺位；第二张卡的标签是「活跃商机」而其值是 `summary.opportunityCount`（后端 `DashboardStatsService` 里是**全部**商机数）——标签与值不是一回事，已改为「商机总数」。
   同批**补齐 006 三项从未渲染的验收要件**（US2 情形 1 预测卡 / FR-D07 客户分析 / FR-D08 跟进活动报表）：数据后端早已下发、TS 类型早已定义、i18n 键早已写好，缺的只是页面里的那一次渲染。**本文件第 0 节与本文件同批订正**——此前「成交预测」被删、而「客户分析」是**从 i18n 键的存在推断已实现**（当时并无组件）写下的，「业绩趋势」则全仓不存在（真实卡片标题是「业绩达成」）。006 的记账订正见 `specs/006-sales-dashboard/tasks.md` 的订正段。**形制**：纯前端渲染层改动，无新端点、**无后端改动**、无迁移、无契约变更。

**「仍待处理」（原标题，逐字保留在上）的处置：已查明、无须动作（2026-09-16，由 097 结案）**

1. **迁移编号空缺**：V72 未使用（V71 → V73）；spec 目录缺 069。

   ⚠️ **本条已查明 ⇒ 结案，且无须任何动作。** 两条核实命令（**查全历史**，不只看当前树）：

   ```bash
   git log --all --oneline --diff-filter=A -- '*V72*'       # 空
   git log --all --oneline --diff-filter=A -- 'specs/069*'  # 空
   ```

   ⇒ `V72` 与 `specs/069` **全历史从未存在过**——既不是「曾经有、后来被删」，也不是「被改过名」，
   是**纯编号空缺**。两条看起来可行的处置**都要否决**：

   - **不补号**（不新建一个空的 `V72__noop.sql`）：那是**造一个假工件**去填一个真实的空格，
     且它会进迁移计数（本文件 §一 那一行）与 `specs/README.md` 的迁移对照表；
   - **不改名**（不把 `V73` 及其后**整体前移**占掉 `V72`）：迁移的**版本号是 Flyway checksum 的一部分**，
     重排会让**已经应用过**的迁移在新库/旧库上全部对不上 checksum，**生产库将拒绝启动**。

   ⇒ 唯一正确的动作就是**什么都不做**，并把这件事登记在案（本段即登记）。**「编号空缺」不是缺陷，
   是既成事实**；把它写成待办会诱导后来者去做上面两件错事之一。

---
*本文件由代码核对生成，如需按此更新 README / specs/README.md，可基于第八节表格直接修改。*
