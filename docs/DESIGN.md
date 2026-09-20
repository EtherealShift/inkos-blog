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

### 缓存：三层放置，失效靠版本号而不是扫描

**为什么拆三层。** `CacheService`（common，抽象）/ `RedisCacheService`（framework，实现）
/ `ContentCache`（content，策略）。关键约束还是依赖方向：`content` 需要缓存，
却不能依赖 `framework`。把抽象放 `common`、实现放 `framework`，
`content` 只依赖抽象，运行期拿不到实现时退化为 `NoOpCacheService` ——
和 `AuthorNameResolver` 端口是同一套解法，连理由都一样：**让下层可独立编译与测试**。

**为什么不放 `@Cacheable` 注解。** 本项目的缓存策略里有三样东西注解表达不了：
「读不到就直连数据库且不缓存 null」「Redis 挂了要熔断而不是每个请求等满超时」
「列表要按命名空间整体失效」。注解会把这三条藏进配置里，而它们恰恰是最需要被 review 的部分。

**失效：一次 `INCR` 代替一次 `SCAN`。** 列表类缓存的 key 里带 `limit` 或查询条件，
取值不可枚举，想「删掉所有列表缓存」在 Redis 里只有两条路：`KEYS`（阻塞单线程，生产禁用）
或 `SCAN`（大 key 空间下代价不可控，还要处理游标与并发删除）。
第三条路是把版本号写进 key：写操作对 `inkos:article:list:version` 做一次 `INCR`，
旧 key 立刻不可达，随各自 TTL 自然消失。代价是短暂的内存冗余（最多多存一版），
换来 O(1)、不阻塞、不需要知道缓存过哪些 key。

> 版本号 key **不设 TTL**：一旦过期就从 0 重新开始，与它同时代的老条目可能被「复活」。
> 因此它走原生 `INCR` 而不是值序列化器 —— 顺带绕开了「裸数字没有类型 id、读不回来」的问题。

**计数类字段不参与失效。** 点赞、评论数变化极频繁，为它们作废整个列表缓存不划算。
判定标准是「这次变化会不会改变结果集」：改标题 / 分类 / 发布状态会（版本号 +1），
点赞数不会（交给 TTL）。**详情缓存的 TTL 就是计数滞后的上界**，这一点写在了
`CacheConstants.ARTICLE_DETAIL_TTL` 的注释里，而不是留在「以后有空再说」。

失效只在 **slug 已经拿在手里**时主动做（文章写操作、点赞切换）；
评论数变化需要额外查一次文章才能拿到 slug，因此刻意不做，交给 TTL。
规则是「不为了失效而多查一次库」，不是「尽量失效」。

**降级用熔断而非 try/catch。** 连接超时是 3s，逐个操作 catch 的后果是
Redis 挂掉时**每个请求都先等满 3 秒**才回源 —— 缓存故障被放大成全站变慢。
所以失败一次就开 30 秒窗口，窗口内直接判定不可用，不再真的去连。
代价是 Redis 恢复后最多 30 秒空窗，对缓存完全可以接受。
日志只在「从可用转入不可用」时打一条：故障期间每个请求一行会把线索刷掉。

**阅读计数的写放大。** 详情走缓存后，若计数器仍是「每个请求 +1」，
就变成读压力转移出数据库、写压力原样留下。前台传访客标识做去重窗口，
识别不出访客时 fail-open（每次都计）—— 宁可多记一次，也不要因为 Redis 故障丢掉全部计数。

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
| 换缓存后端 | 实现 `common` 的 `CacheService` 即可（本地 Caffeine、多级缓存都行）；内容域的 key 与失效规则集中在 `ContentCache`，不随实现变化 |
| 新增一处公开读缓存 | 在 `ContentCache` 里加 key 构造与失效方法，调用方只写 `getOrLoad`。**不要**在 Service 里直接拼 key，否则失效逻辑必然与读路径漂移 |
| 给权限查询加缓存 | `SysPermissionServiceImpl` 内部加缓存与失效；**前提是覆盖全部失效面**（角色-菜单授权、用户-角色变更、角色与用户删除），漏一个就是越权。见 README 技术债 |
| 数据库迁移 | 把 `spring.sql.init` 换成 Flyway，`schema.sql` 转为 `V1__init.sql` |
| 多数据源 | `MybatisPlusConfig` 的分页拦截器未指定 `DbType`，由 DataSource 自动识别 |

## 内容工作台与首屏主题

文章可管理性由 `CurrentUserProvider` 归属校验与操作权限共同决定；回收站状态 4 可恢复为草稿，不恢复历史逻辑删除。文章、分类、标签及语句写入在事务提交后失效相关公开缓存。

首页语句以 MySQL 为持久来源，Redis 为可降级读缓存。独立初始化器用 MySQL 连接级命名锁保护空表首次写入，提交后释放；包含逻辑删除数据时不重新初始化。预热逐模块隔离失败。

语句同时携带可选的首屏 `headline` 与 `description`，前端将两项作为一个主题展示。服务层强制成组校验，兼容旧客户端省略字段，显式空串表示移除主题。增量 DDL 串行部署，重复执行不覆写编辑内容，缓存键结构版本与新增字段一起升级。
