# Implementation Plan: 合同管理模块

**Branch**: `008-contract-management` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增合同（Contract）、合同附件（ContractAttachment）、合同模板（ContractTemplate）三大实体：合同支持基于已通过报价单创建（自动带入客户/金额）或直接创建，状态机 DRAFT→PENDING_APPROVAL→APPROVED→EFFECTIVE→COMPLETED/TERMINATED（REJECTED 可编辑重提），管理员审批；附件上传/下载/删除（本地磁盘存储，类型白名单 + 20MB 限制）；模板管理（3 个内置占位符替换生成正文）。前端新增合同列表/详情页与模板管理页。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、Spring Multipart（附件）、AuditService

**Storage**: MySQL 新增 `contract`、`contract_attachment`、`contract_template` 三表（Flyway V16~V18）；附件文件存本地磁盘（`crm.contract.storage-dir`，默认 `./contract-files`）

**Testing**: JUnit 5 + Spring Boot Test（ContractServiceTest/ContractTemplateServiceTest 单元、ContractIT 集成含附件）

**Target Platform**: Web（合同列表/详情页 + 模板管理页）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 合同列表/创建 ≤1s（SC-CT01）；附件上传下载 ≤2s（20MB 内）

**Constraints**: 基于报价创建须报价已通过且客户一致；合同编号唯一自动生成；状态机非法流转 409；附件类型白名单 + 20MB；日期校验（生效 ≤ 结束）

**Scale/Scope**: 合同/附件/模板数量级 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层，附件 IO 独立 Service） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize 角色控制 + 附件类型/大小校验） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（列表批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/008-contract-management/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/contracts.md

backend/src/main/java/com/crm/
├── entity/Contract.java + repository/ContractMapper.java
├── entity/ContractAttachment.java + repository/ContractAttachmentMapper.java
├── entity/ContractTemplate.java + repository/ContractTemplateMapper.java
├── dto/contract/（ContractRequest/ContractResponse/ContractTemplateRequest/ContractTemplateResponse/AttachmentResponse）
├── service/ContractService.java（状态机/基于报价创建/列表装配）
├── service/ContractAttachmentService.java（上传/下载/删除，磁盘 IO）
├── service/ContractTemplateService.java（模板 CRUD/占位符替换）
├── controller/ContractController.java + ContractAttachmentController.java + ContractTemplateController.java
├── common/ErrorCode.java（新增 CONTRACT_* / ATTACHMENT_* / TEMPLATE_* 错误码）
└── resources/db/migration/V16__contract.sql + V17__contract_attachment.sql + V18__contract_template.sql

backend/src/test/java/com/crm/
├── service/ContractServiceTest.java + ContractTemplateServiceTest.java
├── integration/ContractIT.java（含附件上传下载、状态机、权限）

frontend/src/
├── types/contract.ts + services/contractService.ts
├── pages/contracts/ContractListPage.tsx + pages/contracts/ContractDetailPage.tsx
├── pages/contract-templates/ContractTemplateListPage.tsx
└── App.tsx（合同/合同模板菜单 + 路由）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。附件磁盘 IO 独立 `ContractAttachmentService`；模板占位符替换放 `ContractTemplateService`。

## Complexity Tracking

无违规，本表留空。
