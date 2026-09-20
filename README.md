# 砚知 · 博客系统（后端）

> `inkos` = **Ink** + **OS**。

一个**分层架构的 Java 后端**，技术栈为 **Java 25 + Spring Boot 4.1.1**，数据库为 **MySQL 8**。
不是空壳：登录鉴权、RBAC 权限、文章 / 分类 / 标签 / 语句、**评论树与互动（点赞收藏）**、
**检索**、**可观测性与接口限流**全部是能跑通的真实代码，建表与种子数据在首次启动时自动完成。

---

## 一、快速开始

### 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **25** | 编译目标为 `release 25`，**必须 JDK 25+**（用 21 编译会直接失败） |
| MySQL | **8.0+** | 唯一的外部依赖；建库语句见下 |
| Maven | 3.9+ | 可选 —— 仓库自带 Maven Wrapper |

### 准备数据库

建库只做一次（跑测试还需要第二个库）：

```sql
CREATE DATABASE inkos      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE inkos_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

表结构与种子数据由应用在首次启动时自动创建（`CREATE TABLE IF NOT EXISTS`，可反复执行），
**不需要手工执行 `schema.sql`**。

数据库口令有两个来源，任选其一：

```bash
# A. 环境变量 —— CI 或临时运行
DB_PASSWORD=你的口令 ./mvnw -pl inkos-admin -am spring-boot:run
```

```yaml
# B. 本机个人配置文件（开发推荐）—— 该文件已被 .gitignore 忽略，clone 后自行创建
#    inkos-admin/src/main/resources/application-local.yml
spring:
  datasource:
    username: root
    password: 你的口令
```

连接信息默认 `127.0.0.1:3306/inkos`、用户 `root`，可用
`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` 覆盖。

### 启动

```bash
cd inkos-blog

# 编译（跳过测试）
./mvnw clean package -DskipTests

# 运行
java -jar inkos-admin/target/inkos-blog.jar
```

或者直接：

```bash
./mvnw -pl inkos-admin -am spring-boot:run
```

> Windows 下用 `mvnw.cmd` 代替 `./mvnw`。本机已装 Maven 3.9+ 时，`mvn` 与 `./mvnw` 等价 ——
> wrapper 的价值在于**锁定构建工具版本**，不依赖机器上装了什么。

> **注意 `mvnw` 依赖 `JAVA_HOME`**：wrapper 脚本不会像 `mvn` 那样回退到 PATH 上的 `java`，
> 未设置时直接报 `JAVA_HOME not found in your environment`。先确认：
>
> ```bash
> echo $JAVA_HOME          # Windows: echo %JAVA_HOME%
> # 未设置则指向 JDK 21 安装目录，例如：
> #   Windows  C:\Program Files\Java\jdk-21.0.12
> #   macOS/Linux  $(/usr/libexec/java_home -v 21)
> ```

启动后：

| 入口 | 地址 |
|---|---|
| 探活（免登录） | http://localhost:8080/api/v1/public/ping |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| 健康检查 | http://localhost:8080/actuator/health |

> `dev` 首次启动会自动建表并灌入种子数据（已有 `admin` 用户时自动跳过）。
> 想重置开发库：`DROP DATABASE inkos` 后重建，下次启动会重新初始化。

### 三个 profile

| profile | 数据库 | 建表方式 | 用途 |
|---|---|---|---|
| `dev`（默认） | MySQL 8 | 启动时执行 `schema.sql` + 种子数据 | 本地开发 |
| `test` | MySQL 8（`inkos_test` 库） | 启动时执行 `schema.sql` + 种子数据 | 集成测试 |
| `prod` | MySQL 8 + Redis | 不自动建表（交给 Flyway / DBA） | 生产 |

> `local` **不是** profile，而是 `application-local.yml` 这份被 gitignore 的个人配置文件，
> 由 `spring.config.import` 无条件加载（文件不存在时静默跳过），用来放自己的数据库口令。

跑测试：

```bash
./mvnw clean verify     # 连 inkos_test 库，不污染开发数据
```

本机没有 MySQL 时，可以用仓库自带的 compose 起一个：

```bash
# 起 MySQL（宿主端口是 3307，避开本机常见的 3306 实例）
docker compose -f deploy/docker-compose.yml up -d mysql

# 记得把端口告诉应用
DB_PORT=3307 ./mvnw -pl inkos-admin -am spring-boot:run
```

`deploy/docker-compose.yml` 里的开发口令同样支持环境变量覆盖，默认值只面向本地：

```bash
MYSQL_ROOT_PASSWORD=xxx REDIS_PASSWORD=yyy docker compose -f deploy/docker-compose.yml up -d
```

### 默认账号

| 账号 | 密码 | 角色 | 权限范围 |
|---|---|---|---|
| `admin` | `admin123` | ROLE_ADMIN | 全部权限（通配 `*`） |
| `author` | `author123` | ROLE_AUTHOR | 内容管理（文章 / 分类 / 标签） |

快速验证：

```bash
# 1. 探活
curl http://localhost:8080/api/v1/public/ping

# 2. 登录拿令牌
curl -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'

# 3. 带令牌访问受保护接口（把 <token> 换成上一步返回的 tokenValue）
curl http://localhost:8080/api/v1/admin/users -H "Authorization: Bearer <token>"

# 4. 前台文章列表（免登录，返回 3 篇种子文章）
curl http://localhost:8080/api/v1/public/articles
```

---

## 二、分层架构

### 模块与职责

```
inkos-blog (parent, packaging=pom)
│
├── inkos-common      基础层   统一响应、异常、枚举、常量、分页、工具    ← 零业务依赖
├── inkos-system      系统层   用户、角色、菜单权限、操作/登录日志
├── inkos-framework   框架层   Sa-Token、全局异常、MyBatis-Plus、AOP、Web 配置
├── inkos-content     内容层   文章、分类、标签、评论
└── inkos-admin       表现层   控制器、DTO 装配、启动类、配置文件   ← 打包入口
```

### 依赖方向（严格单向，无环）

```
                    ┌──────────────┐
                    │ inkos-admin  │  表现层 + 启动
                    └──┬───────┬───┘
             ┌─────────┘       └─────────┐
             ▼                           ▼
    ┌────────────────┐          ┌──────────────┐
    │ inkos-framework│          │ inkos-content│
    └───────┬────────┘          └───────┬──────┘
            ▼                           │
    ┌────────────────┐                  │
    │  inkos-system  │                  │
    └───────┬────────┘                  │
            └───────────────┬───────────┘
                            ▼
                   ┌────────────────┐
                   │  inkos-common  │  基础层
                   └────────────────┘
```

**关键点：`content` 与 `system` 之间没有任何依赖。**
内容层需要展示作者昵称，但用户数据属于系统层 —— 解法是在 `content` 里声明一个端口：

```java
// inkos-content：只声明「我需要什么」
public interface AuthorNameResolver {
    Map<Long, String> resolveNames(Collection<Long> authorIds);
}
```

```java
// inkos-admin：唯一同时依赖两边的地方，负责接起来
@Component
public class SysUserAuthorNameResolver implements AuthorNameResolver { ... }
```

这不是过度设计 —— 它换来的是：`content` 可以独立编译、独立测试、将来独立拆成服务，
而 `admin` 只是把端口和实现对接起来。

### 各层能放什么、不能放什么

| 层 | 可以放 | 禁止放 |
|---|---|---|
| `common` | 与业务无关的响应体、异常、枚举、工具 | 任何业务实体、任何 Web/ORM 框架的强依赖 |
| `system` / `content` | 实体、Mapper、Service、DTO、VO | Controller、Sa-Token 调用、跨模块直接引用对方的 Mapper |
| `framework` | 全局配置、鉴权、异常处理、AOP、基础设施 Bean | 业务逻辑 |
| `admin` | Controller、请求参数装配、启动类、配置 | 业务逻辑（应下沉到对应业务层） |

> `system` 层**刻意不依赖 Sa-Token**：它的职责只是「校验凭证」，签发 Token 属于框架层。
> 这样 `system` 可以被任意测试，也保留了更换鉴权方案的自由。

---

## 三、目录结构

```
inkos-blog/
├── pom.xml                                  父 POM：统一依赖版本与编译插件
├── mvnw / mvnw.cmd                          Maven Wrapper
├── .mvn/wrapper/                            wrapper 版本锁定
├── .gitattributes                           换行符归一化
│
├── inkos-common/
│   └── com/inkos/common/
│       ├── core/domain/       Result / PageResult / PageQuery / BaseEntity
│       ├── core/enums/        ResultCode / ArticleStatus / LogBusinessType
│       ├── core/constant/     CommonConstants / CacheConstants
│       ├── core/validate/     ValidGroup（Create / Update / Query 分组）
│       ├── annotation/        @OperLog
│       ├── exception/         BusinessException
│       ├── metrics/           InkosMetrics（业务指标统一入口）
│       └── util/              StrUtils / TextUtils / TreeUtils
│
├── inkos-system/
│   └── com/inkos/system/
│       ├── entity/            SysUser SysRole SysMenu SysUserRole SysRoleMenu SysOperLog SysLoginLog
│       ├── mapper/            对应 Mapper（含自定义权限查询 SQL）
│       ├── domain/            LoginUser（登录上下文）
│       ├── dto/ vo/           LoginRequest / SysUserForm / UserInfoVO / RouterVO ...
│       └── service/           用户、角色、菜单、权限、日志
│
├── inkos-framework/
│   └── com/inkos/framework/
│       ├── config/            SaTokenConfigure MybatisPlusConfig MyMetaObjectHandler
│       │                      JacksonConfig WebMvcConfig AsyncConfig PasswordConfig
│       ├── security/          StpInterfaceImpl AuthService SecurityUtils LoginResult
│       ├── ratelimit/         @RateLimit / RateLimiterAspect（滑动窗口，无 Redis）
│       ├── web/               GlobalExceptionHandler / TraceIdFilter
│       └── aspect/            OperLogAspect
│
├── inkos-content/
│   └── com/inkos/content/
│       ├── entity/ mapper/    Article Category Tag ArticleTag Comment
│       │                      ArticleReaction CommentReaction（点赞 / 收藏）
│       ├── enums/             CommentStatus（审核状态机）
│       ├── port/              AuthorNameResolver（跨层端口）
│       ├── dto/ vo/           ArticleQuery ArticleForm CategoryForm CommentForm
│       │                      ArticleVO CommentVO（评论树）SearchResultVO ReactionStateVO
│       └── service/           文章、分类、标签、语句、评论、互动
│
├── deploy/
│   └── docker-compose.yml                    本地 MySQL / Redis（仅 local、prod 需要）
│
└── inkos-admin/
    ├── java/com/inkos/admin/
    │   ├── InkosApplication.java            启动类
    │   ├── config/        OpenApiConfig DevDataInitializer
    │   ├── integration/   SysUserAuthorNameResolver（端口适配器）
    │   └── controller/    Auth PublicContent AdminContent AdminComment
    │                      PublicInteraction Interaction
    │                      SysUser SysRole SysMenu
    └── resources/
        ├── application.yml / -dev.yml / -prod.yml
        ├── application-local.yml            个人数据库口令（已 gitignore，需自行创建）
        ├── logback-spring.xml
        └── db/schema.sql                    建表脚本（MySQL 方言，可反复执行）
```

---

## 四、技术栈

| 分类 | 选型 | 版本 |
|---|---|---|
| 语言 / 运行时 | Java | **25（LTS）** |
| 框架 | Spring Boot | **4.1.1**（Spring Framework 7 / Jakarta EE 11） |
| 鉴权 | **Sa-Token** | 1.46.0（`sa-token-spring-boot4-starter`） |
| ORM | **MyBatis-Plus** | 3.5.17（`mybatis-plus-spring-boot4-starter`） |
| JSON | **Jackson 3** | `tools.jackson`（Boot 4 默认，已从 `com.fasterxml.jackson` 迁移） |
| 密码加密 | spring-security-crypto（仅 BCrypt） | 随 Boot 管理 |
| 数据库 | **MySQL** | 8.0+ / InnoDB / utf8mb4（全部 profile 统一） |
| 缓存 | **Redis** | 抽象 `CacheService`、实现 `RedisCacheService`、内容域策略 `ContentCache`；本机验证于 Redis 8.10.2（Lettuce 协商到 RESP3） |
| 驱动 | mysql-connector-j | 随 Boot 管理 |
| API 文档 | springdoc-openapi | 3.1.1（v3 线对应 Boot 4） |
| 可观测性 | Micrometer + Actuator | 随 Boot 管理，暴露 Prometheus 端点 |
| 代码简化 | Lombok | 1.18.48 |

**刻意没引入的东西**（骨架阶段保持轻量）：Spring Security 过滤器链、Elasticsearch、
消息队列。理由与接入时机见文末「已知技术债」。

---

## 五、接口清单

### 公共（白名单，免登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/public/ping` | 探活 |
| GET | `/api/v1/public/articles` | 文章分页（仅已发布） |
| GET | `/api/v1/public/articles/{slug}` | 文章详情（累加浏览量，同一访客在窗口内去重） |
| GET | `/api/v1/public/articles/{id}/related` | 相关文章 |
| GET | `/api/v1/public/search` | 检索（标题/摘要/正文，附命中片段） |
| GET | `/api/v1/public/categories` | 分类树 |
| GET | `/api/v1/public/tags` | 标签云 |
| GET | `/api/v1/public/quotes` | 首页语句 |
| GET | `/api/v1/public/articles/{id}/comments` | 评论树（仅已通过审核） |
| POST | `/api/v1/public/articles/{id}/comments` | 发表评论 / 回复（游客需填昵称） |
| GET | `/api/v1/public/articles/{id}/reactions` | 互动状态快照 |

### 认证

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/v1/auth/login` | 免登录，限流 10 次/分钟 |
| POST | `/api/v1/auth/logout` | 需登录 |
| GET | `/api/v1/auth/info` | 需登录 |
| GET | `/api/v1/auth/routers` | 需登录 |
| GET | `/api/v1/auth/status` | 需登录 |

### 互动（需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/articles/{id}/like` | 点赞 / 取消（toggle），限流 30 次/分钟 |
| POST | `/api/v1/articles/{id}/favorite` | 收藏 / 取消（toggle），限流 30 次/分钟 |
| POST | `/api/v1/comments/{id}/like` | 评论点赞 / 取消，限流 30 次/分钟 |
| GET | `/api/v1/me/favorites` | 我的收藏（按收藏时间倒序） |

### 后台（需登录 + 权限码）

| 方法 | 路径 | 权限码 |
|---|---|---|
| GET | `/api/v1/admin/users` | `system:user:list` |
| POST/PUT/DELETE | `/api/v1/admin/users` | `system:user:add/edit/remove` |
| PUT | `/api/v1/admin/users/{id}/password` | `system:user:resetPwd` |
| GET/POST/PUT/DELETE | `/api/v1/admin/roles` | `system:role:*` |
| PUT | `/api/v1/admin/roles/{id}/menus` | `ROLE_ADMIN` |
| GET | `/api/v1/admin/menus/tree` | `system:menu:list` |
| GET/POST/PUT/DELETE | `/api/v1/admin/articles` | `content:article:*` |
| PUT | `/api/v1/admin/articles/{id}/publish` | `content:article:publish` |
| GET/POST/PUT/DELETE | `/api/v1/admin/categories` | `content:category:*` |
| GET | `/api/v1/admin/comments` | `content:comment:list` |
| PUT | `/api/v1/admin/comments/{id}/audit` | `content:comment:audit` |
| DELETE | `/api/v1/admin/comments/{id}` | `content:comment:remove` |

> 新增的 `content:comment:*` 权限码无需补种子数据：超管持有通配权限 `*`，
> 天然覆盖任意段数的权限码。

---

## 六、工程约定

### 统一响应

```json
{ "code": 200, "message": "操作成功", "data": { }, "timestamp": 1730000000000 }
```

- `code` 表达业务语义，前端只依赖它；HTTP 状态码同时被设置以便网关/监控识别。
- 领域错误码约定为「HTTP 状态码 × 100 + 序号」，例如 `40101` 登录失败、`40401` 文章不存在。

### 鉴权策略：默认拒绝

`SaTokenConfigure` 里只放行显式声明的白名单，其余 `/api/**` 一律要求登录。
**新增接口忘记加注解不会导致越权**，这是刻意的选择。

### 权限码规范

```
system:user:list     系统 - 用户 - 列表
content:article:add  内容 - 文章 - 新增
```

段数不强求统一。但要注意下面这条真实的坑 —— 它曾让超管被拒之门外：

超管返回通配权限 `*`（Sa-Token 把权限项当正则模式匹配，`*` 展开为 `.*`，可覆盖任意段数）。
**不要**用 RuoYi 风格的 `*:*:*`：它会展开成 `.*:.*:.*`，要求至少两个冒号，
匹配不到 `content:list` 这类两段式权限码 —— 超管反而拿到 403。

### 数据层约定

| 约定 | 实现 |
|---|---|
| 逻辑删除 | 实体字段 `@TableLogic(value="0", delval="1")` |
| 审计字段 | `BaseEntity` + `MyMetaObjectHandler` 自动填充，业务代码不赋值 |
| 主键 | `@TableId(type = IdType.AUTO)`，依赖数据库自增 |
| 分页 | MP `Page` → `PageResult`，Service 层完成转换 |
| 防全表更新 | `BlockAttackInnerInterceptor` 拦截无 WHERE 的 update/delete |
| 排序注入 | 外部排序字段必须走白名单映射，禁止拼接进 SQL |

---

## 七、可观测性与限流

### 请求追踪 ID

每个请求都有 `X-Request-Id`：客户端传了就用客户端的，没传则服务端生成 16 位随机串。
它被写入 MDC，因此**所有日志行都带这个 ID**，拿到一个 ID 就能串起一次请求的完整链路：

```
2026-09-19 16:50:44.961 [tomcat-handler-17] [398f9c8564a84fe9] WARN  c.i.f.ratelimit.RateLimiterAspect - 触发限流 ...
```

同时回写到响应头，方便前端报错时一并提交。

两个实现细节：外部传入的 ID 做了长度约束（它会被写进日志，不能原样信任）；
MDC 在 `finally` 中清理 —— 处理请求的线程是复用的，不清理会把上一个请求的 ID 串到下一个。

### 指标

| 指标 | 说明 |
|---|---|
| `http_server_requests_*` | 自动采集的 HTTP 指标（按 URI / 方法 / 状态码） |
| `inkos.article.view` | 文章详情浏览 |
| `inkos.comment.submit` | 评论创建（标签 `audit=on/off`） |
| `inkos.reaction.toggled` | 互动切换（标签 `type=like/favorite/comment`、`action=on/off`） |
| `inkos.ratelimit.rejected` | 被限流拒绝的请求（标签 `key`） |

```bash
curl http://localhost:8080/actuator/prometheus | grep '^inkos_'
```

标签刻意只放**低基数维度**。`userId`、`articleId` 这类放进标签会让时间序列基数爆炸；
需要按它们分析时应该走日志或链路追踪，而不是指标。

### Redis

连接配置在 `application.yml`（全部 profile 共用，环境变量驱动），序列化策略在 `RedisConfig`：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DB:0}
```

**连接是惰性的**：Redis 没启动时应用照常启动，只有真正操作 Redis 才会失败。
但要注意 —— 引入 starter 后 **Redis 会成为健康检查的一部分**，Redis 不可用时
`/actuator/health` 整体变 `DOWN`（会影响 k8s 就绪探针）。不打算强依赖它时：

```yaml
management:
  health:
    redis:
      enabled: false
```

序列化用 JSON 而非 JDK 序列化，并且**开启默认类型信息** —— 否则反序列化只能得到
`LinkedHashMap`，类型静默丢失。三处非默认选择（`As.WRAPPER_ARRAY`、
`NON_FINAL_AND_RECORDS`、白名单 validator）的理由写在 `RedisConfig` 的注释里，
边界由 `RedisConfigTest` 覆盖：

| 存什么 | 能否读回 |
|---|---|
| record / POJO | ✅ 类型完整 |
| `new ArrayList<>(...)` | ✅ 容器与元素类型都保留 |
| `List.of(...)` / `Map.of(...)` 作为**顶层值** | ❌ **读不回来** —— 改用 `ArrayList` 或用 POJO 包一层 |

### 缓存：抽象、策略与失效

缓存分三层放置，每层只解决一件事：

| 位置 | 角色 | 职责 |
|---|---|---|
| `inkos-common` | `CacheService` | **抽象**：读 / 写 / 精确删除 / 一次性占坑 / 计数器，并声明「任何方法都不许把缓存故障抛给调用方」 |
| `inkos-framework` | `RedisCacheService` | **实现**：JSON 读写、故障熔断、TTL 抖动、不可变集合归一化 |
| `inkos-content` | `ContentCache` | **策略**：内容域缓存了哪些 key、TTL 多长、什么操作让它失效 |

抽象之所以放在 `common`：`content` / `system` 需要缓存，但**不能**依赖 `framework`
（依赖方向只允许 `admin → framework → system → common` 与 `admin → content → common`）。
拿不到实现时自动退化为 `NoOpCacheService`，所有读写变成「未命中 + 直连数据库」，
因此 `content` 依旧可独立编译、独立测试 —— 与 `AuthorNameResolver` 端口是同一套思路。

**缓存了什么**

| key | 内容 | TTL |
|---|---|---|
| `inkos:category:tree` | 分类树 | 30 min |
| `inkos:tag:cloud` | 标签云 | 30 min |
| `inkos:quote:list:{ver}:{limit}` | 首页语句 | 30 min |
| `inkos:article:list:{ver}:{条件指纹}` | 文章列表（仅前台、无关键字） | 5 min |
| `inkos:article:related:{ver}:{id}:{limit}` | 相关文章 | 5 min |
| `inkos:article:detail:{slug}` | 文章详情 | 5 min |
| `inkos:view:dedup:{articleId}:{访客}` | 阅读去重占坑 | 6 h |

按 slug 的详情缓存正是最划算的一处：一次详情渲染原本要 5~6 次查询
（正文 + 分类 + 标签 + 作者 + 浏览自增），现在命中缓存时是 0 次读库。

**刻意不缓存**的两处：关键字检索（关键字取值空间无界，缓存它等于把 Redis 的写权限
交给调用方）与后台列表（后台要立刻看到自己的改动）。

**失效只有三种形态**，一律不做前缀/模式删除（`KEYS` 会阻塞 Redis，
`SCAN` 在大 key 空间下代价不可控）：

1. **精确删除** —— 分类树、标签云、文章详情：key 是确定的；
2. **版本号整体作废** —— 列表、相关文章、首页语句的 key 里带 `limit` 或查询条件，
   取值不可枚举。写操作只对 `inkos:article:list:version` 做一次 `INCR`，
   全部列表缓存立刻作废；老 key 不删，随各自 TTL 自然消失（O(1) 失效且不阻塞 Redis）；
3. **什么都不做，交给 TTL** —— 只有计数值变了（点赞、评论数）时。
   计数变化太频繁，为它作废整个列表缓存不划算，这类字段的 TTL 就是「最多滞后多久」的承诺。

> 版本号 key **刻意不设过期时间**：一旦过期就会从 0 重新开始，
> 与它同时代的老条目可能被「复活」。它由原生 `INCR` 写入，天然不带 TTL。

**启动预热**：`CacheWarmUpRunner` 挂在 `ApplicationReadyEvent` 上（晚于所有
`ApplicationRunner`，因此种子数据已提交），把分类树、标签云、首页语句、前两页文章列表
以及这些文章的详情写进 Redis。它调用的是**正常业务读方法**，所以预热写入的 key
与真实读取时不可能对不上 —— 预热另写一套 key 是只在「第一次访问」才暴露的隐蔽 bug。
预热失败只打日志，不影响启动；`inkos.cache.warm-up.enabled=false` 可关闭。

**故障降级**：Redis 挂了不该变成接口 5xx，也不该让每个请求都先等满 3 秒连接超时。
`RedisCacheService` 用 30 秒熔断窗口做到这一点 —— 首次失败打一条 WARN，
之后 30 秒内直接判定「不可用」，不再真的去连。**只在「从可用转入不可用」时打日志**，
否则故障期间每个请求一行日志会把真正的线索刷掉。

**TTL 抖动**：同一批预热写入的 key 若 TTL 完全相同，就会在同一秒集体过期、
同一秒集体回源。`±10%` 抖动把它们打散（实测 `category:tree` 1793s、
`tag:cloud` 1854s、`quote:list` 1933s，标称值都是 1800s）。

**归一化**：`put` 在落盘前把 `List` / `Set` / `Map` 复制成可变实现。
`Stream.toList()`、`Collections.emptyList()` 的实现类是 final 且不是 record，
不写类型信息，作为顶层值**读不回来**（见上表）。业务侧每次手写 `new ArrayList<>()`
太容易漏，因此在缓存层统一兜住。

**阅读计数的去重**：详情走缓存后，若计数器仍是「每个请求 +1」，就变成
「读压力转移出数据库、写压力原样留在数据库」—— 一次 F5 就是一次 UPDATE。
前台传访客标识（登录用户 `u:{id}`、游客 `ip:{ip}`），6 小时内同一访客只计一次；
识别不出访客时 fail-open（每次都计），与引入缓存之前的行为一致。

> 注意：详情缓存里带着 `view_count` / `like_count` / `comment_count`，
> 缓存期间不会刷新 —— **5 分钟 TTL 就是「计数最多滞后多久」的承诺**。
> 点赞与文章编辑会在写路径主动删掉详情 key（slug 当时已在手里，不额外查库）；
> 评论数变化刻意不做主动失效，交给 TTL。

覆盖面由两个测试把守：`CachedValueRoundTripTest`（序列化契约，不连 Redis，必然执行）
与 `ContentCacheIntegrationTest`（真实 Redis 链路，Redis 不可用时自动 skip 而非失败）。

### 健康检查的一个坑

`management.endpoint.health.show-details` **不能写 `when-authorized`**：
它依赖 Spring Security 的 `Authentication`，而本项目鉴权走 Sa-Token，
Boot 永远拿不到登录态，结果是详情**永远不显示**（连 Redis / MySQL 组件都看不到）。

现在的配置是：基础 `never`（生产安全），`dev` 覆盖为 `always`：

```bash
curl http://localhost:8080/actuator/health
# {"components":{"db":{...,"status":"UP"},"redis":{"details":{"version":"8.10.2"},"status":"UP"}},"status":"UP"}
```

### 接口限流

在接口上标注 `@RateLimit` 即可，已用于登录（防爆破）与发表评论（防刷屏）：

```java
@RateLimit(count = 10, period = 60, key = "auth.login", message = "登录尝试过于频繁，请稍后再试")
@PostMapping("/login")
```

实现是**单机内存滑动窗口日志**，不依赖 Redis：

- 不用固定窗口计数 —— 固定窗口在边界处会放过接近 2 倍配额
  （00:59 打满一轮，01:00 立刻又能打满）
- 默认维度 `AUTO`：已登录按用户、未登录按 IP
- 放在框架层而非网关层：网关拿不到登录态，只能按 IP，而一个 IP 后面可能是整栋楼的用户

> 多实例部署时每个实例各限各的。需要精确全局限流时，把切面的计数后端换成
> Redis 计数器即可，注解与调用方都不用改。

---

## 八、如何扩展

### 新增一个业务模块（例如 `inkos-notification` 站内通知）

1. 复制 `inkos-content` 的 `pom.xml`，改 `artifactId` 为 `inkos-interaction`
2. 父 POM 的 `<modules>` 与 `<dependencyManagement>` 各加一项
3. `inkos-admin/pom.xml` 加依赖
4. 包结构照抄：`entity / mapper / port / dto / vo / service / service.impl`
5. 建表语句追加到 `db/schema.sql`
6. 在 `DevDataInitializer` 里补种子数据与菜单权限

### 生产部署

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=inkos DB_USERNAME=inkos DB_PASSWORD=xxx \
DB_POOL_SIZE=20 \
java -jar inkos-admin/target/inkos-blog.jar
```

**开发与生产跑同一套 MySQL 方言**：`db/schema.sql` 用 MySQL 方言编写
（InnoDB / utf8mb4 / 内联索引 / TINYINT / DATETIME），所有 profile 执行的都是它，
不存在「本地能跑、线上方言不兼容」的落差。

两处刻意的取舍：

- **索引内联在 `CREATE TABLE` 里**，不写独立的 `CREATE INDEX` ——
  MySQL 不支持 `CREATE INDEX IF NOT EXISTS`，只有内联才能让脚本可重复执行。
- **时间列用 `DATETIME` 而非 `TIMESTAMP`**：Java 侧字段是 `LocalDateTime`，
  `DATETIME` 原样存取、不做时区换算，也没有 `TIMESTAMP` 的 2038 年上限。

生产环境建议改用 Flyway/Liquibase 管理版本，并把 `spring.sql.init.mode` 设为 `never`。

### 接入 Redis 分布式会话

`spring-boot-starter-data-redis` 与连接配置都已就绪（`inkos-framework` + `application.yml`），
公开读缓存也已经跑在这套连接上。会话要切到 Redis 只剩一步：

1. 加 Sa-Token 的 Redis 集成包即可 —— Sa-Token 会自动把会话从内存切到 Redis，
   业务代码无需改动（`LoginUser` 已实现 `Serializable`，就是为这一步准备的）

`CacheConstants.LOGIN_USER_KEY` 是为「会话内登录用户」预留的 key 前缀，**当前未被使用**：
登录态由 Sa-Token 自己管理，缓存层刻意不碰它。

---

## 九、环境坑（实测记录）

这几个问题是本骨架搭建时真实踩到并已修复的，升级依赖时请留意：

### 1. Lombok 不生效？JDK 23+ 的注解处理器变更

javac 从 **JDK 23** 起不再隐式执行 classpath 上的注解处理器。
Lombok 若不显式声明，`@Data`、`@Slf4j` 会**静默失效**（只在用到时才报「找不到符号」）。
父 POM 已修复：

```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.46</version>
    </path>
</annotationProcessorPaths>
```

### 2. MyBatis-Plus 3.5.17 包名重构

`IService` / `ServiceImpl` 从 `extension.service` **迁到了 `spring.service`**：

```java
// ❌ 3.5.9 之前
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
// ✅ 3.5.17
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
```

`Page` / `LambdaQueryWrapper` 仍在 `extension` 包下，未变。

### 3. 分页插件需要额外依赖

MyBatis-Plus 3.5.9 起把依赖 JSqlParser 的插件拆到了独立模块。
不加 `mybatis-plus-jsqlparser`，`PaginationInnerInterceptor` 会 ClassNotFound。
`inkos-framework` 已显式引入。

### 4. 关联表启动时的 WARN（无害，可忽略）

启动日志里会有 6 行这类提示：

```
WARN c.b.m.core.metadata.TableInfoHelper - Can not find table primary key in Class: "com.inkos.system.entity.SysUserRole".
WARN c.b.m.c.injector.DefaultSqlInjector - class ...SysUserRole Not found @TableId annotation, Cannot use Mybatis-Plus 'xxById' Method.
```

涉及 `SysUserRole`、`SysRoleMenu`、`ArticleTag` 三张**复合主键关联表**。
它们本来就没有单一主键，MP 只是提示「不能用 `selectById` / `deleteById`」——
而这些方法我们本来就不会对关联表使用（读写都走 `LambdaQueryWrapper`）。

**不要**为了消除警告就给关联表加一个自增 id 列，那会丢掉
`(user_id, role_id)` 的唯一性约束，让重复授权成为可能。

### 5. Spring Boot 4 的构件改名（升级时最容易卡住的地方）

Boot 4 做了大规模模块化。旧名不只是 deprecated，有两个是**彻底移除**：

| Boot 3.5 | Boot 4.1 | 症状 |
|---|---|---|
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` | **BOM 不再管理版本 → Maven 在读 POM 阶段就失败**，不是编译错误 |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | 仍可用，但已 deprecated |
| `spring-boot-starter-test`（含 MockMvc） | 拆出 `spring-boot-starter-webmvc-test` | `@AutoConfigureMockMvc` 找不到包 |
| Jackson 2 `com.fasterxml.jackson.core` | **Jackson 3 `tools.jackson.core`** | 序列化配置需整体重写 |

配套的代码迁移：

- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`
- Java 8 时间类型支持已**并入 databind**（`tools.jackson.databind.ext.javatime`），不再需要 jsr310 依赖
- `@AutoConfigureMockMvc` 迁到 `org.springframework.boot.webmvc.test.autoconfigure`

反过来，这些**不用改**（已核实，别白改）：注解包仍是 `com.fasterxml.jackson.annotation`；
`org.springframework.test.web.servlet.*` 的 MockMvc API 位置未变。

### 6. 第三方 starter 的 Boot 4 坐标

同样是 starter，Boot 3 与 Boot 4 的 artifactId 不同，写错会直接解析失败：

```xml
<artifactId>sa-token-spring-boot4-starter</artifactId>      <!-- 不是 -spring-boot3- -->
<artifactId>mybatis-plus-spring-boot4-starter</artifactId>  <!-- 不是 -spring-boot3- -->
```

### 7. 用脚本验证中文接口时，不要相信控制台

PowerShell 的 `Invoke-RestMethod` 在响应头没有 charset 时会猜错编码，把中文显示成
`å升级è¸©å` 这类 mojibake；用字符串拼 JSON 请求体时更会把中文**替换成 `?` 写进库**。

这两件事都不是服务端的错（`application/json` 不带 charset 完全符合 RFC 8259，JSON 恒为 UTF-8），
但很容易被误判成「中文乱码 bug」。判定方法只有一个 —— 看字节：

```powershell
# 请求体必须显式编码，否则中文会被替换成 '?'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
Invoke-RestMethod -ContentType "application/json; charset=utf-8" -Body $bytes ...
```

```sql
-- 库里到底存对没有：中文 1 字符 = 3 字节
SELECT CHAR_LENGTH(content) AS chars, LENGTH(content) AS bytes, HEX(content) FROM cms_comment;
-- 存坏的样子：chars = bytes，且 HEX 全是 3F（就是 '?'）
```

---

## 十、下一步

| 阶段 | 状态 |
|---|---|
| 技术栈：Java 25 + Spring Boot 4.1.1 | ✅ 已完成 |
| M1 地基：多模块、Sa-Token、RBAC、统一异常、容器化环境 | ✅ 已完成 |
| M2 内容：文章 CRUD、分类标签、首页语句、**检索** | 🔶 主体完成（Markdown 渲染与 XSS 净化待接入） |
| M3 发布流水线：Outbox + MQ + 索引 + 缓存失效 | 🔶 公开读缓存、启动预热与写时失效已完成；Outbox + MQ + 增量索引待开发 |
| M4 互动：**评论树、点赞收藏**、统计看板 | 🔶 评论与互动已完成，统计看板待开发 |
| M5 生产化：**可观测性、接口限流**、SEO、备份演练 | 🔶 可观测性与限流已完成，SEO 与备份演练待开发 |

**推荐的下一步**：接入 Markdown 渲染 + Jsoup 净化（`ArticleServiceImpl` 中 `contentHtml` 目前暂存原文），
这样 M2 才算真正闭环。紧接着把 `spring.sql.init.mode` 换成 Flyway 管理数据库版本。

### 已知技术债

| 项 | 现状 | 方向 |
|---|---|---|
| Markdown → HTML | 暂存原文，未净化 | 渲染 + Jsoup 白名单 |
| 全文检索 | LIKE `%kw%` 匹配正文，**无法走索引** | MySQL `ngram` 全文索引，或外接 Elasticsearch |
| 数据库版本管理 | `schema.sql` 全量执行；新增列不会作用于已存在的表 | 生产切 Flyway/Liquibase |
| 限流 | 单机内存滑动窗口，多实例各限各的 | 换 Redis 计数器（只需替换切面后端，注解不动） |
| 缓存 | 公开读缓存（字典 / 列表 / 详情）+ 启动预热 + 阅读去重已接入 | 一致性见下一行；需要集群时把 `NoOpCacheService` 之外再多一个本地二级缓存 |
| 缓存一致性 | 事务提交后失效（精确删除 + 版本号），同键并发回源使用单进程条带锁合并 | 跨实例没有分布式锁；需要强一致性时增加提交事件或分布式协调 |
| 权限查询 | `SysPermissionServiceImpl` 每次鉴权都查库（Sa-Token 每个 `@SaCheckPermission` 都会回调） | 加 Redis 二级缓存。**前提是先覆盖失效面**：角色-菜单授权、用户-角色变更、角色与用户删除，漏一个就是越权 |
| 标签重名 | `cms_tag.name` 无唯一约束，并发建标会产生同名标签 | 名称加唯一索引 + 冲突重试 |
| slug 分配 | `resolveUniqueSlug` 是「先查再插」，存在 TOCTOU 窗口，并发创建会撞唯一键抛 `DuplicateKeyException` | 捕获冲突后重试，与点赞的幂等写法一致 |
| 评论数 → 详情缓存 | 评论增减不主动失效详情缓存，靠 5 分钟 TTL | 失效需要多查一次文章拿 slug；评论是低频写，暂不为它加查询 |
| 评论树 | 单篇评论全量查出后内存组树 | 评论量上千后改为按 rootId 分页 + 懒加载 |
| JSON 字段 | 未使用 | MySQL `JSON` 无 GIN 索引，复杂查询需另建索引表 |

> **升级注意**：本项目升级到 Boot 4 时新增了 `cms_article.favorite_count` 等列。
> `schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，**不会修改已存在的表**，
> 已有库应备份后执行对应的增量迁移，保留数据；本轮的首屏主题字段使用下面的 `hero-copy-upgrade.sql`。

## 砚知前端联调与缓存验收

前端默认端口为 4173，Vite `/api` 代理连接后端 8080。开发 profile 明确允许 `http://localhost:4173` 和 `http://127.0.0.1:4173`；生产来源使用 `CORS_ALLOWED_ORIGINS` 逗号分隔配置，默认不允许跨域，不使用通配来源。响应暴露 `X-Request-Id` 以便前端诊断。配置改动后须重新打包并重启后端，不能用旧进程验收。

Redis 读缓存保留 TTL 抖动及不可用时的数据库降级；同键并发加载使用 256 个条带锁和二次缓存检查减少数据库查询。失效动作在事务成功提交后执行，避免提交前旧值重新进入缓存。该保护是单进程内的，并不声称跨实例强一致。

执行 `mvn test` 时启动 MySQL 与 Redis。真实 Redis 测试覆盖缓存命中、TTL、重复读的数据库回源计数、内容失效及阅读去重；并发与故障单测覆盖合并回源、冷却降级，事务单测验证提交后失效。Redis 离线会跳过集成测试，应核对 Surefire 的 skipped 数，不将跳过算作通过。前端的真实浏览器登录测试见相邻 `inkos-web/scripts/verify-redesign.cjs`，覆盖两个来源及登录、刷新、登出。


## 内容工作台升级

文章后台增加回收/恢复（状态 4 → 草稿）、标签增删改查、详情 tagIds 和列表 visibility。普通作者只可访问与修改本人文章；公开列表、详情和相关文章仅返回公开且已发布内容。编辑已发布文章即时生效，写后缓存失效在事务提交后执行。

已有环境串行执行 `inkos-admin/src/main/resources/db/workspace-upgrade.sql` 后重新登录，补齐标签与恢复权限。脚本幂等，不自动扩大无内容管理权限角色的权限。新开发数据库在种子初始化时配置完整权限。

默认首页语句由独立初始化器处理，配置 `inkos.content.default-quotes.enabled`（默认 true）。仅在整个语句表为空时初始化，保留已有或已删除内容；MySQL 命名锁和事务保护并发启动。Redis 不可用时按需回源，各预热模块独立记录结果。

`mvn test` 包含 WorkspaceIntegrationTest：文章生命周期、归属和可见性、分类循环、标签引用及改名失效、语句停用、并发空库初始化与历史删除保留。测试使用 inkos_test、Redis DB 15，可通过 INKOS_TEST_REDIS_DB 更改；并发初始化测试需要测试 MySQL 账号创建/删除临时数据库权限。

## 首屏主题字段升级

`cms_quote` 新增可空 `headline VARCHAR(80)` 和 `description VARCHAR(240)`。公开 `GET /api/v1/public/quotes` 和后台语句列表返回这两个字段，后台新建/更新接收相同字段；成组填写或同时清空，不完整时返回 400。旧客户端都不传时保留原主题，显式传两个空字符串可清除主题。

生产部署前串行执行 `inkos-admin/src/main/resources/db/hero-copy-upgrade.sql`；dev/test 启动自动执行。脚本按列存在性判断，给五条未修改的默认语句补齐主题，保留其他内容、显式清空和历史删除记录。全新库由独立默认语句初始化器写入五组主题，仍受默认语句开关控制，不依赖开发账号。

语句缓存键加入 `hero-v3` 结构版本，避免旧 JSON 缺少字段；写后仍在事务提交后递增列表版本。公开列表只返回启用且未删除的语句。`WorkspaceIntegrationTest` 额外验证主题成组校验、长度、兼容旧客户端、清空、写后读取及旧表迁移重复执行与保留编辑。
