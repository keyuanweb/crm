# Constitution 项目宪法

---

## 1. 核心原则

### 原则 1：代码质量优先
- 所有公共方法必须有完整的 Javadoc
- Service 层方法必须有单元测试（覆盖率 ≥ 80%）
- Controller 层方法必须有集成测试
- 严禁裸 try-catch，异常必须由 GlobalExceptionHandler 统一处理
- 方法长度不超过 50 行，类长度不超过 500 行

### 原则 2：RESTful API 设计规范
- 所有 API 必须遵循 RESTful 风格
- 统一响应格式：`{"code": 200, "message": "success", "data": {}}`
- 统一错误响应格式：`{"code": 400, "message": "参数错误", "detail": "详细信息"}`
- 所有请求体使用 DTO，禁止直接使用 Entity
- Controller 层使用 @Valid 注解进行参数校验

### 原则 3：数据库设计规范
- 表名使用小写 + 下划线（如 crm_customer）
- 所有表必须有 created_at 和 updated_at 字段
- 所有表必须有逻辑删除字段 deleted（类型：TINYINT，默认 0）
- 主键统一使用 id（类型：BIGINT，自增或雪花算法）
- 外键使用 user_id、created_by、customer_id 等下划线命名
- 所有字段必须有注释
- 索引设计必须基于查询场景

### 原则 4：Redis 缓存策略
- 用户信息缓存（TTL 2 小时）：key 格式 `crm:user:{userId}`
- 统计报表缓存（TTL 1 小时）：key 格式 `crm:report:{type}:{date}`
- 客户列表缓存（TTL 30 分钟）：key 格式 `crm:customer:list:{page}:{size}`
- 缓存更新策略：先更新数据库，再删除缓存
- 缓存穿透防护：缓存空值（TTL 5 分钟）

### 原则 5：前端开发规范
- TypeScript 严格模式
- 所有组件必须有 TypeScript 类型定义
- 状态管理使用 React Query + Zustand
- 样式使用 Tailwind CSS
- API 请求使用 Axios，统一封装在 services/api.ts
- 路由使用 React Router v6

---

## 2. 技术栈约束

### 后端技术栈

| 技术 | 版本 | 约束 |
|------|------|------|
| Spring Boot | 3.2.0 | 必须使用 3.x 版本 |
| MySQL | 8.0 | 必须使用 8.0+ |
| MyBatis-Plus | 3.5.5 | 必须使用 3.5.x 版本 |
| Redis | 7.0 | 必须使用 7.0+ |
| Lombok | 1.18.30 | 必须使用 1.18.x 版本 |

### 前端技术栈

| 技术 | 版本 | 约束 |
|------|------|------|
| React | 18.2.0 | 必须使用 18.x 版本 |
| TypeScript | 5.0+ | 必须使用 5.x 版本 |
| Vite | 5.0+ | 必须使用 5.x 版本 |
| Tailwind CSS | 3.4.0 | 必须使用 3.4.x 版本 |

---

## 3. 验收标准

### 功能验收标准
- 所有用户故事必须有验收条件
- 验收条件必须可测试（Given-When-Then 格式）
- 所有验收条件必须有测试用例

### 性能验收标准
- API 响应时间 P95 < 200ms
- 数据库查询 P95 < 100ms
- 前端页面加载时间 < 1s

---

## 4. 版本约束

### API 版本
- API 路径使用版本号：/api/v1/customers
- 向后兼容原则：新版本不修改旧版本接口
- 旧版本支持至少 6 个月

---

## 5. 违例处理流程

### 自动检查
- 单元测试覆盖率 < 80% → CI 自动标记为失败
- 裸 try-catch → SonarQube 自动标记
- API 格式不一致 → Custom Checkstyle 规则

### 手动审查
- 无权限控制 → 安全团队人工审查
- 缺少 Javadoc → Code Review 时检查

---

## 6. 下一步

阅读 [03-Specify功能规范](03-Specify功能规范.md)。
