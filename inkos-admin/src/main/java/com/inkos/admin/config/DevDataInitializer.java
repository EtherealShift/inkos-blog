package com.inkos.admin.config;

import com.inkos.content.dto.ArticleForm;
import com.inkos.content.dto.CategoryForm;
import com.inkos.content.service.ArticleService;
import com.inkos.content.service.CategoryService;
import com.inkos.content.service.TagService;
import com.inkos.system.dto.SysUserForm;
import com.inkos.system.entity.SysMenu;
import com.inkos.system.entity.SysRole;
import com.inkos.system.service.SysMenuService;
import com.inkos.system.service.SysRoleService;
import com.inkos.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发环境种子数据。
 *
 * <p>为什么用 Java 而不是 {@code data.sql}：
 * <ol>
 *   <li>密码需要 BCrypt 加密，不能把明文或写死的哈希塞进 SQL；</li>
 *   <li>菜单/角色/关联表之间存在 ID 依赖，Java 里能拿到自增主键，SQL 里要写一堆子查询；</li>
 *   <li>种子数据必须幂等：生产与开发共用同一套建表脚本，重复执行不能产生脏数据。</li>
 * </ol>
 *
 * <p>仅在 {@code dev} profile 生效，且已有 admin 用户时直接跳过，可重复启动。
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD_ADMIN = "admin123";
    private static final String DEFAULT_PASSWORD_AUTHOR = "author123";

    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final SysUserService userService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final ArticleService articleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (userService.getByUsername("admin") != null) {
            log.info("检测到已有种子数据，跳过初始化");
            return;
        }
        log.info("开始初始化开发环境种子数据 ...");

        SysRole adminRole = role("超级管理员", "ROLE_ADMIN", 1, "拥有全部权限");
        SysRole authorRole = role("作者", "ROLE_AUTHOR", 2, "可管理自己的文章");
        role("读者", "ROLE_READER", 3, "仅可浏览与评论");

        List<SysMenu> authorMenus = new ArrayList<>();
        List<SysMenu> allMenus = buildMenus(authorMenus);

        roleService.assignMenus(adminRole.getId(), allMenus.stream().map(SysMenu::getId).toList());
        roleService.assignMenus(authorRole.getId(), authorMenus.stream().map(SysMenu::getId).toList());

        Long adminId = createUser("admin", "砚知管理员", "admin@inkos.dev", DEFAULT_PASSWORD_ADMIN,
                List.of(adminRole.getId()));
        createUser("author", "示例作者", "author@inkos.dev", DEFAULT_PASSWORD_AUTHOR,
                List.of(authorRole.getId()));

        seedContent();
        // Default quotes are independently initialized in every environment.

        log.info("种子数据初始化完成：admin/{}  author/{}  userId={}",
                DEFAULT_PASSWORD_ADMIN, DEFAULT_PASSWORD_AUTHOR, adminId);
    }

    // ==================== 角色 ====================

    private SysRole role(String name, String code, int sort, String remark) {
        SysRole role = new SysRole();
        role.setRoleName(name);
        role.setRoleCode(code);
        role.setSortOrder(sort);
        role.setStatus(1);
        role.setRemark(remark);
        roleService.save(role);
        return role;
    }

    // ==================== 菜单 ====================

    /**
     * 构建完整菜单树，同时把「作者角色可见」的节点收集到 authorMenus。
     *
     * <p>返回扁平的全部菜单，供超管授权使用。
     */
    private List<SysMenu> buildMenus(List<SysMenu> authorMenus) {
        List<SysMenu> all = new ArrayList<>();

        // ---- 系统管理（仅超管）----
        SysMenu system = dir(0L, "系统管理", "/system", "Setting", 1);
        all.add(system);

        SysMenu userPage = page(system.getId(), "用户管理", "user", "system/user/index", "system:user:list", 1);
        all.add(userPage);
        all.addAll(buttons(userPage.getId(), "用户管理", "system:user",
                List.of("query:查询", "add:新增", "edit:修改", "remove:删除", "resetPwd:重置密码")));

        SysMenu rolePage = page(system.getId(), "角色管理", "role", "system/role/index", "system:role:list", 2);
        all.add(rolePage);
        all.addAll(buttons(rolePage.getId(), "角色管理", "system:role",
                List.of("query:查询", "add:新增", "edit:修改", "remove:删除")));

        SysMenu menuPage = page(system.getId(), "菜单管理", "menu", "system/menu/index", "system:menu:list", 3);
        all.add(menuPage);
        all.addAll(buttons(menuPage.getId(), "菜单管理", "system:menu",
                List.of("query:查询", "add:新增", "edit:修改", "remove:删除")));

        // ---- 内容管理（超管 + 作者）----
        SysMenu content = dir(0L, "内容管理", "/content", "Document", 2);
        all.add(content);
        authorMenus.add(content);

        SysMenu articlePage = page(content.getId(), "文章管理", "article", "content/article/index",
                "content:article:list", 1);
        all.add(articlePage);
        authorMenus.add(articlePage);
        List<SysMenu> articleButtons = buttons(articlePage.getId(), "文章管理", "content:article",
                List.of("query:查询", "add:新增", "edit:修改", "remove:删除", "publish:发布", "restore:恢复"));
        all.addAll(articleButtons);
        authorMenus.addAll(articleButtons);

        SysMenu categoryPage = page(content.getId(), "分类管理", "category", "content/category/index",
                "content:category:list", 2);
        all.add(categoryPage);
        authorMenus.add(categoryPage);
        List<SysMenu> categoryButtons = buttons(categoryPage.getId(), "分类管理", "content:category",
                List.of("add:新增", "edit:修改", "remove:删除"));
        all.addAll(categoryButtons);
        authorMenus.addAll(categoryButtons);

        SysMenu quotePage = page(content.getId(), "语句管理", "quote", "content/quote/index",
                "content:quote:list", 3);
        all.add(quotePage);
        authorMenus.add(quotePage);
        List<SysMenu> quoteButtons = buttons(quotePage.getId(), "语句管理", "content:quote",
                List.of("add:新增", "edit:修改", "remove:删除"));
        all.addAll(quoteButtons);
        authorMenus.addAll(quoteButtons);

        SysMenu tagPage = page(content.getId(), "标签管理", "tag", "content/tag/index", "content:tag:list", 4);
        all.add(tagPage); authorMenus.add(tagPage);
        List<SysMenu> tagButtons = buttons(tagPage.getId(), "标签管理", "content:tag", List.of("add:新增", "edit:修改", "remove:删除"));
        all.addAll(tagButtons); authorMenus.addAll(tagButtons);
        return all;
    }

    private SysMenu dir(Long parentId, String name, String path, String icon, int sort) {
        return saveMenu(parentId, name, "M", path, null, null, icon, sort);
    }

    private SysMenu page(Long parentId, String name, String path, String component, String perms, int sort) {
        return saveMenu(parentId, name, "C", path, component, perms, null, sort);
    }

    private SysMenu button(Long parentId, String name, String perms, int sort) {
        return saveMenu(parentId, name, "F", null, null, perms, null, sort);
    }

    /**
     * 批量生成按钮权限。
     *
     * @param specs 形如 {@code "add:新增"} 的「权限后缀:按钮名」
     */
    private List<SysMenu> buttons(Long parentId, String moduleName, String permsPrefix, List<String> specs) {
        List<SysMenu> menus = new ArrayList<>(specs.size());
        int sort = 1;
        for (String spec : specs) {
            String[] parts = spec.split(":", 2);
            menus.add(saveMenu(parentId, parts[1], "F", null, null, permsPrefix + ":" + parts[0], null, sort++));
        }
        log.debug("为 [{}] 生成 {} 个按钮权限", moduleName, menus.size());
        return menus;
    }

    private SysMenu saveMenu(Long parentId, String name, String type, String path, String component,
                             String perms, String icon, int sort) {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setPerms(perms);
        menu.setIcon(icon);
        menu.setSortOrder(sort);
        menu.setVisible(1);
        menu.setStatus(1);
        menuService.save(menu);
        return menu;
    }

    // ==================== 用户 ====================

    private Long createUser(String username, String nickname, String email, String password, List<Long> roleIds) {
        SysUserForm form = new SysUserForm();
        form.setUsername(username);
        form.setNickname(nickname);
        form.setEmail(email);
        form.setPassword(password);
        form.setStatus(1);
        form.setRoleIds(roleIds);
        return userService.createUser(form);
    }

    // ==================== 内容 ====================

    private void seedContent() {
        Long tech = category("技术", "tech", "技术相关", 0L, 1);
        Long backend = category("后端", "backend", "服务端开发", tech, 1);
        category("前端", "frontend", "浏览器与界面", tech, 2);
        category("生活", "life", "生活随笔", 0L, 2);

        List<Long> javaTags = tagService.resolveTagIds(List.of("Java", "Spring Boot", "架构设计"));
        List<Long> dbTags = tagService.resolveTagIds(List.of("MySQL", "MyBatis-Plus", "性能优化"));
        List<Long> opsTags = tagService.resolveTagIds(List.of("MySQL", "数据库迁移", "架构设计"));

        Long a1 = articleService.create(new ArticleForm(null, backend, "为什么我把博客系统做成了模块化单体",
                "why-modular-monolith",
                "从分层边界、依赖方向到拆分时机，讲清楚这个后端骨架的取舍。",
                null,
                """
                # 为什么我把博客系统做成了模块化单体

                微服务不是起点，而是**结果**。当你只有 1~5 个开发者时，先把边界画对，
                比先把进程拆开重要得多。

                ## 分层与依赖方向

                ```
                admin      表现层：控制器、DTO 装配、启动
                framework  框架层：Sa-Token、全局异常、MyBatis-Plus、AOP
                system     系统层：用户、角色、菜单权限
                content    内容层：文章、分类、标签、评论
                common     基础层：统一响应、异常、常量、工具
                ```

                依赖只能自上而下。`content` 需要作者昵称，但它**不依赖** `system`，
                而是声明一个 `AuthorNameResolver` 端口，由 `admin` 层提供适配器 ——
                这才叫边界，否则只是把一个大泥球分成了几个包。

                ## 什么时候才该拆

                | 信号 | 动作 |
                |---|---|
                | 导出/报表任务拖慢 Web 响应 | 把批处理拆成独立进程 |
                | 检索流量与主体差异大 | 抽出 search-service |
                | 服务数 > 5 | 引入网关统一鉴权 |

                在那之前，单体 + 清晰的模块边界，交付速度完胜。
                """,
                javaTags, 0));

        Long a2 = articleService.create(new ArticleForm(null, backend, "MyBatis-Plus 3.5.17 升级踩坑记",
                "mybatis-plus-3517-notes",
                "包名重构、JSqlParser 拆分、逻辑删除与自动填充的实测记录。",
                null,
                """
                # MyBatis-Plus 3.5.17 升级踩坑记

                ## 坑一：包名变了

                `IService` / `ServiceImpl` 从 `extension.service` 迁到了：

                ```java
                import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
                ```

                照抄旧教程会直接编译失败。

                ## 坑二：JSqlParser 被拆出去了

                分页拦截器现在位于 `mybatis-plus-jsqlparser`：

                ```xml
                <dependency>
                    <groupId>com.baomidou</groupId>
                    <artifactId>mybatis-plus-jsqlparser</artifactId>
                </dependency>
                ```

                不加这个依赖，`PaginationInnerInterceptor` 会 ClassNotFound。

                ## 坑三：JDK 23+ 的注解处理器

                javac 从 JDK 23 起不再隐式执行 classpath 上的注解处理器，
                Lombok 必须显式声明，否则 `@Data`、`@Slf4j` 全部静默失效：

                ```xml
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.46</version>
                    </path>
                </annotationProcessorPaths>
                ```
                """,
                dbTags, 0));

        Long a3 = articleService.create(new ArticleForm(null, backend, "从 PostgreSQL 换到 MySQL 的迁移清单",
                "postgresql-to-mysql-checklist",
                "方言差异、索引写法、时间类型与自增主键，逐条对照。",
                null,
                """
                # 从 PostgreSQL 换到 MySQL 的迁移清单

                ## 一、主键自增

                | PostgreSQL | MySQL |
                |---|---|
                | `BIGSERIAL PRIMARY KEY` | `BIGINT NOT NULL AUTO_INCREMENT` + `PRIMARY KEY (id)` |
                | 序列独立于表 | 自增值属于表，`AUTO_INCREMENT = n` 可重置 |

                两者对 MyBatis-Plus 的 `IdType.AUTO` 都友好 —— 它走的是 JDBC
                `getGeneratedKeys()`，不依赖具体语法。

                ## 二、索引必须内联

                MySQL **不支持** `CREATE INDEX IF NOT EXISTS`：

                ```sql
                -- PostgreSQL 可以反复执行
                CREATE INDEX IF NOT EXISTS idx_x ON t (c);

                -- MySQL 只能内联，才能保证脚本可重复执行
                CREATE TABLE t (
                    ...,
                    KEY idx_x (c)
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
                ```

                这是唯一一处真正影响脚本结构的差异 —— 其余都能靠改写绕过。

                ## 三、时间类型选 DATETIME

                Java 侧字段是 `LocalDateTime`，对应 MySQL 就用 `DATETIME`：

                - `DATETIME` 原样存取，不做时区换算；`TIMESTAMP` 会按会话时区转换，
                  跨时区部署时同一个值读出来可能不一样。
                - `TIMESTAMP` 有 2038 年上限，`DATETIME` 到 9999 年。

                ## 四、字符集一定要显式声明

                ```sql
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
                ```

                依赖服务端默认值是「本地正常、线上乱码」的经典成因。
                排序规则用 `general_ci` 而不是 MySQL 8 默认的 `0900_ai_ci`，
                是为了让脚本在 5.7 上也能跑。
                """,
                opsTags, 0));

        articleService.publish(a1);
        articleService.publish(a2);
        articleService.publish(a3);
        log.info("已写入 3 篇示例文章");
    }

    private Long category(String name, String slug, String description, Long parentId, int sort) {
        return categoryService.create(new CategoryForm(null, parentId, name, slug, description, sort, 1));
    }

}
