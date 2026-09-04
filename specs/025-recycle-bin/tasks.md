# Tasks: 回收站与批量恢复


**Input**: Design documents from `/specs/025-recycle-bin/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/recycle-bin.md



**Tests**: 单元测试覆盖 RecycleBinService 列表/恢复/冲突/权限逻辑 + 集成测试验证删除→回收站→恢复全流程


## Phase 1: 基础设施搭建



- [x] T001 [P] 扩展 4 个 Mapper（Customer/Lead/Contact/Opportunity）添加自定义注解 SQL 绕过 @TableLogic 逻辑删除过滤：`selectDeletedByUser`（@Select 查 deleted=1 AND created_by）、`selectDeletedAll`（@Select 查 deleted=1）、`restoreById`（@Update deleted=0）、`purgeById`（@Delete 物理删除）

## Phase 2: 后端测试


- [x] T002 [P] [US1] 创建 `backend/src/test/java/com/crm/service/RecycleBinServiceTest.java` 编写 RecycleBinService 单元测试，覆盖列表查询（4 实体合并分页）、类型筛选 + 关键字搜索、批量恢复、彻底删除、SALES 数据权限过滤（created_by=当前用户）、RecycleBinService 异常场景（恢复时 name+company 唯一性冲突跳过）

- [x] T003 [P] [US1] 创建 `backend/src/test/java/com/crm/integration/RecycleBinIT.java` 编写集成测试，验证删除→回收站查询→恢复全流程，覆盖 GET /recycle-bin、POST restore、POST purge，验证审计日志记录


## Phase 3: 后端实现


- [x] T004 [US1] 创建 `dto/recycle/RecycleItem.java` 定义 RecycleItem DTO（type/id/name/deletedAt/deletedBy 字段）

- [x] T005 [US1] 创建 `service/RecycleBinService.java` 实现 RecycleBinService，查询 4 个核心实体（客户/线索/联系人/商机）已删除记录 + 内存合并分页、支持类型筛选 + 关键字搜索、批量恢复（deleted=0）+ 彻底删除（物理删）、SALES 用户仅本人记录（created_by）+ ADMIN 全量、处理恢复时 name+company 唯一性冲突跳过逻辑、记录审计日志，依赖 T001/T004

- [x] T006 [US1] 创建 `controller/RecycleBinController.java` 实现 RecycleBinController，提供 GET /recycle-bin、POST /recycle-bin/restore、POST /recycle-bin/purge 端点，添加 PreAuthorize ADMIN 权限控制，依赖 T005


## Phase 4: 前端实现


- [x] T007 [P] [US1] 创建 `types/recycle.ts` + `services/recycleService.ts` 定义 RecycleItem 类型，实现 fetchRecycleBin/restoreItems/purgeItems 服务方法

- [x] T008 [US1] 创建 `pages/recycle/RecycleBinPage.tsx` 实现回收站页面，包含：Table 展示 + 实体类型 Tag 筛选（客户/线索/联系人/商机）+ 关键字搜索 + 批量勾选 + 批量恢复/彻底删除操作 + 空状态提示 + "恢复成功/彻底删除成功" antd Modal 确认 + 权限控制（SALES 仅本人），注册路由到 `App.tsx` 系统管理分组，依赖 T007


## Phase 5: 质量检查

- [x] T009 运行 `mvn test` 验证后端测试通过：RecycleBinServiceTest + RecycleBinIT 全部通过

- [x] T010 运行 `pnpm run typecheck` + `lint` + `test` 验证前端代码质量

- [x] T011 [P] 人工审查：代码结构、异常处理、审计日志、权限控制、数据一致性


## Dependencies & Execution Order


- T001 是后续所有任务的基础依赖

- T002/T003 并行执行，依赖 T001

- T004 无依赖，可独立执行

- T005 依赖 T001/T004

- T006 依赖 T005

- T007/T008 并行执行，依赖后端 API 就绪

- Phase 5 在所有开发任务完成后执行


## Notes


- 绕过 MyBatis-Plus @TableLogic 逻辑删除过滤：使用自定义注解 SQL（@Select/@Update/@Delete）

- 恢复 = deleted=0（逻辑删除逆向）；彻底删除 = 物理删除（绕过逻辑删除）

- SALES 用户仅本人删除记录（created_by=当前用户）；ADMIN 全量

- 恢复唯一性冲突：仅跳过冲突记录（如 name+company 重复），其余继续
