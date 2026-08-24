# 快速开始：审批流

## 后端

1. Flyway V49：approval_flow/approval_instance/approval_task/approval_log 四表。
2. 实体/Mapper 4 组。
3. `ApprovalFlowService`（定义 CRUD + 条件解析 + 节点匹配）。
4. `ApprovalEngineService`（发起/通过/驳回/转交/重提 + 状态机 + 026 通知）。
5. `ApprovalController`（配置 + 我的审批）。
6. 合同/报价接入引擎（兼容原逻辑）。
7. 测试：ApprovalEngineServiceTest + ApprovalIT。

## 前端

1. `types/approval.ts` + `approvalService.ts`。
2. `ApprovalFlowPage`（审批流配置）+ `ApprovalCenterPage`（我的审批）。
3. 路由注册。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：配置双级流程 → 合同提交 → 一级通过 → 二级驳回 → 重提 → 全通过。
