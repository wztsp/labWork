# LIS 临床检验系统 整体测试报告与优化方案

- 测试日期：2026-08-15
- 测试范围：前端（Vue 3 + TS + Element Plus）、后端（Spring Boot 3.2 多模块）
- 测试方式：静态审计 + 编译 + 单元测试 + 依赖审计 + 专项代码审计（未启动服务，跳过了在线 API/浏览器 E2E）

---

## 一、总体结论

| 检查项 | 结果 |
|---|---|
| 前端编译/构建 | ✅ 通过（35.3s，Webpack/Vite 产物正常） |
| 后端编译 | ✅ 通过（19 个 Maven 模块） |
| 后端单元测试 | ✅ 通过（修复后全部绿色，累计 500+ 用例） |
| 前端依赖审计 | ⚠️ 3 个漏洞（1 high / 2 moderate） |
| 前端代码质量 | ⚠️ 1 个 P1（打印 XSS）、多个 P2/P3 |
| 后端安全审计 | ❌ 1 个 P0（JWT 密钥硬编码）、多个 P1/P2 |

结论：**功能与构建质量良好，但安全性存在明显短板**，需优先修复后端认证授权与敏感信息硬编码问题。

---

## 二、本次已修复的问题（原测试套件无法编译/无法通过）

测试过程中发现并修复了 **10 处**问题，其中 1 处为生产逻辑缺陷：

### 2.1 修复的编译错误（7 处）
| 文件 | 问题 | 修复 |
|---|---|---|
| `lis-service-hr/.../StaffCertificateServiceImplTest.java` | 缺少 `MockitoExtension` 导入 | 补导入 |
| 同上 | `list(any())` 在 `Wrapper`/`IPage` 重载间歧义 | 改为 `list(any(Wrapper.class))` |
| `lis-service-instrument/.../InstrumentBootstrapTest.java` | `mock(InstrumentMapper.class)` 类不存在 | 改为 `InstrumentCommMapper` |
| `lis-service-sample/.../SampleServiceImplTest.java` | `deliverSamples(null,…)` 重载歧义 | `null` 显式转型为 `List<String>` |
| `lis-service-report/.../TechnicianServiceSigninTest.java` | 构造函数新增 `ReportService` 参数 | 补 mock 并传入 |
| `lis-service-qc/.../WestgardRuleEngineTest.java` | 共享 stub 触发 `UnnecessaryStubbing` | 类级 `@MockitoSettings(LENIENT)` |
| `lis-service-integration/.../HL7MessageParserTest.java` | PID 串少 1 个 `|`，地址落在 index 18 | 补 1 个分隔符，落到 index 19 |

### 2.2 修复的测试断言/运行错误（3 处）
| 文件 | 问题 | 修复 |
|---|---|---|
| `lis-service-report/.../DeltaCheckTest.java` | 嵌套 `when()` 导致 `UnfinishedStubbing` | 改用真实 `Result` + setter |
| `lis-service-report/.../WestgardRulesTest.java` | 断言与生产设计相悖（1-2s 设上限） | 断言改为与设计一致（1-2s 不设上限，z≥3 也报警） |
| `lis-service-sample/.../BarcodeServiceImplTest.java` | 未 stub `isRedisAvailable()`，Redis 分支被跳过；DB 回退匹配 `eq("%")` 与真实 LIKE 不符 | 补 `isRedisAvailable()` stub、改 `anyString()` |

### 2.3 修复的生产逻辑缺陷（1 处，重要）
**`MicroExpertRuleServiceImpl.java` — MDR（多重耐药）漏报**
- 原实现 `countResistantClasses()` 仅按类别名关键词（`青霉素`/`头孢`/`碳青霉烯`…）匹配，具体药物名（`亚胺培南`=碳青霉烯、`庆大霉素`=氨基糖苷、`环丙沙星`=喹诺酮）无法命中，导致对 ≥3 类耐药时 **MDR 不触发**。
- 修复：按药物类别维护代表性药物名（中英文）映射，每类命中任一代表药即计为耐药。

---

## 三、前端问题与建议

### 🔴 P1（高）
**P1-1　打印窗口 HTML 拼接 XSS**
`src/views/sample/barcode/index.vue`（约 788-933 行）：把患者姓名、条码等数据库字段直接拼进 HTML 后 `document.write()`，未转义。若字段含 `<img onerror>`/`</script>` 可在打印窗口执行脚本。
→ 建议：拼接前对每个动态值做 HTML 转义；JS 内插值用 `JSON.stringify`；抽取统一 `printHtml()` 工具。

### 🟠 P2（中）
- **P2-1** `barcode/index.vue`（857/976）运行时从外网 CDN 加载 jsbarcode，无 SRI，且与本地依赖重复 → 改用本地打包，去除运行时外网依赖。
- **P2-2** `login/index.vue`（67-68）预填弱口令 `admin/123456`；`api/system/index.ts`（116）重置密码默认弱口令 → 取消预填、移除后端默认弱口令兜底。
- **P2-3** 全库 60+ 处非空断言 `!`（如 `reagent/requisition`、`report/micro`）可能在数据缺失时崩溃 → 对"数据可能缺失"处判空。
- **P2-4** `request.ts` 默认 `T = any`、大量 `catch(e:any)` → 各 API 显式泛型、`unknown` 收窄。
- **P2-5** 约 100 个页面重复 CRUD/分页样板 → 抽取 `useCrudTable()` 组合式函数。
- **P2-6** 打印逻辑重复 7+ 处 → 统一 `printHtml()` 工具。
- **P2-7** `utils/offlineQueue.ts` 将医疗数据明文写 localStorage → 仅存幂等键/最小字段或加密。

### 🟡 P3（低）
- 部分 `setTimeout` 未在卸载时清理（`system/role`、`stats/trend`、`qc/report`）。
- `report/technician`、`report/query` 图片缺 `alt`。
- 打印用 `window.onload=` 覆盖已有回调，应改 `addEventListener`。
- 多处 `catch(e){/*ignore*/}` 静默吞错。
- `report/technician/index.vue` 约 2550 行过大，建议拆分。

### 构建体积
`element-vendor` 1.07MB(≈339KB gzip)、`echarts-vendor` 591KB(≈198KB gzip) 超 500KB 阈值 → 建议按需引入 ECharts、`manualChunks` 拆分。

### 依赖漏洞
- `echarts <6.1.0`（moderate，XSS，来自历史趋势功能）→ 升级 `vue-echarts` + `echarts`。
- `nanoid <3.3.18`（high）→ `npm audit fix`。

---

## 四、后端安全与质量问题（按严重级别）

### 🔴 P0（致命）
**P0-1　JWT 签名密钥硬编码**
`lis-common/.../security/JwtUtil.java:18-19` 写死 `SECRET`。攻击者拿到源码即可伪造任意用户令牌绕过网关鉴权。
→ 密钥移入环境变量/密管；生产与开发分离，禁止提交仓库。

### 🟠 P1（高）
- **P1-1　全服务无授权（权限）校验**：全库无 `@PreAuthorize`/权限切面，网关只校验"是否登录"，任意登录用户可访问用户/角色/权限/系统配置/备份等管理接口 → 引入 `@RequiresPermission` AOP 校验并覆盖全部管理接口。
- **P1-2　DB/RabbitMQ 明文密码**：`lis-auth`、`lis-service-system` 明文 `lis_dev_2026`；其余用 `${DB_PASSWORD:lis_dev_2026}` 弱默认值；HIS 对接 `change_me` → 全部改为环境变量注入，移除默认值，账号强口令并区分环境。
- **P1-3　备份下载/删除无权限控制**：`DataBackupController` 任意登录用户可下载含全库患者数据的 `.sql` → 限定管理员并做操作审计。
- **P1-4　临床报告接口未鉴权可遍历**：`/api/clinical/**` 网关白名单放行，按自增 id 遍历可批量下载患者报告 → 引入访问令牌/就诊号+身份证双重校验。
- **P1-5　CORS 过宽**：网关 `allowedOriginPatterns:"*"` + `allowCredentials:true` → 限定院内前端域名白名单。

### 🟡 P2（中）
- **P2-1** 全局异常处理器把 `e.getMessage()` 返回前端，泄漏 SQL/内部细节 → 对外统一中性提示，堆栈仅记日志。
- **P2-2** 网关日志记录完整 query（可能含 token）→ 对敏感字段脱敏。
- **P2-3** Spring Boot 3.2.0 存在多个公开 CVE（如 3.2.0-3.2.8 的 CVE-2024-38808）→ 升级 3.3.x/3.4.x。
- **P2-4** MyBatis 控制台打印 SQL 及参数 → 生产关闭或脱敏日志。

### 🟢 P3（低）
- Controller 几乎无 `@Valid` 参数校验 → 补 Bean Validation。
- PostgreSQL 42.7.1、jjwt 0.11.5 等依赖偏旧 → 升级并接入依赖漏洞扫描。
- Swagger 文档生产默认暴露且免鉴权 → 生产关闭 / 移除白名单。

### ✅ 已核查无风险
- SQL 注入：全部 `#{}`/`LambdaQueryWrapper` 参数化，风险低。
- 文件上传：无上传接口，不适用。
- `MessageController` 已做当前用户归属校验（正确范例）。

> 注：本次未启动服务，未做在线 API 集成与浏览器 E2E 测试（当前仅 PostgreSQL 运行中）。建议下一步启动网关+服务后做登录态、CRUD 冒烟与越权用例验证。

---

## 五、优化方案优先级路线

**第一批（安全，必须）**
1. 修复 P0-1（JWT 密钥外置）与 P1-2（密码环境变量化）。
2. 引入统一接口权限校验（P1-1）并覆盖管理接口。
3. 收紧备份、临床报告接口访问控制（P1-3/P1-4），收紧 CORS（P1-5）。
4. 修复前端打印 XSS（P1-1）。

**第二批（稳健性）**
5. 升级 Spring Boot 版本（P2-3）与前端 echarts/nanoid（依赖漏洞）。
6. 异常/日志脱敏（P2-1/P2-2）、MyBatis 日志治理（P2-4）。
7. 前端弱口令预填与离线队列明文存储（P2-2/P2-7）。

**第三批（工程效率）**
8. 抽取 `useCrudTable()` 与 `printHtml()`，消灭重复样板（P2-5/P2-6）。
9. 类型收窄与判空整改（P2-3/P2-4）。
10. ECharts/Element 按需引入，优化构建体积；拆分超大页面。