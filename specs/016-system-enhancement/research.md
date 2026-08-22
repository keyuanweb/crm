# Research: 系统增强模块

**Branch**: `016-system-enhancement` | **Date**: 2026-08-22

## 1. 自定义字段存储模型

**Decision**: 双表模型：`custom_field`（定义：实体类型、名称、类型、必填、选项、启用、排序）+ `custom_field_value`（值：字段 id、实体类型、实体 id、字符串值）。字段定义删除时物理删除关联值（spec 边界已明确）。创建/编辑实体时随请求提交 `customFieldValues: [{fieldId, value}]`，保存时批量 upsert（先删后插或按字段 upsert）；列表筛选：文本/下拉字段按值 LIKE/EQ 过滤（join custom_field_value + 字段定义过滤实体与类型）。

**Rationale**: 实体表不加列避免迁移风暴（章程原则五 YAGNI）；字符串存储 + 展示层转换覆盖需求类型。

**Alternatives considered**: EAV 单一表 —— 查询更简单但缺定义元数据；JSON 列 —— 无法按字段筛选/校验，拒绝。

## 2. 通知中心模型

**Decision**: 新增统一 `notification` 表（user_id、type[WORKFLOW/TICKET_ASSIGN/TICKET_REPLY]、message、read、entity_type、entity_id、created_at），迁移 V41 将 013 `workflow_notification` 数据复制过来（同结构）。014/015 不产生通知；015 工单分配/回复在 TicketService 中补充通知写入（复用 NotificationService）。保留策略：每用户最近 100 条（写入时或定时清理）。

**Rationale**: 统一模型便于聚合视图与角标；013 数据结构相同可直接迁移。

**Alternatives considered**: 多表联合查询 —— 复杂且难以统一分页，拒绝。

## 3. 数据导出

**Decision**: `export_job` 表（type[LEAD/CUSTOMER/OPPORTUNITY/TICKET]、filter JSON、status[PENDING/RUNNING/DONE/FAILED]、file_path、row_count、error、created_by、completed_at）。创建任务入表后提交到线程池（@Async 或 ExecutorService）执行；执行器按类型查数据（复用各 Service 的查询条件/权限过滤）生成 xlsx 到 `backend/contract-files/exports/`，更新状态与行数。下载走文件读取接口（带鉴权，仅创建人/ADMIN）。保留策略：每用户最近 50 条（创建时清理旧记录）。

**Rationale**: 后台异步避免长请求；复用既有 POI 依赖；文件落盘便于下载与清理。

**Alternatives considered**: 同步导出 —— 大表阻塞请求，拒绝；独立消息队列 —— YAGNI，拒绝。

## 4. 移动端适配

**Decision**: 响应式布局：全局 Content 区域窄屏（<768px）时表格降级为卡片/列表（ProTable `responsive` 列隐藏 + 表单 `labelCol` 垂直布局）；仅做 CSS/antd 响应式优化，不做独立移动应用（spec 假设）。

**Rationale**: 成本可控、覆盖高频移动查看场景。

## 5. 契约与权限

**Decision**: 契约写入 `contracts/`（custom-fields.md/notifications.md/exports.md）。字段配置仅 ADMIN；通知仅本人；导出按实体既有读权限（SALES 仅自身数据，复用各 Service 行级过滤）。前端：设置页字段配置、顶栏通知角标+抽屉、导出中心页。

**Rationale**: 与既有权限模式一致；服务端强制授权（章程原则三）。
