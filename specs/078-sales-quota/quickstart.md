# Quickstart: 销售配额分解（Sales Quota Decomposition）

**创建日期**: 2026-08-27

## 验证场景

### 前置条件

1. 后端服务运行在 `http://localhost:8081`
2. 前端服务运行在 `http://localhost:5173`
3. 用户已登录，具有 admin 或销售管理员角色
4. MySQL 8.0 运行中，Flyway 迁移已执行

### 场景 1: 创建年度配额并分解到季度

**步骤**:

1. 调用创建配额 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/sales-quota \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "year": 2026,
       "quarter": null,
       "teamId": null,
       "userId": null,
       "amount": 10000000.00,
       "periodStart": "2026-01-01",
       "periodEnd": "2026-12-31"
     }'
   ```

2. 验证响应 201，返回配额 ID（假设为 1）

3. 调用分解配额 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/sales-quota/1/breakdown \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "breakdowns": [
         {"quarter": 1, "teamId": null, "amount": 2500000.00},
         {"quarter": 2, "teamId": null, "amount": 2500000.00},
         {"quarter": 3, "teamId": null, "amount": 2500000.00},
         {"quarter": 4, "teamId": null, "amount": 2500000.00}
       ]
     }'
   ```

4. 验证响应 201，返回 4 个季度配额

5. 调用获取配额列表 API 验证分解结果：
   ```bash
   curl http://localhost:8081/api/v1/sales-quota?year=2026 \
     -H "Authorization: Bearer <token>"
   ```

**预期结果**:
- 年度配额 amount = 10,000,000
- 4 个季度配额 amount 总和 = 10,000,000
- 分解总和校验通过

### 场景 2: 分解季度配额到团队/个人

**步骤**:

1. 获取季度 1 的配额 ID（假设为 2）

2. 调用分解配额 API：
   ```bash
   curl -X POST http://localhost:8081/api/v1/sales-quota/2/breakdown \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "breakdowns": [
         {"quarter": null, "teamId": 1, "amount": 1250000.00},
         {"quarter": null, "teamId": 2, "amount": 1250000.00}
       ]
     }'
   ```

3. 验证响应 201

4. 调用获取配额分解 API 验证：
   ```bash
   curl http://localhost:8081/api/v1/sales-quota/2/breakdown \
     -H "Authorization: Bearer <token>"
   ```

**预期结果**:
- 季度 1 配额分解到团队 1 和团队 2
- 团队配额 amount 总和 = 2,500,000

### 场景 3: 达成率计算

**步骤**:

1. 确保有一个 ACTIVE 状态的配额（ID 为 1）

2. 创建商机并成交（金额 6,000,000，close_date 在配额期间内）

3. 调用获取达成率 API：
   ```bash
   curl http://localhost:8081/api/v1/sales-quota/1/achievement \
     -H "Authorization: Bearer <token>"
   ```

4. 验证响应：
   ```json
   {
     "quotaAmount": 10000000.00,
     "actualAmount": 6000000.00,
     "achievementRate": 60.00,
     "status": "AT_RISK"
   }
   ```

**预期结果**:
- 达成率 = 6,000,000 / 10,000,000 = 60%
- 状态 = AT_RISK（60-80%）

### 场景 4: 配额调整与版本管理

**步骤**:

1. 调用更新配额 API：
   ```bash
   curl -X PUT http://localhost:8081/api/v1/sales-quota/1 \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "amount": 12000000.00,
       "changeReason": "Q1 业绩超预期，追加目标"
     }'
   ```

2. 验证响应 200，amount = 12,000,000

3. 调用获取版本历史 API：
   ```bash
   curl http://localhost:8081/api/v1/sales-quota/1/versions \
     -H "Authorization: Bearer <token>"
   ```

4. 验证响应包含版本记录：
   ```json
   [
     {
       "oldAmount": 10000000.00,
       "newAmount": 12000000.00,
       "changeReason": "Q1 业绩超预期，追加目标",
       "versionNumber": 1
     }
   ]
   ```

**预期结果**:
- 配额金额更新为 12,000,000
- 版本历史保留变更记录
- 达成率基于新配额重新计算

### 场景 5: 分解总和校验（错误场景）

**步骤**:

1. 调用分解配额 API，分解总和与上级配额不一致：
   ```bash
   curl -X POST http://localhost:8081/api/v1/sales-quota/1/breakdown \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{
       "breakdowns": [
         {"quarter": 1, "teamId": null, "amount": 2000000.00},
         {"quarter": 2, "teamId": null, "amount": 2000000.00}
       ]
     }'
   ```

2. 验证响应 400，错误信息包含"分解总和与上级配额不一致"

**预期结果**:
- 请求被拒绝
- 错误信息明确提示偏差金额

## 前端验证

### 步骤

1. 访问 `http://localhost:5173/quotas`（配额管理页面）

2. 验证页面布局：
   - 顶部统计卡片（总配额、总实际、总达成率）
   - ProTable 列表（配额列表）
   - 操作按钮（创建、分解、调整、导出）

3. 点击"创建配额"，验证表单：
   - 年份选择器
   - 季度选择器（年度配额时隐藏）
   - 团队/用户选择器
   - 金额输入框
   - 期间选择器

4. 创建配额后，点击"分解"，验证分解流程：
   - 选择分解目标（季度/团队/个人）
   - 输入分解金额
   - 实时显示分解总和与上级配额的偏差
   - 偏差为 0 时允许保存

5. 查看达成率页面，验证：
   - 达成率计算正确
   - 低达成预警（<60% 标红，<80% 标黄）
   - 版本历史可追溯

## 测试命令

### 后端单元测试
```bash
cd backend
mvn test -Dtest=SalesQuotaServiceTest
mvn test -Dtest=SalesQuotaControllerTest
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
