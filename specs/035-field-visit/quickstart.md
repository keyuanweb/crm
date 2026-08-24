# 快速开始：外勤拜访

## 后端

1. Flyway V50：field_visit 表。
2. 实体/Mapper。
3. `FieldVisitService`：计划 CRUD + 签到（防重复 + 坐标/时间）+ 小结转跟进 + 统计。
4. `FieldVisitController`。
5. 测试：FieldVisitServiceTest + FieldVisitIT。

## 前端

1. `types/visit.ts` + `visitService.ts`。
2. `VisitListPage`（拜访列表 + 统计卡 + 签到/取消）。
3. PWA 移动端签到（Geolocation API）。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：建拜访计划 → 签到（定位）→ 小结 → 客户跟进时间线出现拜访记录 → 统计。
