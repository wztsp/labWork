server:
  port: __PORT__
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true
      force-request: true
      force-response: true

spring:
  application:
    name: lis-monolith
  main:
    # 允许 Bean 定义覆盖 (解决不同模块间的同名 Bean 冲突)
    allow-bean-definition-overriding: true
  datasource:
    url: jdbc:postgresql://__DB_HOST__:__DB_PORT__/__DB_NAME__?characterEncoding=utf8
    username: __DB_USER__
    password: __DB_PASSWORD__
    driver-class-name: org.postgresql.Driver
  sql:
    init:
      mode: never
      schema-locations: classpath:schema.sql
      continue-on-error: true
  redis:
    host: __REDIS_HOST__
    port: __REDIS_PORT__
    database: 3
    timeout: 5000
  rabbitmq:
    host: __MQ_HOST__
    port: __MQ_PORT__
    username: __MQ_USER__
    password: __MQ_PASSWORD__
    virtual-host: __MQ_VHOST__
    listener:
      simple:
        # 离线包内置 RabbitMQ，生产环境开启消费者
        auto-startup: true
        missing-queues-fatal: false
        missing-exchange-fatal: false
        max-concurrent-consumers: 2
        prefetch: 1
        recovery-interval: 15000
        retry:
          enabled: true
          initial-interval: 3000
          max-attempts: 3
          multiplier: 2.0
          max-interval: 30000

# 启动时是否强制停止所有 MQ 监听器（开发机无 RabbitMQ 时用），部署环境必须为 false
lis:
  mq:
    stop-listeners-on-startup: false

# MyBatis Plus 配置 (统一)
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      id-type: auto

# === 业务配置 ===
report:
  pdf:
    # 相对安装目录（服务工作目录固定为安装目录）
    storage-path: ./data/pdf
  audit:
    auto-enabled: true
  critical:
    alert-queue: critical.alert
    confirm-timeout-seconds: 1800

# === MQ 配置 ===
mq:
  exchange: lis.exchange
  report:
    published-queue: report.published
    published-routing-key: report.published
    instrument-result-queue: lis.report.instrument-result
    instrument-result-routing-key: instrument.result.received
  result:
    routing-key: instrument.result.received
    unmatched-routing-key: result.unmatched
  critical:
    alert-queue: critical.alert
    alert-routing-key: critical.alert

# === 日志配置 ===
logging:
  level:
    com.lis: info
    org.springframework.web: info
  file:
    name: __LOG_DIR__/lis-backend.log

# === TCP 仪器通信配置 (TcpServer 组件) ===
tcp:
  server:
    enabled: true
    port: __TCP_PORT__
