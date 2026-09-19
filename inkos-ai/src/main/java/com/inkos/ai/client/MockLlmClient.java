package com.inkos.ai.client;

import com.inkos.ai.config.AiProperties;
import com.inkos.ai.exception.AiUnavailableException;
import com.inkos.common.util.StrUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地 Mock 客户端：默认启用，保证「零 API Key、零外网」也能跑通整条链路。
 *
 * <p>行为约定：
 * <ul>
 *   <li>不发任何网络请求，按 {@code userPrompt} 中的任务标记 / 关键词返回确定性文本；</li>
 *   <li>固定睡眠 {@value #MOCK_LATENCY_MS} ms，模拟真实调用的延迟特征；</li>
 *   <li>token 数按字符数粗估（中英混排约 2 字符 1 token）。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "inkos.ai.enabled", havingValue = "false", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    /** 供应商标识 */
    public static final String PROVIDER = "mock";

    /** 默认模型名 */
    public static final String DEFAULT_MODEL = "mock-llm-v1";

    /** 模拟延迟（毫秒） */
    private static final long MOCK_LATENCY_MS = 30L;

    /** 每 token 粗估字符数 */
    private static final int CHARS_PER_TOKEN = 2;

    private static final String TASK_TAG_MARK = "【任务】抽取标签";
    private static final String TASK_SUMMARY_MARK = "【任务】生成摘要";
    private static final String WRITING_TASK_MARK = "【任务类型】";
    private static final String SECTION_QUESTION = "【用户问题】";
    private static final String ANSWER_MARK = "【回答】";
    private static final String SECTION_TITLE = "【文章标题】";
    private static final String SECTION_CONTENT = "【文章正文】";

    /** 抽取标签的固定假响应，逗号分隔 */
    private static final String MOCK_TAGS = "Java, Spring Boot, 微服务, 可观测性, 后端架构";

    private final AiProperties aiProperties;

    public MockLlmClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        log.warn("智能层运行在 Mock 模式下（inkos.ai.enabled=false），不会调用任何真实大模型");
    }

    @Override
    public String provider() {
        return StrUtils.isNotBlank(aiProperties.getProvider()) ? aiProperties.getProvider() : PROVIDER;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String prompt = request == null || request.userPrompt() == null ? "" : request.userPrompt();
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(MOCK_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Mock 调用被中断", e);
        }
        String content = reply(prompt);
        long latencyMs = Math.max(MOCK_LATENCY_MS, System.currentTimeMillis() - start);
        int promptTokens = estimateTokens(prompt);
        int completionTokens = estimateTokens(content);
        String model = request != null && StrUtils.isNotBlank(request.model()) ? request.model() : DEFAULT_MODEL;
        log.debug("Mock 生成完成 provider={} model={} promptTokens={} completionTokens={} latencyMs={}",
                provider(), model, promptTokens, completionTokens, latencyMs);
        return new LlmResponse(content, model, promptTokens, completionTokens, latencyMs);
    }

    /**
     * 按任务标记优先、关键词兜底决定返回内容。
     *
     * <p>先判任务标记是为了避免「正文里恰好出现摘要/标签二字」导致的误判。
     */
    private String reply(String prompt) {
        if (prompt.contains(TASK_TAG_MARK) || prompt.contains("标签")) {
            return MOCK_TAGS;
        }
        if (prompt.contains(TASK_SUMMARY_MARK) || prompt.contains("摘要")) {
            String title = StrUtils.defaultIfBlank(section(prompt, SECTION_TITLE, SECTION_CONTENT), "这篇文章");
            return "本文围绕「" + title + "」展开，先交代了要解决的背景问题，再给出可落地的实现思路与关键取舍，"
                    + "最后总结了实践中的常见坑与自检方式，适合有一定后端基础的读者快速建立整体认识。";
        }
        if (prompt.contains(WRITING_TASK_MARK) || containsAny(prompt, "大纲", "润色", "扩写", "续写", "标题", "写作")) {
            return writingReply(prompt);
        }
        String question = section(prompt, SECTION_QUESTION, ANSWER_MARK);
        if (StrUtils.isBlank(question)) {
            question = StrUtils.abbreviate(prompt.replaceAll("\\s+", " ").trim(), 60);
        }
        return "（Mock 回答，未接入真实模型）关于「" + StrUtils.defaultString(question) + "」：当前站内上下文没有任何检索结果，"
                + "因此无法给出有依据的结论。建议先补充相关文章或知识库切片；接入真实模型后，这里会严格依据上下文作答，"
                + "并在每句结论后标注引用编号 [1]。";
    }

    /** 写作助手：按动作返回不同骨架，便于前端联调不同交互。 */
    private String writingReply(String prompt) {
        if (prompt.contains("大纲") || prompt.contains("outline")) {
            return """
                    一、问题背景：为什么需要这个能力
                    二、核心概念与边界：先把术语和适用范围说清楚
                    三、实现路径：从最小可用版本开始
                    四、关键取舍：性能、一致性、可维护性
                    五、常见坑与自检清单
                    六、小结与延伸阅读""";
        }
        if (prompt.contains("标题") || prompt.contains("title")) {
            return """
                    从原理到落地：一次完整的实践复盘
                    关于该主题，你需要先弄清楚的五个问题
                    我们踩过的坑：把复杂方案做简单的三条经验""";
        }
        if (prompt.contains("润色") || prompt.contains("polish")) {
            return "（Mock 润色结果）这段文字已调整措辞与节奏：拆分了过长的句子，统一了术语表达，"
                    + "补上了缺失的逻辑连接词，原意与信息量保持不变。";
        }
        if (prompt.contains("扩写") || prompt.contains("expand")) {
            return "（Mock 扩写结果）在原要点的基础上补充了背景动机、一个可运行的最小示例、"
                    + "以及两组反例对比，并给出可验证的结论，篇幅约为原文的两倍。";
        }
        return "（Mock 续写结果）接下来从工程实现的角度继续展开：先给出最小可用方案，"
                + "再讨论边界条件、失败重试与性能取舍，最后附上一段可直接运行的示例代码。";
    }

    /** 按字符数粗估 token 数，至少为 1。 */
    private int estimateTokens(String text) {
        if (StrUtils.isBlank(text)) {
            return 1;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    /** 截取两个标记之间的内容，找不到起始标记时返回 null。 */
    private String section(String text, String from, String to) {
        int start = text.indexOf(from);
        if (start < 0) {
            return null;
        }
        start += from.length();
        int end = text.indexOf(to, start);
        String value = end < 0 ? text.substring(start) : text.substring(start, end);
        return value.trim();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
