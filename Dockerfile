# ==================== 构建阶段 ====================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 缓存层）
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY backend/src ./src
RUN mvn clean package -DskipTests -Dspotless.check.skip=true

# ==================== 运行阶段 ====================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S crm && adduser -S crm -G crm

# 从构建阶段复制 jar
COPY --from=builder /app/target/crm-backend-0.1.0-SNAPSHOT.jar app.jar

# 设置权限
RUN chown -R crm:crm /app
USER crm

# 暴露端口
EXPOSE 8081

# JVM 优化参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
