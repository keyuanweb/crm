# Quickstart: CRM 初始版本验证指南

**Branch**: `001-crm-core` | **Date**: 2026-08-21 | **Spec**: [spec.md](./spec.md) | **Contracts**: [contracts/](./contracts/README.md) | **Data Model**: [data-model.md](./data-model.md)

> 本文件是端到端验证/运行指南（不包含实现代码；实现细节见 tasks.md 与实现阶段）。
> 每个场景对应规格中的用户故事/功能需求，用于证明功能按契约工作。

## 前置条件

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17 (LTS) | 后端运行 |
| Maven | 3.8+ | 后端构建（`mvn -v` 验证） |
| MySQL | 8.0 | 主存储；创建 `crm_db`（utf8mb4） |
| Redis | 7.0 | 缓存与令牌；`redis-server` 启动 |
| Node.js | 18+ | 前端（`node -v` 验证） |

## 初始化与启动

### 1. 数据库

```sql
CREATE DATABASE crm_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'crm_user'@'localhost' IDENTIFIED BY 'crm123456';
GRANT ALL PRIVILEGES ON crm_db.* TO 'crm_user'@'localhost';
```

### 2. 后端

```bash
cd backend
mvn spring-boot:run
```

- 启动时 Flyway 自动执行版本化迁移并种子管理员（admin / admin123，bcrypt 哈希）；
- 验证：打开 `http://localhost:8081/swagger-ui/index.html`（OpenAPI 契约，章程原则一）。

### 3. 前端

```bash
cd frontend
pnpm install
pnpm run dev
```

- 访问 `http://localhost:5173`，登录 admin / admin123。

## 验证场景（对应用户故事 / FR）

### S1 登录认证（FR-认证 / US 全流程前提）

1. 用 admin/admin123 登录 → 应成功进入系统，顶部显示当前用户与角色。
2. 登出后直接请求任意业务接口 → 应返回 401。

### S2 客户全生命周期（US1 / FR-001~005）

1. 客户列表 → 空列表页正常展示。
2. 新建客户（名称"张三"、公司"XX 科技"、电话 13800000000）→ 保存成功，列表出现该客户。
3. 编辑该客户公司为"YY 科技"→ 列表与详情同步更新。
4. 删除该客户 → 从列表消失；用管理员账号在数据层验证 `deleted=1`（逻辑删除，SC-005）。

### S3 客户检索与分页（US2 / FR-001 / SC-002）

1. 导入或手工造数 ≥ 100 条客户。
2. 搜索关键字"科技"→ 仅返回名称/公司/联系人/电话命中的客户。
3. 翻页后返回第 2 页，搜索与筛选条件保持。
4. 感知响应时间 ≤ 2 秒（SC-002）。

### S4 商机 → 销售机会 → 关闭（US3 / FR-007~014）

1. 为客户"张三"创建商机（名称"年度合作"、预期金额 10 万~50 万）→ 商机列表出现。
2. 为该商机创建销售机会（金额 30 万、阶段 INITIAL_CONTACT、预计成交 2026-09-30）→ 管道阶段列表出现。
3. 将阶段流转为 NEGOTIATING → 从 INITIAL_CONTACT 列表移至 NEGOTIATING。
4. 关闭（WON）→ 状态变为 CLOSED_WON，不再出现在活动管道（FR-014）。

### S5 跟进记录（US4 / FR-015）

1. 打开客户详情 → 添加跟进（方式 PHONE、内容"沟通续约意向"）→ 时间线出现。
2. 修改该记录内容 → 时间线更新。
3. 尝试修改他人的跟进记录（用 SUPPORT 角色）→ 应返回 403。

### S6 客户导入导出（US5 / FR-006，仅 ADMIN）

1. 下载导入模板 → 按模板填 50 条客户 → 上传导入 → 返回成功 50 / 失败 0。
2. 故意造 2 条非法行（缺公司、电话格式错）→ 返回失败明细（行号 + 原因）。
3. 点击导出 → 下载 .xlsx，内容与当前筛选结果一致。

### S7 商机统计（US6 / FR-011 / SC-006）

1. 存在多个不同阶段销售机会时打开统计页 → 各阶段数量与金额合计正确。
2. 关闭一条销售机会为 WON 后刷新统计 → CLOSED_WON 合计随之更新，且与列表查询结果一致（SC-006）。

## 自动化验证

```bash
# 后端：单元 + 集成 + 契约校验（合并门禁）
cd backend && mvn test

# 前端：单元/组件 + 类型检查 + Lint
cd frontend && pnpm run test && pnpm run typecheck && pnpm run lint

# 端到端：Playwright（覆盖 S1~S7 关键路径）
cd frontend && npx playwright test
```

**预期**: 全部测试通过；覆盖率达到章程原则四配置的门槛；任何契约变更必须附带契约测试（原则一）。
