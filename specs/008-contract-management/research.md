# Research: 合同管理模块

**Branch**: `008-contract-management` | **Date**: 2026-08-22

## 1. 附件存储方案

**Decision**: 附件存服务器本地磁盘（配置键 `crm.contract.storage-dir`，默认 `./contract-files`），数据库仅存元数据（文件名、相对存储路径、大小、上传人、上传时间）；上传时以 `contractId/yyyyMMdd/UUID-原文件名` 布局落盘；下载时校验路径穿越（仅允许存储根目录内文件）；删除为物理删除（文件 + 记录）。

**Rationale**: spec 假设本地磁盘；实现简单、可离线验证。路径穿越防护（`Path.normalize().startsWith(root)`）满足章程原则三（数据安全）。

**Alternatives considered**: MinIO/OSS 对象存储（生产可替换但本地验证复杂，后续增强）——否决。

## 2. 合同状态机

**Decision**: `DRAFT → PENDING_APPROVAL → APPROVED → EFFECTIVE → COMPLETED | TERMINATED`；REJECTED 可编辑后重提回 PENDING_APPROVAL；APPROVED/EFFECTIVE/COMPLETED/TERMINATED 为不可编辑态。非法流转（重复审批、编辑终态、跳过节点）抛 409 QUOTE_INVALID_STATE 同模式（命名 CONTRACT_INVALID_STATE）。

**Rationale**: 与报价状态机同构（复用既有模式与前端交互习惯）；终态不可变保证合同档案可信。生效/完成/终止记录时间与原因，满足履约追溯。

## 3. 基于报价创建合同

**Decision**: `POST /contracts` 支持可选 `quoteId`：若提供，校验报价存在、状态为 APPROVED、客户与请求一致，自动带入客户、金额（报价总额）与报价单号（存备注或独立字段）；否则客户必填、金额手填。金额均分精度。

**Rationale**: 报价→合同金额链路唯一且可追溯（SC-CT02）。

## 4. 合同编号生成

**Decision**: `HT-YYYYMMDD-XXXX`（当日 4 位序号），同报价单号实现（当日前缀查询 max+1），唯一约束 active_key 生成列（V6 模式）。编号生成发生在插入前；并发冲突概率极低，靠唯一索引兜底。

**Rationale**: 与报价单号一致，可读可追溯。

## 5. 模板占位符

**Decision**: 内置占位符 `{customerName}`、`{contractNo}`、`{amount}`（金额转元带千分位）。创建合同时若传 `templateId`，正文 = 模板正文替换占位符；不传则正文为空/手填。模板停用（INACTIVE）后新建不可选。

**Rationale**: spec 假设 3 个变量；占位符替换在 Service 层实现（Controller 不承载业务）。

## 6. 契约与权限

**Decision**: 契约写入 `contracts/contracts.md`。合同查看/创建/编辑 SALES+ADMIN（类级 `@PreAuthorize`）；审批、模板写操作仅 ADMIN（方法级）。附件上传/下载/删除与合同同权限（SALES+ADMIN）。

**Rationale**: 与报价模块权限矩阵一致；服务端强制授权（章程原则三）。
