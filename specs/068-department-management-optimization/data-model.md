# Data Model: 部门管理优化

## 实体关系图

```
Department (部门)
├── id: BIGINT (主键)
├── name: VARCHAR(50) (部门名称，相同上级下唯一)
├── parent_id: BIGINT (上级部门，自引用外键)
├── description: VARCHAR(500) (部门描述，可选)
├── sort_order: INT (排序号，默认 0)
├── created_by: BIGINT (创建人 ID，外键关联 user.id)
├── created_at: DATETIME (创建时间)
├── updated_at: DATETIME (更新时间)
├── deleted: TINYINT (逻辑删除，0=未删除，1=已删除)
├── version: INT (乐观锁版本号)
└── children: Department[] (子部门，计算字段)
```

## 字段说明

### Department 表

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| id | BIGINT | 是 | 自增 | 主键 |
| name | VARCHAR(50) | 是 | - | 部门名称，相同 parent_id 下唯一 |
| parent_id | BIGINT | 否 | NULL | 上级部门 ID，NULL 表示顶级部门 |
| description | VARCHAR(500) | 否 | NULL | 部门描述 |
| sort_order | INT | 否 | 0 | 排序号，值越小越靠前 |
| created_by | BIGINT | 是 | - | 创建人 ID |
| created_at | DATETIME | 是 | 当前时间 | 创建时间 |
| updated_at | DATETIME | 是 | 当前时间 | 更新时间 |
| deleted | TINYINT | 是 | 0 | 逻辑删除标记 |
| version | INT | 是 | 0 | 乐观锁版本号 |

## 索引设计

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PRIMARY | id | 主键 | 主键索引 |
| idx_parent_id | parent_id | 普通索引 | 查询子部门 |
| idx_name_parent | name, parent_id | 联合唯一索引 | 部门名称唯一性约束 |
| idx_created_by | created_by | 普通索引 | 查询创建人的部门 |

## 约束

### 业务约束

1. **部门名称唯一性**: 相同 parent_id 下，name 必须唯一
2. **防环约束**: parent_id 不能是自身的子孙部门
3. **最大深度**: 部门树最大深度为 5 层
4. **删除防护**: 有子部门或成员的部门不能删除

### 数据库约束

```sql
-- 部门名称唯一性约束
ALTER TABLE department ADD CONSTRAINT uk_name_parent UNIQUE (name, parent_id);

-- 上级部门外键约束（可选，使用级联更新）
ALTER TABLE department ADD CONSTRAINT fk_parent_id 
  FOREIGN KEY (parent_id) REFERENCES department(id) ON DELETE SET NULL;
```

## 计算字段

| 字段 | 类型 | 说明 | 计算方式 |
|------|------|------|----------|
| member_count | INT | 成员数量 | `SELECT COUNT(*) FROM user WHERE department_id = department.id AND deleted = 0` |
| child_count | INT | 子部门数量 | `SELECT COUNT(*) FROM department WHERE parent_id = department.id AND deleted = 0` |

## API 响应扩展

### DepartmentResponse 新增字段

```java
public class DepartmentResponse {
  private Long id;
  private String name;
  private Long parentId;
  private String description;        // 新增
  private Integer sortOrder;         // 新增
  private Integer version;
  private LocalDateTime createdAt;
  private String createdBy;          // 新增（用户名，非 ID）
  private Integer memberCount;       // 新增（计算字段）
  private Integer childCount;        // 新增（计算字段）
  private List<DepartmentResponse> children;
}
```

### DepartmentRequest 新增字段

```java
public class DepartmentRequest {
  @NotBlank(message = "部门名称不能为空")
  @Size(max = 50, message = "名称不能超过 50 字")
  private String name;

  private Long parentId;

  @Size(max = 500, message = "描述不能超过 500 字")
  private String description;        // 新增

  private Integer sortOrder;         // 新增

  private Integer version;
}
```

## 前端类型扩展

### Department 类型新增字段

```typescript
export interface Department {
  id: number
  name: string
  parentId?: number
  description?: string        // 新增
  sortOrder?: number          // 新增
  version: number
  createdAt?: string
  createdBy?: string          // 新增（用户名）
  memberCount?: number        // 新增
  childCount?: number         // 新增
  children: Department[]
}
```
