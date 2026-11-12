# =============================================================================
# LIS 临床检验系统 - 单体后端 (lis-monolith) Dockerfile
# 构建上下文: lis-system 目录 (docker-compose 中 context: ../lis-system)
# 产物: lis-monolith-fresh.jar (maven finalName)
# 端口: 8080 (HTTP) / 9100 (TCP 仪器通信)
# =============================================================================

# ---------- 阶段一: Maven 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
LABEL stage=builder

WORKDIR /build

# 复制整个 lis-system (含所有模块, 单体需要聚合编译)
COPY . .

# 打包单体 jar (跳过测试; -am 一并构建依赖模块)
RUN mvn -B clean package -DskipTests -pl lis-monolith -am

# ---------- 阶段二: JRE 运行 ----------
FROM eclipse-temurin:17-jre

LABEL maintainer="LIS Team"
LABEL description="LIS Clinical Lab Solution - Monolith Backend"

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# 复制可执行 jar
COPY --from=builder /build/lis-monolith/target/lis-monolith-fresh.jar /app/app.jar

# JVM 参数
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

# 暴露端口
EXPOSE 8080 9100

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]