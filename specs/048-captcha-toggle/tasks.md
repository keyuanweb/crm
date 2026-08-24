# Tasks: 验证码临时关闭模块

**Input**: Design documents from `/specs/048-captcha-toggle/`

**Prerequisites**: plan.md (required), spec.md (required), research.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1

---

## Phase 1: 用户故事 1 - 登录免验证码 (P0)

**Goal**: 验证码默认关闭，登录免验证码。

**Independent Test**: 无验证码登录成功。

### 实现

- [x] T001 [US1] application.yml 中 `crm.captcha.enabled` 默认值改为 false in `backend/src/main/resources/application.yml`
- [x] T002 [US1] 适配既有登录测试（如需）in `backend/src/test/java/com/crm/`（AuthIT 等）
- [x] T003 Backend `mvn verify` 通过
- [x] T004 线上验证：重启后无验证码登录成功；/auth/captcha 仍 200
- [x] T005 更新 roadmap 048 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Notes

- 保留 `CAPTCHA_ENABLED` 环境变量覆盖（生产可重新开启）
- 前端登录页不改
- 提交规范：Conventional Commits
