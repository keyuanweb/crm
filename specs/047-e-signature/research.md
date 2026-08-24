# Research: 电子签署模块

**Branch**: `047-e-signature` | **Date**: 2026-08-25

## 1. 签署模型

**Decision**: 新增 `signature_record` 表（business_type QUOTE/CONTRACT、business_id、signer_id、signer_name、signed_at、signature_image 文本列 base64）。报价/合同状态枚举扩展 SIGNED。签署记录统一表，支持报价与合同两类单据。

**Rationale**: 统一记录便于审计与查询；base64 存文本列（v1 简化，量大可迁对象存储）。

**Alternatives considered**: 分别挂在 quote/contract 表加签名字段——单据与记录耦合、查询不便；接入第三方电子签名服务（法大大等）——合规但外部依赖重，v1 不做。

## 2. 状态机扩展

**Decision**: Quote 状态加 SIGNED（APPROVED→SIGNED，终态不可编辑）；Contract 状态加 SIGNED（APPROVED→SIGNED→EFFECTIVE，未签署不可生效）。

**Rationale**: 报价签署后即确认（终态）；合同签署是生效前置（保持法律流程严谨）。

## 3. 签署校验

**Decision**: 仅 APPROVED 可发起签署；同一单据仅一次签署（409 SIGNATURE_ALREADY_SIGNED）；签名图必填且 ≤500KB（base64 长度校验）；签署后 audit 记录。

**Rationale**: 防止重复/越权签署；审计留痕（章程原则三）。

## 4. 前端签名

**Decision**: 详情页"签署"区块：canvas 手绘签名板 + 图片上传两种方式，提交 base64 至后端；已签署展示签署记录（签署人/时间/签名图预览）。

**Rationale**: 手绘降低录入成本（对标 DocuSign 手写签）；上传兼容已有图片签名。
