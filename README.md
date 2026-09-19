# 砚知 · 博客系统（后端骨架）

> `inkos` = **Ink** + **OS**。

一个**分层架构的 Java 后端骨架**，数据库为 **MySQL 8**。不是空壳：登录鉴权、RBAC 权限、文章 CRUD 全部是**能跑通的真实代码**，`git clone` 后无需任何外部中间件即可启动并调用。

> AI 能力（模型路由 / RAG / 写作助手）**本期不开发**。模块代码完整保留在 `inkos-ai/`，
> 但已移出构建，恢复步骤见 [`inkos-ai/PARKED.md`](inkos-ai/PARKED.md)。

---

## 一、快速开始

### 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **21+** | 推荐 21 LTS；已在 JDK 25 上验证 |
| Maven | 3.9+ | |

### 启动

```bash
cd inkos-blog

# 编译（跳过测试）
mvn clean package -DskipTests

# 运行
java -jar inkos-admin/target/inkos-blog.jar
```

或者直接：

```bash
mvn -pl inkos-admin -am spring-boot:run
```

启动后：

| 入口 | 地址 |
|---|---|
| 探活（免登录） | http://localhost:8080/api/v1/public/ping |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| 健康检查 | http://localhost:8080/actuator/health |
| H2 控制台 | http://localhost:8080/h2-console |

H2 控制台连接参数：JDBC URL `jdbc:h2:mem:inkos`、用户名 `sa`、密码留空。

> **零依赖启动**：`dev` profile 用 H2 内存库，跑的是 **MySQL 兼容模式**，
> 执行的正是生产那份 `db/schema.sql`。不需要装 MySQL / Redis。

### 三个 profile

| profile | 数据库 | 建表方式 | 用途 |
|---|---|---|---|
| `dev`（默认） | H2 内存库（`MODE=MySQL`） | 启动时自动执行 `schema.sql` | 零依赖开发、跑测试 |
| `local` | 真实 MySQL 8 | 启动时自动执行 `schema.sql` | 联调、提前暴露方言差异 |
| `prod` | 真实 MySQL 8 + Redis | 不自动建表（交给 Flyway） | 生产 |

连真实 MySQL 开发：

```bash
# 1. 起一个 MySQL（宿主端口刻意用 3307，避开本机已有的 3306 实例）
docker compose -f deploy/docker-compose.yml up -d mysql

# 2. 用 local profile 启动
mvn -pl inkos-admin -am spring-boot:run -Dspring-boot.run.profiles=local

# 3. 连自己的 MySQL 就覆盖环境变量
#    DB_PORT=3306 DB_USERNAME=root DB_PASSWORD=xxx
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

inkos-ai              智能层   已移出构建（本期不开发，代码保留）
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
├── inkos-common/
│   └── com/inkos/common/
│       ├── core/domain/       Result / PageResult / PageQuery / BaseEntity
│       ├── core/enums/        ResultCode / ArticleStatus / LogBusinessType
│       ├── core/constant/     CommonConstants / CacheConstants
│       ├── core/validate/     ValidGroup（Create / Update / Query 分组）
│       ├── annotation/        @OperLog
│       ├── exception/         BusinessException
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
│       ├── web/               GlobalExceptionHandler
│       └── aspect/            OperLogAspect
│
├── inkos-content/
│   └── com/inkos/content/
│       ├── entity/ mapper/    Article Category Tag ArticleTag Comment
│       ├── port/              AuthorNameResolver（跨层端口）
│       ├── dto/ vo/           ArticleQuery ArticleForm CategoryForm / ArticleVO ...
│       └── service/           文章、分类、标签
│
├── inkos-ai/                                ← 已移出构建，仅存档
│   ├── PARKED.md                            ← 恢复步骤 + 重启前的选型提醒
│   └── com/inkos/ai/
│       ├── client/            LlmClient（抽象）/ MockLlmClient / OpenAiCompatibleLlmClient
│       ├── router/            ModelRouter（按场景路由 + 降级）
│       ├── prompt/            PromptRegistry（模板 + 变量渲染）
│       └── facade/            AiFacade（业务唯一入口）
│
└── inkos-admin/
    ├── java/com/inkos/admin/
    │   ├── InkosApplication.java            启动类
    │   ├── config/        OpenApiConfig DevDataInitializer
    │   ├── integration/   SysUserAuthorNameResolver（端口适配器）
    │   └── controller/    Auth PublicContent AdminContent SysUser SysRole SysMenu
    └── resources/
        ├── application.yml / -dev.yml / -local.yml / -prod.yml
        ├── logback-spring.xml
        └── db/schema.sql                    建表脚本（MySQL 方言，H2 兼容模式复用）
```

---

## 四、技术栈

| 分类 | 选型 | 版本 |
|---|---|---|
| 语言 / 运行时 | Java | 21（编译目标） |
| 框架 | Spring Boot | 3.5.11 |
| 鉴权 | **Sa-Token** | 1.45.0 |
| ORM | **MyBatis-Plus** | 3.5.17 |
| 密码加密 | spring-security-crypto（仅 BCrypt） | 随 Boot 管理 |
| 数据库（开发） | H2（**MySQL 兼容模式**） | 随 Boot 管理 |
| 数据库（生产） | **MySQL** | 8.0+ / InnoDB / utf8mb4 |
| 驱动 | mysql-connector-j | 随 Boot 管理 |
| API 文档 | springdoc-openapi | 2.8.9 |
| 代码简化 | Lombok | 1.18.46 |

**刻意没引入的东西**（骨架阶段保持轻量）：Spring Security 过滤器链、Redis、Elasticsearch、
消息队列、任何 AI SDK。理由与接入时机见架构设计文档。

---

## 五、接口清单

### 公共（白名单，免登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/public/ping` | 探活 |
| GET | `/api/v1/public/articles` | 文章分页（仅已发布） |
| GET | `/api/v1/public/articles/{slug}` | 文章详情（累加浏览量） |
| GET | `/api/v1/public/articles/{id}/related` | 相关文章 |
| GET | `/api/v1/public/categories` | 分类树 |
| GET | `/api/v1/public/tags` | 标签云 |

### 认证

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/v1/auth/login` | 免登录 |
| POST | `/api/v1/auth/logout` | 需登录 |
| GET | `/api/v1/auth/info` | 需登录 |
| GET | `/api/v1/auth/routers` | 需登录 |
| GET | `/api/v1/auth/status` | 需登录 |

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

> 原 `/api/v1/ai/**` 三个接口随 `inkos-ai` 模块一起下线，现在访问返回 404。

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
匹配不到 `ai:chat` 这类两段式权限码 —— 超管反而拿到 403。

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

## 七、如何扩展

### 新增一个业务模块（例如 `inkos-interaction` 评论互动）

1. 复制 `inkos-content` 的 `pom.xml`，改 `artifactId` 为 `inkos-interaction`
2. 父 POM 的 `<modules>` 与 `<dependencyManagement>` 各加一项
3. `inkos-admin/pom.xml` 加依赖
4. 包结构照抄：`entity / mapper / port / dto / vo / service / service.impl`
5. 建表语句追加到 `db/schema.sql`
6. 在 `DevDataInitializer` 里补种子数据与菜单权限

### 切换到真实 MySQL

`dev` 默认走 H2；连真实 MySQL 用 `local` profile（见上文「三个 profile」），生产用 `prod`：

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=inkos DB_USERNAME=inkos DB_PASSWORD=xxx \
java -jar inkos-admin/target/inkos-blog.jar
```

**一份 DDL 同时服务 dev 与生产**：`db/schema.sql` 用 MySQL 方言编写
（InnoDB / utf8mb4 / 内联索引 / TINYINT / DATETIME），H2 以 `MODE=MySQL` 执行它。
这能在开发阶段就暴露方言问题，比维护两套建表语句可靠得多。

两处刻意的取舍：

- **索引内联在 `CREATE TABLE` 里**，不写独立的 `CREATE INDEX` ——
  MySQL 不支持 `CREATE INDEX IF NOT EXISTS`，只有内联才能让脚本可重复执行。
- **时间列用 `DATETIME` 而非 `TIMESTAMP`**：Java 侧字段是 `LocalDateTime`，
  `DATETIME` 原样存取、不做时区换算，也没有 `TIMESTAMP` 的 2038 年上限。

生产环境建议改用 Flyway/Liquibase 管理版本，并把 `spring.sql.init.mode` 设为 `never`。

### 重启 AI 功能

见 [`inkos-ai/PARKED.md`](inkos-ai/PARKED.md)。里面有两部分：四步恢复清单，
以及**重启前必须先做的三个选型决策** —— 换到 MySQL 后，原来的
`pgvector`（向量检索）和 `zhparser`（中文全文检索）方案都失效了，需要重新选型。

### 接入 Redis 分布式会话

1. 三个模块的 POM 加 `spring-boot-starter-data-redis` 与 Sa-Token 的 Redis 集成包
2. `application-prod.yml` 里的 `spring.data.redis.*` 已就绪
3. Sa-Token 会自动把会话从内存切到 Redis，代码无需改动

---

## 八、环境坑（实测记录）

这三个问题是本骨架搭建时真实踩到并已修复的，升级依赖时请留意：

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

---

## 九、下一步

对照《智能博客系统-架构与功能设计.md》的里程碑：

| 阶段 | 状态 |
|---|---|
| M1 地基：多模块、Sa-Token、RBAC、统一异常、容器化环境 | ✅ 已完成 |
| M2 内容：文章 CRUD、分类标签 | 🔶 主体完成（Markdown 渲染与 XSS 净化待接入） |
| M3 发布流水线：Outbox + MQ + 索引 + 缓存失效 | ⬜ 待开发 |
| M4 互动：评论树、点赞收藏、统计看板 | ⬜ 待开发（`cms_comment` 表已建） |
| M5 生产化：可观测性、限流熔断、SEO、备份演练 | ⬜ 待开发 |
| ~~AI：模型路由、RAG、写作助手~~ | ⏸ 本期不做，模块已归档至 `inkos-ai/` |

**推荐的下一步**：接入 Markdown 渲染 + Jsoup 净化（`ArticleServiceImpl` 中 `contentHtml` 目前暂存原文），
这样 M2 才算真正闭环。紧接着把 `spring.sql.init.mode` 换成 Flyway 管理数据库版本。

### 换到 MySQL 后遗留的技术债

架构设计文档第 5 章里 RAG 相关方案建立在 PostgreSQL 之上，现在需要重新选型：

| 原方案（PostgreSQL） | 换 MySQL 后的选项 |
|---|---|
| `pgvector` 存向量 | 独立向量库（Qdrant / Milvus）或 MySQL 8.4 的 `VECTOR` 类型 |
| `zhparser` 中文分词 | MySQL `ngram` 全文索引，或外接 Elasticsearch（推荐，混合检索更可控） |
| `JSONB` 存 citations / seo | MySQL `JSON` 类型（功能略弱，无 GIN 索引） |

这些只影响 AI 重启时的选型，不影响当前骨架。
