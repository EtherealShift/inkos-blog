package com.inkos.ai.facade;

import com.inkos.ai.client.LlmClient;
import com.inkos.ai.client.LlmRequest;
import com.inkos.ai.client.LlmResponse;
import com.inkos.ai.dto.AiChatRequest;
import com.inkos.ai.dto.AiChatResponse;
import com.inkos.ai.dto.ArticleAiMeta;
import com.inkos.ai.enums.AiRole;
import com.inkos.ai.enums.AiScene;
import com.inkos.ai.exception.AiUnavailableException;
import com.inkos.ai.prompt.PromptRegistry;
import com.inkos.ai.router.ModelRouter;
import com.inkos.ai.service.AiConversationService;
import com.inkos.ai.service.AiUsageService;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import com.inkos.common.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 智能层门面实现：负责「渲染 Prompt → 选模型 → 调用 → 解析 → 计量落库」这条固定流水线。
 *
 * <p>可用性设计：计量与会话落库失败**不**影响本次回答（只记 warn 日志），
 * 但模型调用失败必须向上抛 {@link AiUnavailableException} —— 该抛的错要抛，才能让上层降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFacadeImpl implements AiFacade {

    /** 送进 Prompt 的正文上限（字符），防止上下文过长烧钱 */
    private static final int META_CONTENT_LIMIT = 3000;
    /** 摘要最大字符数 */
    private static final int SUMMARY_MAX_CHARS = 200;
    /** SEO 描述最大字符数 */
    private static final int SEO_DESCRIPTION_MAX_CHARS = 155;
    /** 关键词个数上限 */
    private static final int KEYWORD_MAX_COUNT = 5;
    /** 标签个数上限 */
    private static final int TAG_MAX_COUNT = 8;
    /** 标签单词最大长度，超过视为模型输出的句子而非标签 */
    private static final int TAG_MAX_CHARS = 24;
    /** 骨架阶段固定质量分（真实实现应由模型给出并做服务端二次校验） */
    private static final int DEFAULT_QUALITY_SCORE = 80;
    /** 骨架阶段暂无检索结果 */
    private static final String NO_RETRIEVAL_CONTEXT = "(骨架阶段暂无检索结果)";
    /** 成本估算：输入 1 微元/千 token，输出 2 微元/千 token（仅用于骨架联调，真实单价按供应商配置） */
    private static final long PROMPT_PRICE_MICRO_PER_1K = 1_000L;
    private static final long COMPLETION_PRICE_MICRO_PER_1K = 2_000L;

    private static final List<String> DEFAULT_IMPROVEMENT_TIPS = List.of(
            "为关键结论补充可运行的示例代码或数据佐证",
            "把超过 5 行的长段落拆成 3~4 行，提升移动端可读性",
            "在开头补一段“读完能得到什么”，降低读者的判断成本");

    /** 标签分隔符：中英文逗号、顿号、分号、换行 */
    private static final Pattern TAG_SEPARATOR = Pattern.compile("[，,、;；\\r\\n]+");
    /** 列表前缀：-、*、•、1.、1、1) */
    private static final Pattern LIST_PREFIX = Pattern.compile("^\\s*(?:[-*•]|\\d+[.、)])\\s*");
    /** 需要剥掉的包裹符号 */
    private static final Pattern WRAPPER = Pattern.compile("^[\"'“”‘’\\[\\]【】]+|[\"'“”‘’\\[\\]【】]+$");
    /** 摘要/标签前缀词 */
    private static final Pattern SUMMARY_PREFIX = Pattern.compile("^\\s*(摘要|总结|关键词|标签)[:：]\\s*");

    private final ModelRouter modelRouter;
    private final PromptRegistry promptRegistry;
    private final AiUsageService aiUsageService;
    private final AiConversationService aiConversationService;

    @Override
    public ArticleAiMeta generateMeta(String title, String markdown) {
        String safeTitle = StrUtils.defaultIfBlank(title, "未命名文章");
        try {
            String content = truncate(StrUtils.defaultString(markdown), META_CONTENT_LIMIT);
            Map<String, String> variables = Map.of("title", safeTitle, "content", content);
            LlmResponse summaryResp = callModel(null, modelRouter.route(AiScene.SUMMARY), AiScene.SUMMARY,
                    PromptRegistry.KEY_ARTICLE_SUMMARY, variables, 0.3D, 1200);
            LlmResponse tagResp = callModel(null, modelRouter.route(AiScene.TAG), AiScene.TAG,
                    PromptRegistry.KEY_ARTICLE_TAG, variables, 0.2D, 512);
            return buildMeta(safeTitle, markdown, summaryResp, tagResp);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("生成文章 AI 元信息失败：title={}", safeTitle, e);
            throw new AiUnavailableException("生成文章 AI 元信息失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String assist(String action, String selection, String context) {
        // 动作合法性是入参问题，直接以 400 抛出，不参与降级
        String actionDesc = promptRegistry.writingInstruction(action);
        String normalizedAction = action.trim().toLowerCase();
        try {
            String prompt = promptRegistry.render(PromptRegistry.KEY_WRITING_ASSIST, Map.of(
                    "action", normalizedAction,
                    "actionDesc", actionDesc,
                    "selection", StrUtils.defaultString(selection),
                    "context", StrUtils.defaultString(context)));
            LlmClient client = modelRouter.route(AiScene.WRITING);
            LlmResponse response = callModel(null, client, AiScene.WRITING, prompt, 0.7D, 1500);
            String content = StrUtils.defaultString(response.content()).trim();
            if (StrUtils.isBlank(content)) {
                throw new AiUnavailableException("写作助手返回内容为空");
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("写作助手调用失败：action={}", normalizedAction, e);
            throw new AiUnavailableException("写作助手调用失败：" + e.getMessage(), e);
        }
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, Long userId) {
        if (request == null || StrUtils.isBlank(request.question())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "问题不能为空");
        }
        String question = request.question().trim();
        String sessionId = StrUtils.isNotBlank(request.sessionId())
                ? request.sessionId().trim()
                : UUID.randomUUID().toString().replace("-", "");
        try {
            // 先查配额再调用，超限直接拒绝
            aiUsageService.checkQuota(userId, AiScene.QA);
            LlmClient client = modelRouter.route(AiScene.QA);
            String prompt = promptRegistry.render(PromptRegistry.KEY_QA_RAG,
                    Map.of("context", NO_RETRIEVAL_CONTEXT, "question", question));
            long start = System.currentTimeMillis();
            LlmResponse response = callModel(userId, client, AiScene.QA, prompt, 0.2D, 1500);
            long latencyMs = System.currentTimeMillis() - start;
            String answer = StrUtils.defaultString(response.content()).trim();
            if (StrUtils.isBlank(answer)) {
                throw new AiUnavailableException("问答返回内容为空");
            }
            // 引用校验依赖检索结果，骨架阶段为空列表
            List<String> citations = List.of();
            persistQuietly(userId, sessionId, question, answer, response);
            log.info("站内问答完成：user={} sessionId={} provider={} model={} promptTokens={} completionTokens={} latencyMs={}",
                    userId, sessionId, client.provider(), response.model(),
                    response.promptTokens(), response.completionTokens(), latencyMs);
            return new AiChatResponse(answer, sessionId, citations, response.model(),
                    response.promptTokens(), response.completionTokens(), latencyMs);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("站内问答失败：user={} sessionId={}", userId, sessionId, e);
            throw new AiUnavailableException("站内问答失败：" + e.getMessage(), e);
        }
    }

    // ==================== 内部流水线 ====================

    /** 渲染模板后调用模型，并做计量；模型异常原样向上抛。 */
    private LlmResponse callModel(Long userId, LlmClient client, AiScene scene, String templateKey,
                                  Map<String, String> variables, double temperature, int maxTokens) {
        return callModel(userId, client, scene, promptRegistry.render(templateKey, variables), temperature, maxTokens);
    }

    private LlmResponse callModel(Long userId, LlmClient client, AiScene scene, String prompt,
                                  double temperature, int maxTokens) {
        long start = System.currentTimeMillis();
        LlmResponse response = client.chat(LlmRequest.builder()
                .systemPrompt(PromptRegistry.SYSTEM_PERSONA)
                .userPrompt(prompt)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build());
        long latencyMs = System.currentTimeMillis() - start;
        log.info("AI 调用完成：scene={} provider={} model={} promptTokens={} completionTokens={} latencyMs={}",
                scene.getCode(), client.provider(), response.model(),
                response.promptTokens(), response.completionTokens(), latencyMs);
        recordUsageQuietly(userId, scene, response);
        return response;
    }

    /**
     * 组装元信息：解析模型输出，解析不出来就走兜底，保证作者永远拿得到可用结果。
     */
    private ArticleAiMeta buildMeta(String title, String markdown, LlmResponse summaryResp, LlmResponse tagResp) {
        String summary = cleanSummary(summaryResp == null ? null : summaryResp.content());
        if (StrUtils.isBlank(summary)) {
            log.warn("摘要解析失败，回退为正文截断：title={}", title);
            summary = TextUtils.excerpt(StrUtils.defaultString(markdown), 120);
        }
        List<String> tags = parseTags(tagResp == null ? null : tagResp.content());
        List<String> keywords = tags.size() > KEYWORD_MAX_COUNT
                ? List.copyOf(tags.subList(0, KEYWORD_MAX_COUNT))
                : tags;
        return new ArticleAiMeta(summary, keywords, StrUtils.slugify(title),
                TextUtils.excerpt(summary, SEO_DESCRIPTION_MAX_CHARS), tags,
                DEFAULT_QUALITY_SCORE, DEFAULT_IMPROVEMENT_TIPS);
    }

    /** 清洗摘要：去 Markdown、去前缀词、限长。 */
    private String cleanSummary(String raw) {
        if (StrUtils.isBlank(raw)) {
            return "";
        }
        String text = TextUtils.stripMarkdown(raw).trim();
        String previous;
        do {
            previous = text;
            text = SUMMARY_PREFIX.matcher(text).replaceFirst("").trim();
        } while (!text.equals(previous));
        if (text.length() > SUMMARY_MAX_CHARS) {
            text = text.substring(0, SUMMARY_MAX_CHARS) + "…";
        }
        return text;
    }

    /** 解析标签：按逗号/顿号/分号/换行切分，剥列表前缀与引号，去重限长。 */
    private List<String> parseTags(String raw) {
        if (StrUtils.isBlank(raw)) {
            return List.of();
        }
        String text = SUMMARY_PREFIX.matcher(raw.trim()).replaceFirst("").trim();
        List<String> tags = new ArrayList<>(TAG_MAX_COUNT);
        for (String part : TAG_SEPARATOR.split(text)) {
            String tag = WRAPPER.matcher(LIST_PREFIX.matcher(part).replaceFirst("").trim()).replaceAll("").trim();
            if (StrUtils.isBlank(tag) || tag.length() > TAG_MAX_CHARS) {
                continue;
            }
            boolean duplicated = tags.stream().anyMatch(exist -> exist.equalsIgnoreCase(tag));
            if (!duplicated) {
                tags.add(tag);
            }
            if (tags.size() >= TAG_MAX_COUNT) {
                break;
            }
        }
        return List.copyOf(tags);
    }

    /**
     * 会话与消息落库。
     *
     * <p>刻意吞掉异常：多轮记忆属于增强能力，写库失败不应让用户拿不到回答。
     */
    private void persistQuietly(Long userId, String sessionId, String question, String answer, LlmResponse response) {
        try {
            Long conversationId = aiConversationService.ensureConversation(userId, sessionId, AiScene.QA, question);
            aiConversationService.appendMessage(conversationId, AiRole.USER, question, null, null);
            aiConversationService.appendMessage(conversationId, AiRole.ASSISTANT, answer, null, response);
        } catch (Exception e) {
            log.warn("会话落库失败（不影响本次回答）：sessionId={} 原因={}", sessionId, e.getMessage());
        }
    }

    /** 计量落库，失败只告警：算不清账也不能让功能不可用。 */
    private void recordUsageQuietly(Long userId, AiScene scene, LlmResponse response) {
        if (response == null) {
            return;
        }
        long costMicro = estimateCostMicro(response.promptTokens(), response.completionTokens());
        try {
            aiUsageService.record(userId, scene, response.model(),
                    response.promptTokens(), response.completionTokens(), costMicro);
        } catch (Exception e) {
            log.warn("AI 用量落库失败：user={} scene={} 原因={}", userId, scene.getCode(), e.getMessage());
        }
    }

    /** 粗估成本（微元）：按千 token 单价线性折算。 */
    private long estimateCostMicro(int promptTokens, int completionTokens) {
        long micro = (long) promptTokens * PROMPT_PRICE_MICRO_PER_1K / 1000L
                + (long) completionTokens * COMPLETION_PRICE_MICRO_PER_1K / 1000L;
        return Math.max(0L, micro);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
