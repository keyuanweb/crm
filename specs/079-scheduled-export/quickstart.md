# Quickstart: 定时导出订阅（Scheduled Export Subscription）

**创建日期**: 2026-08-27

## 验证场景

### 前置条件

1. 后端服务运行在 `http://localhost:8081`
2. 前端服务运行在 `http://localhost:5173`
3. 用户已登录，具有 admin 或数据导出角色
4. MySQL 8.0 运行中，Flyway 迁移已执行
5. 邮件服务配置正确（SMTP 服务器可用）

### 场景 1: 创建定时导出任务

**步骤**:

1. 调用创建定时导出任务 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/scheduled-exports \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "entityType": "CUSTOMER",
       "filterConditions": {
         "status": "ACTIVE"
       },
       "exportFormat": "XLSX",
       "executionCron": "0 0 9 * * MON"
     }'
   ```

2. 验证响应 201，返回任务 ID（假设为 1）和下次执行时间

3. 调用获取任务列表 API 验证创建结果：
   ```bash
   curl http://localhost:8081/api/v1/scheduled-exports \
     -H "Authorization: Bearer <token>"
   ```

**预期结果**:
- 任务创建成功，状态 = ACTIVE
- 下次执行时间 = 下周一 9:00
- 任务在列表中显示

### 场景 2: 手动立即执行

**步骤**:

1. 调用手动执行 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/scheduled-exports/1/execute-now \
     -H "Authorization: Bearer <token>"
   ```

2. 验证响应 202，返回执行 ID

3. 等待 1-2 分钟后，调用获取执行历史 API：
   ```bash
   curl http://localhost:8081/api/v1/scheduled-exports/1/executions \
     -H "Authorization: Bearer <token>"
   ```

4. 验证执行记录：
   ```json
   {
     "status": "EMAIL_SENT",
     "filePath": "/tmp/exports/customer_export_20260901.xlsx",
     "fileSizeBytes": 102400,
     "emailSent": true
   }
   ```

**预期结果**:
- 任务立即执行
- 生成导出文件
- 邮件发送成功
- 执行历史显示成功

### 场景 3: 暂停/恢复任务

**步骤**:

1. 调用暂停任务 API：
   ```bash
   curl -X PUT http://localhost:8081/api/v1/scheduled-exports/1/status \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"status": "SUSPENDED"}'
   ```

2. 验证响应 200，状态 = SUSPENDED

3. 调用恢复任务 API：
   ```bash
   curl -X PUT http://localhost:8081/api/v1/scheduled-exports/1/status \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"status": "ACTIVE"}'
   ```

4. 验证响应 200，状态 = ACTIVE，下次执行时间重新计算

**预期结果**:
- 暂停后跳过下次执行
- 恢复后下次执行时间重新计算

### 场景 4: 执行失败处理

**步骤**:

1. 创建定时导出任务，筛选条件导致数据量过大（>10 万行）：
   ```bash
   curl -X POST http://localhost:8081/api/v1/scheduled-exports \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "entityType": "CUSTOMER",
       "filterConditions": {},
       "exportFormat": "XLSX",
       "executionCron": "0 0 9 * * MON"
     }'
   ```

2. 手动执行触发失败：
   ```bash
   curl -X POST http://localhost:8081/api/v1/scheduled-exports/2/execute-now \
     -H "Authorization: Bearer <token>"
   ```

3. 验证执行历史：
   ```json
   {
     "status": "FAILED",
     "errorMessage": "导出数据量超过限制（10 万行）"
   }
   ```

**预期结果**:
- 执行失败，状态 = FAILED
- 错误信息明确提示原因
- 不发送邮件

## 前端验证

### 步骤

1. 访问 `http://localhost:5173/exports/scheduled`（定时导出页面）

2. 验证页面布局：
   - 顶部统计卡片（活跃任务数、本月执行成功数）
   - ProTable 列表（定时任务列表）
   - 操作按钮（创建、暂停/恢复、删除、立即执行）

3. 点击"创建定时导出"，验证表单：
   - 实体选择器（客户/商机/合同/订单/发票）
   - 筛选条件表单（根据实体动态生成）
   - 导出格式选择器（CSV/Excel）
   - 执行周期选择器（每日/每周/每月，指定时间）
   - Cron 表达式预览

4. 创建任务后，验证：
   - 任务在列表中显示
   - 下次执行时间正确
   - 状态显示正确（活跃/已暂停）

5. 点击"立即执行"，验证：
   - 执行状态变为"执行中"
   - 执行完成后更新为"成功/失败"
   - 执行历史可追溯

## 测试命令

### 后端单元测试
```bash
cd backend
mvn test -Dtest=ScheduledExportServiceTest
mvn test -Dtest=ScheduledExportControllerTest
```

### 后端集成测试
```bash
cd backend
mvn verify
```

### 前端类型检查
```bash
cd frontend
npx tsc --noEmit
```

### 前端构建
```bash
cd frontend
npx vite build
```
