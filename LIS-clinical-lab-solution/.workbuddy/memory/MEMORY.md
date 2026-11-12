# LIS 项目长期记忆

## 环境约束（关键，影响所有 Java 验证）
- **Maven 现已可修复使用**：`C:\maven\apache-maven-3.9.9` 的启动 jar 实际在 `boot/plexus-classworlds-2.8.0.jar`（不在 lib）。原 `bin/m2.conf` 的 `load ${maven.conf}/logging` 因 `conf/logging` 是目录导致 FileNotFound。修复：写 `bin/m2-nolog.conf` 去掉该行后启动：
  `cd C:/maven/apache-maven-3.9.9 && java -cp "boot/plexus-classworlds-2.8.0.jar" -Dclassworlds.conf="bin/m2-nolog.conf" -Dmaven.home="C:/maven/apache-maven-3.9.9" -Dmaven.multiModuleProjectDirectory="<项目根>" org.codehaus.plexus.classworlds.launcher.Launcher <args>`
  注意：`maven.home` 必须用 `C:/...` 驱动盘写法，不能用 `/c/...`（Windows Java 会把 `/c` 当成 `C:\c` 而找不到）。
- **但 `mvn test` 仍跑不了（离线缺测试依赖）**：`~/.m2` 缺 `junit-platform-launcher`、`surefire-junit-platform-provider`、`opentest4j`、`apiguardian-api`，且无外网（curl 不通）。已有 junit-jupiter-api/engine 5.10.1、mockito-core 5.11.0、mockito-junit-jupiter 5.11.0、postgresql-42.7.1。**生产代码编译验证用 `javac` 全量显式编译**（列出所有 .java，不用 `-sourcepath`，避 Lombok 假报错）。真实跑测须用户在联网标准 Java17+Maven 环境。
- **PostgreSQL 可用**：服务运行于 `localhost:5432`，`lis_db` 库已存在（66 表）。psql 在 `D:\Program Files\PostgreSQL\13\bin\psql.exe`。连接：`PGPASSWORD=lis_dev_2026 psql -h localhost -p 5432 -U lis_user -d lis_db`（密码默认 `lis_dev_2026`，或 env `DB_PASSWORD`）。建表用 `lis-system/docs/database/*.sql`：`init-schema.sql` 是裸 `CREATE TABLE`（**勿在已有库重跑**，会满屏"已存在"）；新增表用 `add-*.sql`（自带 `IF NOT EXISTS`，安全）。B-2 的 `add-technician-signin-print.sql` 已执行，建好 `lis_barcode_print_log`/`lis_station_signin`。

## 离线环境下"真跑测试"的兜底手段（已验证有效，强烈复用）
既然 `mvn test` 在本沙箱永远跑不了，验证逻辑正确性用**零依赖自检 Runner**：写一个普通 `public static void main` 类（如 `tools/selfcheck/B3B8SelfCheck.java`，放项目根 `tools/` 下**不要放进任何模块 src**，避免污染生产构建），只 import JDK 标准库 + 被测的纯 POJO/静态工具类，自己实现 `assertEq` 累计 pass/fail，末尾打印 `RESULT: total=N passed=X failed=Y` 并在失败时 `System.exit(1)`。
- 编译运行（git-bash，仓库根）：
  `CP="build/selfcheck/main-classes;$(cat build/selfcheck/cp.txt)"`
  `javac -encoding UTF-8 -d build/selfcheck/runner-classes -cp "$CP" tools/selfcheck/B3B8SelfCheck.java`
  `java -cp "build/selfcheck/runner-classes;$CP" B3B8SelfCheck`
- Windows classpath 分隔符是 `;`。控制台中文标题乱码是终端 GBK 显示问题，不影响内存中字符串比较与断言结果。
- 反射测试（如比对两个同名实体的字段契约）不需要 Spring，可以直接放进 Runner。
- 这套办法让"写了测试但没跑"变成"127 @Test 编译零错 + 179 断言真实全绿"，交付说服力完全不同。

## 项目结构约定
- 后端 `lis-system/lis-service/` 为 Maven 多模块（sample/report/qc/micro/reagent/hr/instrument/instrument-mgmt/base/system/stats/integration），MyBatis-Plus 无 XML（ServiceImpl+LambdaQueryWrapper）；前端 `lis-frontend`（Vue3+TS+Vite）。
- 设计/检查类文档落盘到项目根 `docs/`（如 `system_design.md`、`system-overall-check.md`、`docs/database/*.sql` 在 `lis-system/docs/database/`）。
- 后台管理系统独立部署在 `D:\traeitem\labWork\ALLcontrll`（与 LIS 主库分离）。

## 联调环境构建（2026-08-04 起，重要）
- **8080 旧单体（PID 28976）是提权进程杀不掉，且锁死原项目 target/classes**（覆盖写 Permission denied）→ 原项目 Maven 构建不可行。
- **副本构建方案**：`robocopy lis-system → D:\lis-system-build`（/XD target logs backups），在副本里 Maven 构建。命令模板（-f 指定根 pom，-pl 必须带 -am）：
  `cd C:/maven/apache-maven-3.9.9 && java -cp "boot/plexus-classworlds-2.8.0.jar" -Dclassworlds.conf="bin/m2-nolog.conf" -Dmaven.home="C:/maven/apache-maven-3.9.9" -Dmaven.multiModuleProjectDirectory="D:/lis-system-build" org.codehaus.plexus.classworlds.launcher.Launcher -f "D:/lis-system-build/pom.xml" package -pl lis-monolith -am -Dmaven.test.skip=true -Dmaven.compiler.useIncrementalCompilation=false`
- **改模块源码后的标准流程**：① 同步源码到副本（`cp -rf src/main/java/* 副本对应目录`）→ ② `mvn install -f 副本/<模块>/pom.xml -Dmaven.test.skip=true`（新模块 jar 进 ~/.m2）→ ③ **先删 `D:\lis-system-build\lis-monolith\target\lis-monolith-1.0.0.jar`（及 .jar.original）再 package monolith**——否则 maven-jar-plugin 增量跳过 repackage，BOOT-INF/lib 仍是旧依赖且 BUILD SUCCESS 无提示（踩过 2 次）→ ④ 验证：python zipfile 读 jar 内 `BOOT-INF/lib/lis-service-reagent-1.0.0.jar` 的 class 字节 grep 关键字。
- **单体启动**：`java -Dserver.port=8095 -Dspring.rabbitmq.listener.simple.auto-startup=false -jar D:/lis-system-build/lis-monolith/target/lis-monolith-1.0.0.jar`（**-D 必须在 -jar 前**）。启动约 29s。前端 vite proxy `/api → http://localhost:8095`。
- 手工 `java -cp` 跑 Spring Boot 不可行（编译 classpath 含 slf4j 1.7.36/spring-core 6.0.10 等版本冲突）——必须 fat jar。

## 代码风格约定
- 列表/集合判读等核心算法做成**零 Spring 依赖纯 POJO**（如 `ClsiSusceptibilityInterpreter`），便于脱离容器单测（`new` 即可）。
- 数值一律 `BigDecimal` + `compareTo`，禁用 `double/equals`。
- MyBatis-Plus `ServiceImpl` 子类单测用 `spy(new XxxServiceImpl(...))` + `doReturn(...).when(spy).list()` 桩化父类，并用 `TableInfoHelper.initTableInfo` 预注册 TableInfo 绕开 "can not find lambda cache" 异常。
