-- ============================================================================
-- 砚知 · 博客系统 —— MySQL 建表脚本
--
-- 目标：MySQL 8.0+（InnoDB / utf8mb4）
--   * 引擎与字符集在表级显式声明，不依赖服务端默认值 —— 默认值在不同环境往往不一致，
--     是「本地正常、线上乱码」的经典成因。
--   * 时间列统一用 DATETIME 而不是 TIMESTAMP：Java 侧是 LocalDateTime，
--     DATETIME 原样存取不做时区换算，也不会撞上 TIMESTAMP 的 2038 上限。
--   * 索引内联在 CREATE TABLE 中，而不是独立的 CREATE INDEX ——
--     MySQL 不支持 CREATE INDEX IF NOT EXISTS，内联才能保证脚本可重复执行。
--   * 本脚本同时被 dev profile 的 H2（MODE=MySQL 兼容模式）执行，用于零依赖启动。
--     需要 PostgreSQL 等其它方言时请另写脚本；跨方言表结构应由 Flyway/Liquibase 管理，
--     不要指望一份 DDL 通吃所有数据库。
--
-- 生产环境请改用 Flyway/Liquibase 管理版本，并把 spring.sql.init.mode 设为 never。
-- ============================================================================

-- ============================== 系统：用户与权限 ==============================

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(64)  NOT NULL                COMMENT '登录账号',
    nickname        VARCHAR(64)           DEFAULT NULL  COMMENT '昵称',
    email           VARCHAR(128)          DEFAULT NULL  COMMENT '邮箱',
    phone           VARCHAR(20)           DEFAULT NULL  COMMENT '手机号',
    password        VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密文',
    avatar          VARCHAR(512)          DEFAULT NULL  COMMENT '头像地址',
    bio             VARCHAR(512)          DEFAULT NULL  COMMENT '个人简介',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 禁用',
    last_login_time DATETIME              DEFAULT NULL  COMMENT '最后登录时间',
    last_login_ip   VARCHAR(64)           DEFAULT NULL  COMMENT '最后登录 IP',
    create_by       VARCHAR(64)           DEFAULT NULL  COMMENT '创建人',
    create_time     DATETIME              DEFAULT NULL  COMMENT '创建时间',
    update_by       VARCHAR(64)           DEFAULT NULL  COMMENT '更新人',
    update_time     DATETIME              DEFAULT NULL  COMMENT '更新时间',
    remark          VARCHAR(500)          DEFAULT NULL  COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_status (status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_name   VARCHAR(64) NOT NULL                COMMENT '角色名称',
    role_code   VARCHAR(64) NOT NULL                COMMENT '角色编码，Sa-Token 鉴权主体',
    sort_order  INT         NOT NULL DEFAULT 0      COMMENT '排序值',
    status      TINYINT     NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 停用',
    create_by   VARCHAR(64)          DEFAULT NULL  COMMENT '创建人',
    create_time DATETIME             DEFAULT NULL  COMMENT '创建时间',
    update_by   VARCHAR(64)          DEFAULT NULL  COMMENT '更新人',
    update_time DATETIME             DEFAULT NULL  COMMENT '更新时间',
    remark      VARCHAR(500)         DEFAULT NULL  COMMENT '备注',
    deleted     TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色';

CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单 ID，顶级为 0',
    menu_name   VARCHAR(64)  NOT NULL                COMMENT '菜单名称',
    path        VARCHAR(200)          DEFAULT NULL  COMMENT '前端路由地址',
    component   VARCHAR(255)          DEFAULT NULL  COMMENT '前端组件路径',
    perms       VARCHAR(128)          DEFAULT NULL  COMMENT '权限标识，如 content:article:add',
    icon        VARCHAR(64)           DEFAULT NULL  COMMENT '图标',
    menu_type   VARCHAR(2)   NOT NULL DEFAULT 'C'    COMMENT 'M 目录 / C 菜单 / F 按钮',
    sort_order  INT          NOT NULL DEFAULT 0      COMMENT '排序值',
    visible     TINYINT      NOT NULL DEFAULT 1      COMMENT '1 显示 / 0 隐藏',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用',
    create_by   VARCHAR(64)           DEFAULT NULL  COMMENT '创建人',
    create_time DATETIME              DEFAULT NULL  COMMENT '创建时间',
    update_by   VARCHAR(64)           DEFAULT NULL  COMMENT '更新人',
    update_time DATETIME              DEFAULT NULL  COMMENT '更新时间',
    remark      VARCHAR(500)          DEFAULT NULL  COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_sys_menu_parent (parent_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '菜单与权限项';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户-角色关联';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    menu_id BIGINT NOT NULL COMMENT '菜单 ID',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色-菜单关联';

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    title          VARCHAR(64)            DEFAULT NULL  COMMENT '模块标题',
    business_type  VARCHAR(32)            DEFAULT NULL  COMMENT '业务类型',
    method         VARCHAR(255)           DEFAULT NULL  COMMENT '方法全限定名',
    request_method VARCHAR(16)            DEFAULT NULL  COMMENT 'HTTP 方法',
    oper_name      VARCHAR(64)            DEFAULT NULL  COMMENT '操作人',
    oper_url       VARCHAR(255)           DEFAULT NULL  COMMENT '请求地址',
    oper_ip        VARCHAR(64)            DEFAULT NULL  COMMENT '操作 IP',
    oper_param     VARCHAR(2000)          DEFAULT NULL  COMMENT '请求参数（已脱敏）',
    json_result    VARCHAR(2000)          DEFAULT NULL  COMMENT '响应结果',
    status         TINYINT       NOT NULL DEFAULT 1      COMMENT '1 成功 / 0 失败',
    error_msg      VARCHAR(2000)          DEFAULT NULL  COMMENT '错误信息',
    cost_time      BIGINT                 DEFAULT NULL  COMMENT '耗时（毫秒）',
    oper_time      DATETIME               DEFAULT NULL  COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_sys_oper_log_time (oper_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志';

CREATE TABLE IF NOT EXISTS sys_login_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(64)           DEFAULT NULL  COMMENT '登录账号',
    ip         VARCHAR(64)           DEFAULT NULL  COMMENT '登录 IP',
    browser    VARCHAR(64)           DEFAULT NULL  COMMENT '浏览器',
    os         VARCHAR(64)           DEFAULT NULL  COMMENT '操作系统',
    status     TINYINT      NOT NULL DEFAULT 1      COMMENT '1 成功 / 0 失败',
    msg        VARCHAR(255)          DEFAULT NULL  COMMENT '提示信息',
    login_time DATETIME              DEFAULT NULL  COMMENT '登录时间',
    PRIMARY KEY (id),
    KEY idx_sys_login_log_time (login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '登录日志';

-- ============================== 内容：文章 / 分类 / 标签 / 评论 ==============================

CREATE TABLE IF NOT EXISTS cms_article (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    author_id       BIGINT       NOT NULL                COMMENT '作者用户 ID',
    category_id     BIGINT                DEFAULT NULL  COMMENT '分类 ID',
    title           VARCHAR(200) NOT NULL                COMMENT '标题',
    slug            VARCHAR(220) NOT NULL                COMMENT 'URL 标识',
    summary         VARCHAR(500)          DEFAULT NULL  COMMENT '摘要',
    cover_url       VARCHAR(512)          DEFAULT NULL  COMMENT '封面图',
    content_md      TEXT         NOT NULL                COMMENT 'Markdown 原文（唯一真相源）',
    content_html    TEXT                  DEFAULT NULL  COMMENT '渲染净化后的 HTML（派生缓存）',
    status          TINYINT      NOT NULL DEFAULT 0      COMMENT '0 草稿 1 待审 2 已发布 3 已下线 4 回收站',
    visibility      TINYINT      NOT NULL DEFAULT 0      COMMENT '0 公开 / 1 私密',
    word_count      INT          NOT NULL DEFAULT 0      COMMENT '字数',
    reading_minutes INT          NOT NULL DEFAULT 1      COMMENT '预计阅读分钟',
    view_count      BIGINT       NOT NULL DEFAULT 0      COMMENT '浏览量',
    like_count      INT          NOT NULL DEFAULT 0      COMMENT '点赞数',
    comment_count   INT          NOT NULL DEFAULT 0      COMMENT '评论数',
    quality_score   INT                   DEFAULT NULL  COMMENT '内容质量分（预留字段）',
    published_at    DATETIME              DEFAULT NULL  COMMENT '发布时间',
    create_by       VARCHAR(64)           DEFAULT NULL  COMMENT '创建人',
    create_time     DATETIME              DEFAULT NULL  COMMENT '创建时间',
    update_by       VARCHAR(64)           DEFAULT NULL  COMMENT '更新人',
    update_time     DATETIME              DEFAULT NULL  COMMENT '更新时间',
    remark          VARCHAR(500)          DEFAULT NULL  COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cms_article_slug (slug),
    KEY idx_cms_article_published (status, published_at, deleted),
    KEY idx_cms_article_author (author_id, create_time),
    KEY idx_cms_article_category (category_id, published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章';

CREATE TABLE IF NOT EXISTS cms_category (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id     BIGINT      NOT NULL DEFAULT 0      COMMENT '父分类 ID，顶级为 0',
    name          VARCHAR(64) NOT NULL                COMMENT '分类名称',
    slug          VARCHAR(96) NOT NULL                COMMENT 'URL 标识',
    description   VARCHAR(255)         DEFAULT NULL  COMMENT '描述',
    sort_order    INT         NOT NULL DEFAULT 0      COMMENT '排序值',
    article_count INT         NOT NULL DEFAULT 0      COMMENT '文章数（冗余计数）',
    status        TINYINT     NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用',
    create_by     VARCHAR(64)          DEFAULT NULL  COMMENT '创建人',
    create_time   DATETIME             DEFAULT NULL  COMMENT '创建时间',
    update_by     VARCHAR(64)          DEFAULT NULL  COMMENT '更新人',
    update_time   DATETIME             DEFAULT NULL  COMMENT '更新时间',
    remark        VARCHAR(500)         DEFAULT NULL  COMMENT '备注',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cms_category_slug (slug),
    KEY idx_cms_category_parent (parent_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分类';

CREATE TABLE IF NOT EXISTS cms_tag (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(48) NOT NULL                COMMENT '标签名',
    slug          VARCHAR(64) NOT NULL                COMMENT 'URL 标识',
    article_count INT         NOT NULL DEFAULT 0      COMMENT '文章数（冗余计数）',
    create_time   DATETIME             DEFAULT NULL  COMMENT '创建时间',
    update_time   DATETIME             DEFAULT NULL  COMMENT '更新时间',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cms_tag_slug (slug),
    KEY idx_cms_tag_count (article_count)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签';

CREATE TABLE IF NOT EXISTS cms_article_tag (
    article_id BIGINT NOT NULL COMMENT '文章 ID',
    tag_id     BIGINT NOT NULL COMMENT '标签 ID',
    PRIMARY KEY (article_id, tag_id),
    KEY idx_cms_article_tag_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章-标签关联';

CREATE TABLE IF NOT EXISTS cms_comment (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    article_id  BIGINT        NOT NULL                COMMENT '文章 ID',
    user_id     BIGINT                 DEFAULT NULL  COMMENT '评论人用户 ID，游客为空',
    guest_name  VARCHAR(48)            DEFAULT NULL  COMMENT '游客昵称',
    parent_id   BIGINT        NOT NULL DEFAULT 0      COMMENT '父评论 ID，顶级为 0',
    root_id     BIGINT        NOT NULL DEFAULT 0      COMMENT '顶层评论 ID，用于楼中楼聚合',
    content     VARCHAR(2000) NOT NULL                COMMENT '评论内容（纯文本）',
    status      TINYINT       NOT NULL DEFAULT 0      COMMENT '0 待审 1 通过 2 拒绝 3 垃圾',
    like_count  INT           NOT NULL DEFAULT 0      COMMENT '点赞数',
    create_by   VARCHAR(64)            DEFAULT NULL  COMMENT '创建人',
    create_time DATETIME               DEFAULT NULL  COMMENT '创建时间',
    update_by   VARCHAR(64)            DEFAULT NULL  COMMENT '更新人',
    update_time DATETIME               DEFAULT NULL  COMMENT '更新时间',
    remark      VARCHAR(500)           DEFAULT NULL  COMMENT '备注',
    deleted     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_cms_comment_article (article_id, status, create_time),
    KEY idx_cms_comment_root (root_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评论';

CREATE TABLE IF NOT EXISTS cms_quote (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    content     VARCHAR(180) NOT NULL                COMMENT '语句正文',
    attribution VARCHAR(80)           DEFAULT NULL  COMMENT '署名或出处',
    sort_order  INT          NOT NULL DEFAULT 0      COMMENT '展示顺序',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用',
    create_by   VARCHAR(64)           DEFAULT NULL  COMMENT '创建人',
    create_time DATETIME              DEFAULT NULL  COMMENT '创建时间',
    update_by   VARCHAR(64)           DEFAULT NULL  COMMENT '更新人',
    update_time DATETIME              DEFAULT NULL  COMMENT '更新时间',
    remark      VARCHAR(500)          DEFAULT NULL  COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_cms_quote_status_sort (status, sort_order, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '首页语句库';
