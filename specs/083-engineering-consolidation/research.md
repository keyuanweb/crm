# Research: 工程收口与质量门禁修复

**Branch**: `083-engineering-consolidation` | **Date**: 2026-09-12

## 1. 集成测试执行机制

**Decision**: 引入 `maven-failsafe-plugin`，`<includes>` 显式声明 `**/*IT.java`，`integration-test` goal 绑 `integration-test` 相位、`verify` goal 绑 `verify` 相位。不引入 surefire 的 `<includes>` 改动——`*Test.java` 已由 surefire 默认执行。

**Rationale**: failsafe 与 surefire 的默认包含模式天然互补（`*IT.java` / `*Test.java`），因此两者可以并存而不重复执行。当前 `pom.xml` 全文无 failsafe/surefire 声明，`target/failsafe-reports/` 不存在、`target/surefire-reports/*.txt` 为 0 个，证明 60 个 `*IT.class` 从未被执行。

**Alternatives considered**: 把 `*IT.java` 改名为 `*Test.java` 让 surefire 顺带执行——会破坏既有的"单元/集成"命名分层，且使集成测试在 `test` 相位（早于打包）执行，语义错误。故不采用。

## 2. JaCoCo 相位、`argLine` 与阈值

**Decision**: ① `jacoco:report` 从 `test` 相位迁至 **`post-integration-test`**（紧跟 `integration-test` 之后、`verify` 之前）；② `jacoco:check` 保持 `verify`；③ 阈值由实测值上调，并删除自述"预留余量"的注释。

**Rationale**: Maven 生命周期顺序为 `test → integration-test → post-integration-test → verify`。`report` 留在 `test` 相位则报告在集成测试执行**之前**生成，即使接入 failsafe 也不会把集成测试覆盖率计入——门禁会在"覆盖数据不完整"的前提下依然通过，形成**假绿**。这是本规格最容易做错的单点。

**Alternatives considered**: 把 `report` 与 `check` 同绑 `verify` 靠声明顺序保证先后——隐式依赖插件声明次序，脆弱。相位区分是显式的。

> 实施注意：surefire 与 failsafe 分属不同 JVM，均通过 `argLine` 属性接收 JaCoCo agent。若 `pom.xml` 自定义了 `<argLine>`，必须保留 `@{argLine}` 占位，否则集成测试数据不会写入 `jacoco.exec`。`prepare-agent` 的 `append` 默认为真，两次执行的数据会累积到同一文件。

## 3. 测试库镜像策略与长期守卫

**Decision**: 把 V70–V77 **机械转译**进 `schema-h2.sql`，不做语义重写：新表按既有建表语句形制补在文件内，既有表的新增列追加回**对应建表语句内部**（而非文件末尾的 `ALTER`），角色/权限种子 INSERT 置于**角色表建表之后**。同时新增迁移-镜像一致性守卫测试（FR-G07）。

**Rationale**: `schema-h2.sql` 停在 V69（mtime 2026-08-25 12:51 与 V69 时间戳一致），V70–V77 全部未镜像：`user` 缺 `email`（V76，而 `entity/User.java:17` 已声明）、`opportunity` 缺 `amount`（V77，被 `DashboardStatsService`/`Customer360Service`/`ComplianceExportService` 读取）、`sales_quota`/`scheduled_export`/`data_retention` 三组表完全缺失。V75 经核实为**纯增量**（只新增 10 个预置角色，全文不含 `ADMIN`/`SALES`/`SUPPORT` 的修改语句），故镜像成本远低于初始估计。

无守卫则本规格的修复会随下一次迁移再次失效——这正是缺陷的成因（机制无回填），必须同时封堵。

**Alternatives considered**: 关闭 H2 测试库、让集成测试连真实 MySQL——集成测试将依赖外部服务，破坏 `AbstractIntegrationTest` 既有的自包含设计（`application-test.yml` 用 H2、`flyway.enabled: false`、`schema-locations: schema-h2.sql`），且 CI 需额外服务容器。不采用。让集成测试改用 Flyway 直跑——生产迁移含 MySQL 特有语法，H2 无法解析。不采用。

## 4. 进程内缓存实现

**Decision**: 用 `ConcurrentMapCacheManager` 不足以表达 TTL，改用 **Caffeine**（`CacheManager` 编程式配置于 `CacheConfig`），Service 层通过注入的 `CacheManager` **显式** `get`/`put`/`invalidate`，不使用 `@Cacheable` 注解。

**Rationale**: ① **必须进程内**——`AbstractIntegrationTest` 将 `RedisTemplate` 声明为 `@MockBean`，用 Redis 做缓存会使缓存在测试中静默失效（`opsForValue()` 返回 mock，读写无效果），缓存逻辑将**无法被任何测试验证**。② 不用注解是因为同一类内部的自调用不经过代理，`@Cacheable`/`@CacheEvict` 会静默不生效；显式调用让失效点可被审计。③ Caffeine 提供 TTL 兜底与容量上限，且版本由 Spring Boot 统一管理（无需指定版本号）。

**Alternatives considered**: 手写 `ConcurrentHashMap` + 时间戳 TTL——需要自行实现淘汰与并发控制，代码量更大（违反章程原则五"简洁"）。`ConcurrentMapCacheManager`——零依赖但无 TTL，无法覆盖"直接改库/多实例"导致的永久陈旧。Redis——见上，在测试中不可验证。

**T062 补充（实现落点，原决策未及）**：角色权限缓存的读写直接经 `CacheManager`（`RoleService`）；**可见范围缓存则经一个专门的访问包装 `security/VisibleOwnerIdsCache`**。原因是 `DepartmentService` 需在写操作后触发全量失效，而它若直接依赖 `DataPermissionService` 会形成依赖环；该包装类同时把 `evictAll()` 收成唯一出口，使"全量失效"无从绕过。**这不改变本节决策**（仍是显式调用、不用注解），只是把其中一处调用的落点写清——原决策的文字会让人以为两处缓存的访问形态相同。

## 5. 缓存跨测试类泄漏的隔离

**Decision**: `AbstractIntegrationTest` 的 `@BeforeEach` 清空全部进程内缓存（与既有的 `stubRedis()` 同处）。

**Rationale**: Spring TestContext 会跨测试类复用同一 `ApplicationContext`，进程内缓存的 Bean 因此**在测试类之间存活**。若不清理，用例结果将依赖执行顺序（前一个类写入的缓存使后一个类看到陈旧数据），产生偶发失败——这类失败极难归因，且会污染本规格新接入的集成测试基线。

**Alternatives considered**: 给每个测试类加 `@DirtiesContext` 强制重建上下文——每个类都要重跑整个 Spring 启动，集成测试总时长显著上升。不采用。

## 6. 密钥主体的角色语义（去 ADMIN 化）

**Decision**: 密钥鉴权不再注入 `ADMIN` 身份；`EntityAccessService.isUnrestricted()` 改写为**显式三分支**（无主体 → 放行；`ADMIN` → 放行；其余 → 不放行），**保留"无主体"分支**。

**Rationale**: 现状是 `new CrmPrincipal(0L, "open-api", "ADMIN")`（注释自述"绕过行级权限"），使任何有效密钥都同时获得 `EntityAccessService` 的无限制放行与 `PermissionAspect` 的 ADMIN 短路——两处提权。`principal == null` 分支服务于**系统内部调用**（如调度器），与密钥调用是不同主体，删掉会让内部调用被误拒，故必须保留。改动改变既有授权结果（原可读全量的调用方将收到 403/空），属安全修复的预期效果而非回归；按章程原则一"契约不得被静默修改"，变更同步记入 `specs/055-open-platform/contracts/open-platform.md`。

**Alternatives considered**: 给密钥主体一个专用角色码并纳入既有权限体系——需要新增权限码与角色种子，超出"不新增权限码"的约束。不采用。

## 7. 出站地址校验

**Decision**: 新增 `OutboundUrlValidator`，被通知通道与回调服务复用；默认**全拒**，仅允许经环境变量白名单显式放行的地址；校验重定向后的最终地址。

**Rationale**: 现状两处不等价——一处只判 `startsWith("http://")`（甚至连 `https` 都不在判断内），另一处**完全无校验**（直接取用请求中的地址）。只判协议既拦不住回环/私有网段/云元数据地址，也拦不住重定向绕过。默认全拒 + 显式白名单使"合法内网集成"成为需要主动声明的例外，而非默认敞口。

**Alternatives considered**: 仅黑名单（拦回环 + 私有网段 + 链路本地）——新出现的绕过方式（DNS 重绑定、IPv6 映射、非标准端口）无法穷举，且漏一个即为敞口。不采用单独黑名单，但黑名单可作为白名单之外的补充校验。

## 8. 实时通道握手

**Decision**: 握手时复用 `JwtAuthFilter.validateUserState` 的既有逻辑（校验 `enabled` 与 `tokenVersion`）；允许来源**复用既有 `cors.allowed-origins`**（环境变量 `CORS_ALLOWED_ORIGINS`），不新增第二个来源配置项。

**Rationale**: 现状握手仅 `jwtUtil.parse(token)` 取 userId，不校验用户状态，使停用用户/已失效令牌仍可建立长连接——绕过了 `JwtAuthFilter.java:85-99` 的失效机制。来源方面，`SecurityConfig` 已有 `cors.allowed-origins`（`application.yml:48`，默认三个本地端口），实时通道是同源的浏览器来源，复用它可避免"两个来源配置不一致导致一路放行一路拦截"的经典问题。

**Alternatives considered**: 新增 `websocket.allowed-origins`——多一个必须同步维护的配置项，漂移风险大于其灵活性收益。保留 `setAllowedOrigins("*")`——任意站点均可建立已认证连接。

**T062 补充（实现取值差异）**：上文"默认三个本地端口"指 `application.yml:48` 的取值；而 `WebSocketConfig` 注入点的回退值写的是 `${cors.allowed-origins:http://localhost:5173}`，**只有一个端口**（`WebSocketConfig.java:47`）。二者在正常运行下等价——`application.yml` 总会提供该属性，环境变量覆盖（含 `docker-compose.yml` 的 `CORS_ALLOWED_ORIGINS=http://localhost`）同样生效——差别只在"属性被整体移除"这一实际不会出现的配置下才显现，故未改代码；**记录于此，以免读者以为两处默认值相同**。

## 9. 前端 API 客户端收口与保形

**Decision**: 三个模块的 API 客户端统一改用站点既有的 `accessToken` 键，并入既有 `apiClient`；`ComplianceExportPage` 改走同一客户端。**收口时必须保形**——这三个模块的后端返回裸响应体，不得按全站错误信封解包。

**Rationale**: 全站写入的键是 `accessToken`（`store/authStore.ts:41`、`services/apiClient.ts:28`、`hooks/useNotificationSocket.ts:37`），全仓库**无任何地方写入 `'token'`**，故这三处读到 `null`、发出 `Bearer null` → 401。三份逐字相同的裸 `fetch` 绕过了 `apiClient`，因此 401 不触发登出跳转，且错误格式与全站不一致（原始 JSON 而非信封）。保形要求是收口的主要风险点：`apiClient` 的解包逻辑与这三个模块的响应结构不同，盲目复用会使 14 个页面从"401"变为"解析失败"。

**Alternatives considered**: 只把 `'token'` 改名为 `'accessToken'`（改动最小）——仍保留三份重复封装，`Authorization` 拼装与错误处理继续与全站分叉，且不解决 `ComplianceExportPage` 完全无凭据的问题。不采用为最终方案。

## 10. 容器编排的前端产物

**Decision**: 前端镜像**在构建期自己产出**静态产物，不挂载宿主机目录；同时移除把 Flyway 迁移目录挂成数据库初始化目录的挂载；并把编排中的允许来源改为编排实际暴露的端口。

**Rationale**: 三处缺陷同因——编排假设了本机开发环境的状态。① 迁移目录被挂成数据库初始化目录后，数据库会按**字母序**执行迁移（`V1, V10, V11, …, V2, V20 …`），与迁移工具的版本序双轨冲突；README 已声明"只需建空库"。② 前端挂载宿主机构建产物目录，而该目录不随仓库交付也不被编排构建 → 干净检出后是空白页。③ 允许来源指向开发端口，而编排下前端由容器内的 Web 服务器在 80 端口提供。

**Alternatives considered**: 保留挂载、在 README 里要求先手工构建前端——"一键启动"降级为"按文档手工预备"，缺陷仍在。不采用。

## 11. 全局搜索不补权限注解（显式决策）

**Decision**: 三个控制器补既有权限码；`SearchController` **明确不加**注解，并记录为有意决策。

**Rationale**: 初始判断是"四个控制器缺注解"，复核后发现全局搜索在系统中**不存在**对应的权限码。补一个不存在的权限码会使权限切面按"无此权限"拒绝，结果是对**所有**用户（含管理员之外的全体）失效——把可用功能改坏，而非修好。

**Alternatives considered**: 新增 `search:use` 权限码并纳入角色种子——违反本规格"不新增权限码"的约束，且该码的归属需要产品决策，超出收口范围。
