# Quickstart: 数据保留策略（Data Retention Policy）

**创建日期**: 2026-08-27

## 验证场景

### 前置条件

1. 后端服务运行在 `http://localhost:8081`
2. 前端服务运行在 `http://localhost:5173`
3. 用户已登录，具有 admin 权限
4. MySQL 8.0 运行中，Flyway 迁移已执行

### 场景 1: 创建数据保留策略

**步骤**:

1. 调用创建数据保留策略 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/data-retention/policies \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "entityType": "CUSTOMER",
       "retentionPeriodYears": 5,
       "retentionPeriodMonths": 0,
       "retentionPeriodDays": 0,
       "archiveAction": "ARCHIVE"
     }'
   ```

2. 验证响应 201，返回策略 ID（假设为 1）

3. 调用获取策略列表 API 验证创建结果：
   ```bash
   curl http://localhost:8081/api/v1/data-retention/policies \
     -H "Authorization: Bearer <token>"
   ```

**预期结果**:
- 策略创建成功，状态 = ACTIVE
- 策略在列表中显示

### 场景 2: 手动执行归档

**步骤**:

1. 调用手动执行归档 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/data-retention/policies/1/execute-now \
     -H "Authorization: Bearer <token>"
   ```

2. 验证响应 202，返回执行 ID

3. 等待 1-2 分钟后，调用获取执行历史 API：
   ```bash
   curl http://localhost:8081/api/v1/data-retention/policies/1/executions \
     -H "Authorization: Bearer <token>"
   ```

4. 验证执行记录：
   ```json
   {
     "status": "SUCCESS",
     "recordsProcessed": 150
   }
   ```

**预期结果**:
- 归档任务执行成功
- 执行记录显示处理 150 条记录
- 归档日志可追溯

### 场景 3: 合规导出

**步骤**:

1. 调用合规导出 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/data-retention/export \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "entityType": "CUSTOMER",
       "periodStart": "2024-01-01",
       "periodEnd": "2024-12-31",
       "format": "CSV"
     }'
   ```

2. 验证响应 202，返回导出 ID

3. 调用获取导出文件 API：
   ```bash
   curl -O http://localhost:8081/api/v1/data-retention/export/export-123/download \
     -H "Authorization: Bearer <token>"
   ```

4. 验证文件下载（customer_export_2024.csv）

**预期结果**:
- 导出文件生成
- 文件包含 2024 年所有客户数据
- 文件格式为 CSV

### 场景 4: 策略变更审计

**步骤**:

1. 调用更新策略 API：
   ```bash
   curl -X PUT http://localhost:8081/api/v1/data-retention/policies/1 \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "retentionPeriodYears": 3
     }'
   ```

2. 验证响应 200，retentionPeriodYears = 3

3. 调用获取策略详情 API 验证审计日志：
   ```bash
   curl http://localhost:8081/api/v1/data-retention/policies/1 \
     -H "Authorization: Bearer <token>"
   ```

**预期结果**:
- 策略更新成功
- 审计日志记录变更（变更人、变更前后值、变更时间）

## 前端验证

### 步骤

1. 访问 `http://localhost:5173/settings/data-retention`（数据保留策略页面）

2. 验证页面布局：
   - 顶部统计卡片（活跃策略数、本月归档记录数）
   - ProTable 列表（策略列表）
   - 操作按钮（创建、暂停/恢复、删除、立即执行）

3. 点击"创建策略"，验证表单：
   - 实体选择器（客户/商机/合同/订单/发票/跟进记录/审计日志）
   - 保留期限输入（年/月/日）
   - 归档方式选择器（归档到归档表/直接删除）

4. 创建策略后，验证：
   - 策略在列表中显示
   - 状态显示正确（活跃/已暂停）

5. 点击"立即执行"，验证：
   - 执行状态变为"执行中"
   - 执行完成后更新为"成功/失败"
   - 执行历史可追溯

## 测试命令

### 后端单元测试
```bash
cd backend
mvn test -Dtest=DataRetentionPolicyServiceTest
mvn test -Dtest=DataRetentionPolicyControllerTest
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
