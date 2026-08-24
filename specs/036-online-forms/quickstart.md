# 快速开始：在线表单

## 后端

1. Flyway V51：form + form_submission 表。
2. 实体/Mapper。
3. `FormService`：表单 CRUD + 发布 + 匿名提交（校验/防重复/频控/建线索）。
4. `FormController`（管理 + 公开提交）。
5. 测试：FormServiceTest + FormIT。

## 前端

1. `types/form.ts` + `formService.ts`。
2. `OnlineFormPage`（表单管理：字段配置器 + 外链 + 提交记录）。
3. 公开提交页 `/f/:id`。
4. 路由注册。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：建表单 → 外链访问提交 → 线索池出现（来源=表单）→ 重复提交拦截。
