server:
  port: __PORT__
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

spring:
  application:
    name: lis-reagent
  datasource:
    url: jdbc:postgresql://__DB_HOST__:__DB_PORT__/__DB_NAME__?characterEncoding=utf8
    username: __DB_USER__
    password: __DB_PASSWORD__
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      id-type: auto

reagent:
  expiry:
    near-expiry-days: 30
    long-term-expiry-days: 90

logging:
  level:
    root: info
    com.lis.reagent: info
  file:
    name: __LOG_DIR__/lis-reagent.log
