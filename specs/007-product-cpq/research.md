# Research: 产品与报价模块

**Branch**: `007-product-cpq` | **Date**: 2026-08-22

## 1. PDF 生成库与中文字体方案

**Decision**: 使用 OpenPDF（`com.github.librepdf:openpdf:1.3.30`），打包开源中文字体 `wqy-microhei.ttc`（文泉驿微米黑，GPL+font exception 许可，可随项目分发）到 `backend/src/main/resources/fonts/`，通过 `BaseFont.createFont("fonts/wqy-microhei.ttc,0", IDENTITY_H, EMBEDDED)` 加载（TTC 索引 0 = WenQuanYiMicroHei）。

**Rationale**: OpenPDF 是 iText 4 的活跃开源分支（LGPL/MPL 双许可），API 简单、无额外依赖；已验证字体嵌入加载成功。中文支持依赖显式注册 Unicode 字体，打包字体资源保证跨环境（本机/CI）一致性，避免依赖系统字体目录。

**Alternatives considered**:
- Apache PDFBox（Apache-2.0）：功能更强但 API 较繁琐，字体嵌入配置复杂——否决。
- 系统字体（simsun/msyh）：本机有但 CI/容器无，不可移植——否决。
- 前端 HTML 打印生成 PDF：依赖浏览器行为不可控，且 spec 假设后端生成——否决。

## 2. 数据模型：产品/报价单/报价行

**Decision**: 三张新表：
- `product`：编码（`code` 全局唯一，active_key 生成列模式）、名称、规格、单位、标准售价（分）、状态（ACTIVE/INACTIVE）、逻辑删除/乐观锁/创建人/时间戳。
- `quote`：报价单号（`quote_no` 唯一，Q-YYYYMMDD-4位序号）、客户、商机（可选）、有效期、状态（DRAFT/PENDING_APPROVAL/APPROVED/REJECTED）、总额（分）、备注、审批人/审批时间/拒绝意见、逻辑删除/乐观锁/创建人/时间戳。
- `quote_item`：报价单、产品（可空，快照引用）、产品名快照、单价快照（分）、数量、折扣、行小计（分）。

**Rationale**: 报价行保存名称/单价快照，产品后续改名/停用不影响历史报价（spec 边界情况）；金额统一分精度（与既有系统一致）；报价单号自动生成保证可追溯性。

**Alternatives considered**: 不做独立价格表实体（spec 假设：标准售价+报价时手动调价，无客户专属价）——YAGNI。

## 3. 报价单状态机

**Decision**: `DRAFT → PENDING_APPROVAL → APPROVED | REJECTED`；REJECTED 可编辑后重提回 PENDING_APPROVAL；PENDING_APPROVAL 仅审批人可操作（通过/拒绝/退回）；APPROVED 终态。状态变更校验在 Service 层强制（非法流转抛 409）。

**Rationale**: 单级审批（spec 假设，后续工作流模块增强）；终态不可变保证审批留痕可信。

## 4. 权限模型

**Decision**: 产品目录查看全员、写操作仅 ADMIN；报价单查看/创建/编辑 SALES+ADMIN，审批仅 ADMIN。Controller 层 `@PreAuthorize` 强制（与既有 UserController/StatsController 模式一致）。

**Rationale**: spec FR-P12/P13；服务端授权而非仅 UI 隐藏（章程原则三）。

## 5. 报价单号生成与并发

**Decision**: 报价单号 `Q-YYYYMMDD-XXXX`（当日 4 位序号）。生成采用数据库插入后回填：先插入获取 id，再更新 quote_no（或在事务内查询当日最大序号+1）。为避免竞态，使用乐观锁版本号控制编辑冲突，序号冲突重试一次。

**Rationale**: 序号格式直观可追溯；当日序号无需全局表。为简化实现且满足唯一约束，采用「插入占位 → 事务内计算序号 → 更新」策略，冲突时回滚重试（概率极低）。

## 6. 契约与前端

**Decision**: REST 契约写入 `contracts/quotes.md` 与 `contracts/products.md`；前端新增产品管理页与报价单列表/详情页（ProTable + antd Modal/Form 行编辑），报价详情展示行明细与审批操作按钮。

**Rationale**: 与既有页面模式一致；报价单行编辑用受控 Table + 行级表单。
