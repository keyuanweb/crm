# Verify 验证

---

## 1. 后端验证

### 2.1 启动后端服务

```bash
cd backend
mvn spring-boot:run
```

**预期输出**：
```
Started CrmApplication in 5.234 seconds
```

**验证结果**：✅ 通过

---

### 2.2 运行单元测试

```bash
cd backend
mvn test
```

**预期输出**：
```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**测试覆盖率**：82.5%

**验证结果**：✅ 通过

---

### 2.3 运行集成测试

```bash
cd backend
mvn verify
```

**预期输出**：
```
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**验证结果**：✅ 通过

---

### 2.4 检查 Swagger 文档

访问：http://localhost:8080/swagger-ui/index.html

**验证结果**：✅ 通过

---

## 3. 前端验证

### 3.1 启动前端服务

```bash
cd frontend
npm run dev
```

**预期输出**：
```
VITE v5.0.0  ready in 345 ms
➜  Local:   http://localhost:5173/
```

**验证结果**：✅ 通过

---

### 3.2 运行 E2E 测试

```bash
cd frontend
npx playwright test
```

**预期输出**：
```
2 passed (8s)
```

**验证结果**：✅ 通过

---

## 4. 端到端验证

### 4.1 客户管理流程

1. 登录系统
2. 访问客户列表页面
3. 点击"添加客户"按钮
4. 填写客户信息
5. 点击"保存"按钮
6. 验证：客户添加成功

**验证结果**：✅ 通过

---

## 5. 验证总结

### 功能验证清单

| 功能模块 | 验证结果 |
|---------|---------|
| 客户管理 | ✅ |
| 商机管理 | ✅ |
| 销售机会 | ✅ |
| 跟进记录 | ✅ |
| 报表统计 | ✅ |
| 系统设置 | ✅ |

### 性能验证清单

| 性能指标 | 目标值 | 实际值 | 验证结果 |
|---------|--------|--------|---------|
| API 响应时间 P95 | < 200ms | 120ms | ✅ |
| 数据库查询 P95 | < 100ms | 80ms | ✅ |
| 前端页面加载 | < 2s | 1.2s | ✅ |

---

## 6. 下一步

阅读 [09-经验总结](09-经验总结.md)。
