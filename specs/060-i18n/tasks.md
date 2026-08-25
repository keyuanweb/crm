# Tasks: 国际化（i18n）模块

**Input**: Design documents from `/specs/060-i18n/`

**Prerequisites**: plan.md (required), spec.md (required), research.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: i18n 框架与资源

- [x] T001 [P] 安装 i18next + react-i18next in `frontend/package.json`
- [x] T002 [P] 创建 i18n 初始化（localStorage 持久化 + fallback zh-CN）in `frontend/src/i18n/index.ts`
- [x] T003 [P] 创建 zh-CN 资源（菜单/系统名/登录/首页）in `frontend/src/i18n/zh-CN.ts`
- [x] T004 [P] 创建 en 资源（同 key）in `frontend/src/i18n/en.ts`
- [x] T005 [P] main.tsx 引入 i18n 初始化 in `frontend/src/main.tsx`

**Checkpoint**: 框架就绪

---

## Phase 2: 核心文案资源化 (US1/US2)

**Goal**: 核心界面文案 i18n key 驱动 + 语言切换。

**Independent Test**: 切英文 → 核心文案变英文 → 刷新保持。

### 实现

- [x] T006 [US1] App.tsx 系统名/页脚 useTranslation in `frontend/src/App.tsx`
- [x] T007 [US1] 菜单分组与项文案 key 化（menu.*）in `frontend/src/App.tsx`
- [x] T008 [US1] 顶栏"使用地图"等文案 key 化 in `frontend/src/App.tsx`
- [x] T009 [US1] 登录页文案 key 化 in `frontend/src/pages/LoginPage.tsx`
- [x] T010 [US1] 首页问候文案 key 化 in `frontend/src/pages/stats/DashboardPage.tsx`
- [x] T011 [US1] 顶栏语言切换（Dropdown: 中文/English，持久化 + 即时生效）in `frontend/src/App.tsx`

**Checkpoint**: US1/2 可用——语言切换 + 核心资源化

---

## Phase 3: 收尾与验证

- [x] T012 Frontend typecheck / lint / build 通过
- [x] T013 手动验证：切换/刷新保持/回退
- [x] T014 更新 roadmap 060 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2**: 依赖 Phase 1
- **Phase 3**: 依赖全部完成

## Notes

- 默认 zh-CN；未翻译回退中文
- v1 核心界面（业务页面渐进迁移）
- 提交规范：Conventional Commits
