package com.inkos.ai.facade;

import com.inkos.ai.dto.AiChatRequest;
import com.inkos.ai.dto.AiChatResponse;
import com.inkos.ai.dto.ArticleAiMeta;

/**
 * 智能层门面：业务模块唯一需要依赖的 AI 接口。
 *
 * <p>设计原则：业务代码只依赖本接口，不感知模型、Prompt、路由等细节，
 * 因此「今天 OpenAI、明天本地 Qwen」只需要改配置。
 *
 * <p>异常约定：AI 能力不可用时抛 {@code AiUnavailableException}（业务码 50301），
 * 上层据此做功能级降级（隐藏入口），而不是把 500 抛给用户。
 */
public interface AiFacade {

    /**
     * 生成文章 AI 元信息（摘要 / 关键词 / 标签 / SEO 描述 / 质量分）。
     *
     * <p>结果只是候选值，作者可编辑后再落库；正文内部会截断，避免超长 Prompt。
     *
     * @param title    文章标题
     * @param markdown 文章正文（Markdown）
     * @return 结构化元信息
     */
    ArticleAiMeta generateMeta(String title, String markdown);

    /**
     * 写作助手：outline / expand / polish / continue / title。
     *
     * @param action    动作，取值见 {@code PromptRegistry}
     * @param selection 选中的原文
     * @param context   已有上下文
     * @return 生成结果正文
     */
    String assist(String action, String selection, String context);

    /**
     * 站内问答（RAG）。
     *
     * <p>骨架阶段检索结果为空，模板会注入占位上下文并返回空引用列表；
     * 接入向量检索后只需替换 context 的组装来源。
     *
     * @param request 问答请求
     * @param userId  当前用户 id，可为 null（匿名）
     * @return 问答响应（同步返回，流式由上层 SSE 扩展）
     */
    AiChatResponse chat(AiChatRequest request, Long userId);
}
