# Implement 实现

---

## 1. 后端实现

### T1: 项目搭建

**实现内容**：

#### pom.xml

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.5.5</version>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

#### application.yml

```yaml
spring:
  application:
    name: crm-system
  datasource:
    url: jdbc:mysql://localhost:3306/crm_db?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root123456
  redis:
    host: localhost
    port: 6379

server:
  port: 8080
```

#### CrmApplication.java

```java
@SpringBootApplication
@MapperScan("com.crm.mapper")
public class CrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
```

**验收结果**：✅ 通过

---

## 2. 前端实现

### T12: 项目初始化

**实现内容**：

#### package.json

```json
{
  "dependencies": {
    "react": "^18.2.0",
    "react-router-dom": "^6.20.0",
    "axios": "^1.6.0",
    "tailwindcss": "^3.4.0"
  }
}
```

#### vite.config.ts

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
```

**验收结果**：✅ 通过

---

## 3. 下一步

继续实现 T3-T11（后端）和 T13-T23（前端）。

阅读 [08-Verify验证](08-Verify验证.md)。
