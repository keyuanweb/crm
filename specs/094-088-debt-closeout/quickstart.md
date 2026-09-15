# Quickstart：怎么验收本项（094）

前置：`frontend/` 下依赖已装（`pnpm install`）。**不需要**后端、不需要 dev server——本项全程是静态检查与单测。

## 1. 三处补做是否到位（SC-094-004）

```bash
cd frontend
# 全库 Descriptions 开标签：14（含跨行 4）
grep -rn "^ *<Descriptions$" src --include=*.tsx | wc -l
grep -rn "^ *<Descriptions " src --include=*.tsx | wc -l

# 写死数字的 column：应只剩 1 处（portal 的 column={1}，在册例外）
grep -rn "column={[0-9]" src --include=*.tsx
```

期望：`14`（= 4 + 10）；第二条只剩 `src/pages/portal/CustomerPortalPage.tsx` 的 `column={1}`。

## 2. 门禁（SC-094-001）

```bash
cd frontend
pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage
```

期望：全退出码 0；`ui:check` 末行「✓ UI 规范校验通过（白名单内冻结的既存债 **53** 处，未新增违规）」——
**53 不变**是预期的（R8 零容忍、不进白名单）。

## 3. 护栏真的会红吗（SC-094-002，**这一步才是验收**）

在任一 `<Descriptions>` 上把 `column` 写回一个数字（例如把 portal 服务状态块的断点对象改回 `={3}`），然后：

```bash
cd frontend && pnpm ui:check   # 必须红在【R8 …】上，且退出码非零
```

**还原**（逐字节）后 `pnpm ui:check` 必须转绿。还原核对：

```bash
cd frontend && sha256sum src/pages/portal/CustomerPortalPage.tsx   # 与改动前的读数比
```

## 4. 「零候选」说的是哪一种失败（SC-094-003）

把 R8 的候选扫空（临时把脚本里的 `scanTagEvents(code, 'Descriptions')` 换成扫不到任何东西的标签名，
或临时清空候选收集），跑 `pnpm ui:check`，失败信息里应同时出现：

- ①「扫描器失效」→ 修扫描器；
- ②「规则已无可判对象」→ 按 T045/R2 先例**退役该规则**，并附该规则的历史实测候选数。

**看完立刻逐字节还原**（探针不提交）。

## 5. 088 的记录订正（SC-094-005）

```bash
cd /e/code/crm
grep -n 'filled' specs/088-frontend-layout-consistency/*.md
```

期望：6 处原文**都还在**，且每处紧邻一段 `⚠️ 订正（2026-09-15，094）`。
