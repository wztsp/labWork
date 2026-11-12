# =============================================================================
# LIS 临床检验系统 Java 服务 Dockerfile 模板
# 多阶段构建: 1) Maven 构建  2) JRE 运行
# 使用方法:
#   docker build -t lis-service:latest -f Dockerfile ../..
#   docker run -d -p 8081:8081 --name lis-service lis-service:latest
# =============================================================================

# ---------- 阶段一: 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom 利用 Docker 层缓存加速依赖下载
COPY pom.xml ./
COPY lis-common/pom.xml        ./lis-common/pom.xml
COPY lis-service/pom.xml       ./lis-service/pom.xml

# 预下载依赖 (离线构建加速)
RUN mvn -B dependency:go-offline -Dmaven.test.skip=true || true

# 复制源代码
COPY lis-common/src  ./lis-common/src
COPY lis-service/src ./lis-service/src

# 构建: 跳过测试, 输出可执行 jar
RUN mvn -B clean package -DskipTests


# ---------- 阶段二: 运行 ----------
FROM eclipse-temurin:17-jre

LABEL maintainer="LIS Team"
LABEL description="LIS Clinical Lab Solution - Java Service"

WORKDIR /app

# 时区设置 (Asia/Shanghai)
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 复制构建产物
COPY --from=builder /build/lis-service/target/*.jar /app/app.jar

# JVM 参数 (容器内对内存感知更友好)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

# 暴露服务端口 (按实际微服务端口调整)
EXPOSE 8081

# 健康检查 (按需调整 actuator 路径)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fs http://localhost:8081/actuator/health || exit 1

# 启动
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
