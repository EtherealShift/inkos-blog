# ============================================================================
# inkos-ai 模块 —— 暂时移出构建（Parked）
#
# 状态：代码完整保留，但**不参与 Maven 构建**。
#   - 父 POM 的 <modules> 与 <dependencyManagement> 中相关条目已注释
#   - inkos-admin 对它的依赖已注释
#   - inkos-admin 的 AiController 已删除
#   - cms/sys 建表脚本中的 ai_* 表已移除
#
# 为什么保留而不是删除：模块内的客户端抽象、模型路由、Prompt 注册中心是按生产标准写的，
# 将来重启 AI 功能时可以直接复用，不必重写。
#
# ---------------------------------------------------------------------------
# 需要重新启用时，按顺序做四件事：
#
# 1) 根 pom.xml：放开
#      <module>inkos-ai</module>
#    以及 dependencyManagement 中的 inkos-ai 依赖项
#
# 2) inkos-admin/pom.xml：放开
#      <dependency>
#          <groupId>com.inkos</groupId>
#          <artifactId>inkos-ai</artifactId>
#      </dependency>
#
# 3) inkos-admin/resources/db/schema.sql：补回 ai_conversation / ai_message / ai_usage 三张表
#    （DDL 见 git 历史或架构设计文档第 4.2 节）
#
# 4) 恢复 controllers/AiController.java，并在 DevDataInitializer 中补回「智能助手」菜单与 ai:chat 权限码
#
# ---------------------------------------------------------------------------
# 重启 AI 前必须先想清楚的三件事（来自架构设计文档第 5 章）：
#
#   a) 向量检索方案：原设计用 PostgreSQL + pgvector。现已改为 MySQL，
#      需要二选一 —— 独立向量库（Qdrant/Milvus）或 MySQL 内的向量索引方案。
#      这会影响 article_chunk 表的落点。
#   b) 中文全文检索：原设计用 PG 的 zhparser，MySQL 侧需改用 ngram 全文索引
#      或外接 Elasticsearch（推荐后者，语义检索与 BM25 混合更可控）。
#   c) 超管权限通配符：Sa-Token 的权限项按正则匹配，超管必须返回单个 `*`。
#      若新增两段式权限码（如 ai:chat），`*:*:*` 是匹配不到的 —— 这个坑已经踩过一次。
# ============================================================================
