# Research: 任务与提醒模块

**Branch**: `010-task-reminder` | **Date**: 2026-08-22

## 1. 数据隔离模型

**Decision**: 任务表含 `owner_id`（归属用户）；列表/编辑/完成/删除均强制 `owner_id = 当前用户`（SecurityUtil.currentUserId()），杜绝越权访问。不做管理员查看全部（spec 假设简化版：仅本人）。

**Rationale**: 个人待办的核心是数据隔离（SC-T03）；实现简单（查询条件 + 归属校验）。

## 2. 逾期/今日到期标识

**Decision**: 标识在 Service 层计算（不落库）：逾期 = `due_at < now` 且状态 TODO，逾期天数 = 天数差；今日到期 = `due_at ∈ [今天 00:00, 明天 00:00)` 且 TODO。响应含 `reminderStatus`（OVERDUE/TODAY/NORMAL/DONE）与 `overdueDays`。汇总 = 逾期数 + 今日数（仅当前用户）。

**Rationale**: 时间相关标识动态计算避免定时任务；与回款提醒模式一致。

## 3. 日历视图

**Decision**: 后端 `GET /tasks/calendar?month=YYYY-MM` 返回该月按截止日期分组的任务（`date → [tasks]`，含逾期标记）；前端 antd Calendar 渲染，逾期日期红色标记，点击日期弹层展示当日任务。

**Rationale**: spec 假设前端组件渲染；后端按月一次返回（量级小，无分页）。

## 4. 跟进自动创建任务

**Decision**: 修改 `FollowUpService.create`：请求新增可选 `createTask`（布尔）+ 复用 `nextFollowUpAt`；为 true 且 nextFollowUpAt 非空时自动创建任务（标题"跟进：客户名/线索名"，截止=nextFollowUpAt，关联类型 CUSTOMER/LEAD，关联 id）。为保持 FollowUpService 不引入任务依赖循环，通过注入 TaskService 实现。

**Rationale**: spec FR-T09；勾选式创建避免全量自动扫描。

## 5. 契约与权限

**Decision**: 契约写入 `contracts/tasks.md`。任务接口类级权限 `hasAnyRole('ADMIN','SALES','SUPPORT')`（所有登录用户），归属强制当前用户。跟进自动建任务不新增端点（复用 POST /follow-ups）。

**Rationale**: 与既有权限模式一致；归属校验在 Service 层强制（章程原则三）。
