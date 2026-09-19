# 砚知 · 后端设计说明

> 本文记录**设计决策与取舍**，操作步骤见 [README](../README.md)。
> 技术栈：Java 25 / Spring Boot 4.1.1（Spring Framework 7、Jakarta EE 11、Jackson 3）/ MySQL 8 / Sa-Token / MyBatis-Plus。

---

## 一、分层与依赖

```
admin  ──►  framework  ──►  system  ──┐
  │              │                    ├──►  common
  └──────────────┴──►  content  ──────┘
```

严格单向、无环。两条规则最关键：

1. **`content` 与 `system` 互不依赖。** 内容层要展示作者昵称，但用户数据属于系统层 ——
   解法是在 `content` 里声明 `AuthorNameResolver` 端口，由 `admin`（唯一同时依赖两边的层）提供适配器。
   换来的是 `content` 可独立编译、独立测试、将来可独立拆服务。
2. **跨模块聚合只能放在 `admin`。** 例如把用户数据与内容数据拼在一起返回，
   属于装配而非业务逻辑，放 `admin` 是唯一不破坏依赖方向的位置。

`common` 不依赖任何业务模块，也不依赖 Web/ORM 的**强**约束（`Result` 只用到 Jackson 注解，
`InkosMetrics` 用到 micrometer-core，两者都不引入业务语义）。

---

## 二、数据模型

### 系统域

| 表 | 说明 |
|---|---|
| `sys_user` / `sys_role` / `sys_menu` | 用户、角色、菜单权限 |
| `sys_user_role` / `sys_role_menu` | 关联表，**复合主键**（见下） |
| `sys_oper_log` / `sys_login_log` | 操作日志、登录日志 |

### 内容域

| 表 | 说明 |
|---|---|
| `cms_article` | 文章主体，含状态机字段 `status`（0 草稿 / 1 待审 / 2 已发布 / 3 下线 / 4 回收站） |
| `cms_category` / `cms_tag` / `cms_article_tag` | 分类树、标签、文章-标签关联 |
| `cms_comment` | 评论，两级结构：`parent_id`（直接父）+ `root_id`（顶层） |
| `cms_quote` | 首页语句 |
| `cms_article_reaction` | 文章互动，`type` 区分点赞(1)/收藏(2) |
| `cms_comment_reaction` | 评论点赞 |

### 几个刻意的建模决策

**① 点赞与收藏合表。** 两者的结构、唯一性约束、查询模式完全一致，
拆成两张表只会让「查我的互动」「统计计数」都要写两遍。
用 `type` 区分，代价是查询必须带 `type` 条件 —— 已有联合索引覆盖。

**② 评论只存两级，不做递归。** `root_id` 指向顶层评论，任意深度的回复都能
沿 `root_id` 一次查全，无需 CTE 或递归查询，也不绑定具体数据库。

**③ 关联表不加自增主键。** `sys_user_role` 等复合主键表刻意不设 `id`：
加了会丢掉 `(user_id, role_id)` 的唯一约束，让重复授权成为可能。
代价是 MyBatis-Plus 会打印「找不到主键」的 WARN —— 那是无害的，因为读写都走 `LambdaQueryWrapper`。

**④ 计数列冗余在 `cms_article` 上。** `view_count` / `like_count` / `favorite_count` / `comment_count`
都是冗余列，由业务代码维护。理由是列表页需要按热度排序，实时 `COUNT(*)` 聚合无法走索引。
所有计数更新统一用 `GREATEST(x + delta, 0)`，任何异常路径都不会写出负数。

---

## 三、关键设计决策

### 幂等：靠数据库唯一键，而不是「先查再写」

点赞 / 收藏的实现是 **先删、删不到再插、插入冲突就当作已存在**：

```
DELETE WHERE (article_id, user_id, type)   -- 删到了 → 本次是「取消」
        ↓ 没删到
INSERT ...                                 -- 唯一键冲突 → 并发下别人已插，视为「已点赞」
```

比「先 SELECT 判断存在与否，再决定 INSERT 还是 DELETE」少一次查询，且**没有竞态窗口**：
两个并发请求最多其中一个收到唯一键冲突，被捕获后双方返回一致结果。

### 评论树：内存组装 + 孤儿回复上浮

一次查出该文章全部已通过审核的评论（`idx_cms_comment_article` 覆盖），在内存组树。
相比递归查库：少一次 N+1；相比 MySQL 8 的 CTE：不绑定数据库方言。

**父评论不可见（被删除或未过审）时，其回复提升为顶层**，而不是跟着消失 ——
内容一旦发布过就不该凭空蒸发。

计数同步有个易错点：审核接口**先判断状态是否真的变化**，否则重复审核会让
`comment_count` 反复增减。

### 检索：先 LIKE，把升级路径写清楚

当前用 `LIKE '%kw%'` 匹配标题 / 摘要 / 正文。命中正文时返回关键字两侧各 40 字的上下文片段；
只命中标题或摘要时片段为 `null` —— 不硬凑无关正文，那会让用户以为搜错了。

**已知限制**：`LIKE '%kw%'` 无法走索引，内容量上来后必须换成
MySQL `ngram` 全文索引或外接 Elasticsearch。这一点写在了代码注释与技术债清单里，
而不是留作「以后再说」。

### 限流：滑动窗口，放在框架层

- 不用固定窗口计数：固定窗口在边界处会放过接近 2 倍配额（00:59 打满一轮，01:00 立刻又能打满）
- 默认维度 `AUTO`：已登录按用户、未登录按 IP
- **放在框架层而非网关层**：网关拿不到登录态，只能按 IP，而一个 IP 后面可能是整栋楼的用户
- 单机内存实现，多实例各限各的。需要全局限流时替换切面后端为 Redis 计数器，注解与调用方不动

### 可观测性：指标标签只用低基数维度

`inkos.reaction.toggled{type=like,action=on}` 是好的；
`{userId=12345}` 会让时间序列基数爆炸。需要按用户分析时走日志或链路追踪，而不是指标。

`TraceIdFilter` 把请求 ID 写入 MDC，日志格式带 `%X{traceId}`。
两个必须做对的地方：外部传入的 ID 要限长（它会被写进日志）；MDC 必须在 `finally` 清理
（线程是复用的，不清理会把上一个请求的 ID 串到下一个）。

### 鉴权：默认拒绝 + 通配权限

Sa-Token 拦截器只放行显式声明的白名单，其余 `/api/**` 一律要求登录 ——
**新增接口忘记加注解不会导致越权**。

超管返回单个 `*` 而非 RuoYi 风格的 `*:*:*`：Sa-Token 把权限项当**正则**匹配，
`*:*:*` 会展开成 `.*:.*:.*`，要求至少两个冒号，匹配不到两段式权限码 —— 超管反而会被拒。

---

## 四、升级到 Spring Boot 4 的适配要点

| 项 | Boot 3.5 | Boot 4.1 |
|---|---|---|
| AOP starter | `spring-boot-starter-aop` | `spring-boot-starter-aspectj`（旧名**已移除**，BOM 不再管理版本） |
| Web starter | `spring-boot-starter-web` | `spring-boot-starter-webmvc`（旧名 deprecated） |
| MockMvc 测试 | 含在 `spring-boot-starter-test` | 拆到 `spring-boot-starter-webmvc-test` |
| JSON | Jackson 2 `com.fasterxml.jackson.*` | Jackson 3 `tools.jackson.*`（jsr310 已并入 databind） |
| 鉴权 starter | `sa-token-spring-boot3-starter` | `sa-token-spring-boot4-starter` |
| ORM starter | `mybatis-plus-spring-boot3-starter` | `mybatis-plus-spring-boot4-starter` |

**不需要改的**：注解包仍是 `com.fasterxml.jackson.annotation`；
`org.springframework.test.web.servlet.*` 的 MockMvc API 位置未变。

---

## 五、扩展点

| 需求 | 落点 |
|---|---|
| 新增业务模块 | 复制 `inkos-content` 结构 → 父 POM 加 `<module>` 与依赖管理 → `admin` 加依赖 |
| 换限流后端 | 替换 `RateLimiterAspect` 的计数实现，注解不动 |
| 换缓存 | `CacheConstants` 已预留 key 前缀；`common` 加 Cache 抽象，`framework` 给实现 |
| 数据库迁移 | 把 `spring.sql.init` 换成 Flyway，`schema.sql` 转为 `V1__init.sql` |
| 多数据源 | `MybatisPlusConfig` 的分页拦截器未指定 `DbType`，由 DataSource 自动识别 |
