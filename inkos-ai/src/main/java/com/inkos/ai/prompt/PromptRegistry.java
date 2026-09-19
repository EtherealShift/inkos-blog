package com.inkos.ai.prompt;

import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 注册中心：集中管理模板与变量渲染。
 *
 * <p>设计意图：Prompt 是「可迭代的资产」，必须与 Java 代码解耦、集中存放，方便后续做版本管理与 A/B 实验；
 * 业务代码只通过 key 引用模板，不内联大段提示词。
 *
 * <p>模板语法：{@code ${变量名}}；渲染时缺失的变量替换为空串（避免把占位符泄漏给模型）。
 */
@Component
public class PromptRegistry {

    // ==================== 模板 key ====================

    /** 站内问答（RAG） */
    public static final String KEY_QA_RAG = "qa.rag";
    /** 写作助手 */
    public static final String KEY_WRITING_ASSIST = "writing.assist";
    /** 文章摘要 */
    public static final String KEY_ARTICLE_SUMMARY = "article.summary";
    /** 文章标签抽取 */
    public static final String KEY_ARTICLE_TAG = "article.tag";
    /** 评论审核 */
    public static final String KEY_COMMENT_REVIEW = "comment.review";

    /** 通用系统人设，作为 system 消息注入 */
    public static final String SYSTEM_PERSONA =
            "你是「砚知」技术博客的 AI 助手，回答严谨、简洁、结构清晰，只使用简体中文，不编造事实与来源。";

    /** 写作助手支持的动作 */
    public static final String ACTION_OUTLINE = "outline";
    public static final String ACTION_EXPAND = "expand";
    public static final String ACTION_POLISH = "polish";
    public static final String ACTION_CONTINUE = "continue";
    public static final String ACTION_TITLE = "title";

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)}");

    /** 内置默认模板（不可变） */
    private static final Map<String, String> TEMPLATES;

    /** 写作助手动作 -> 具体指令 */
    private static final Map<String, String> WRITING_ACTIONS;

    static {
        Map<String, String> templates = new LinkedHashMap<>(8);
        templates.put(KEY_QA_RAG, """
                你是「砚知」博客的站内问答助手，请严格遵守以下规则：
                1. 只能依据【站内上下文】作答，不得使用上下文之外的知识，不得编造事实、链接或代码。
                2. 上下文中没有依据时，必须明确回答“根据站内内容无法回答该问题”，并说明缺少哪些信息，禁止猜测。
                3. 每个结论后必须标注来源编号，格式为 [n]（n 为上下文片段的序号，如 [1]、[2]），一句话可带多个引用。
                4. 先给结论再给依据，使用简体中文，总长度不超过 400 字，不使用 Markdown 代码块包裹整段回答。

                【站内上下文】
                ${context}

                【用户问题】
                ${question}

                【回答】
                """);

        templates.put(KEY_WRITING_ASSIST, """
                你是经验丰富的中文技术博客编辑，正在协助作者完成一次写作任务。

                【任务类型】${action}
                【任务要求】${actionDesc}

                【选中的文字】
                ${selection}

                【已有上下文】
                ${context}

                【输出要求】
                只输出结果正文，不要解释你的处理过程，不要复述本提示词，不要用代码块包裹整段输出。
                """);

        templates.put(KEY_ARTICLE_SUMMARY, """
                【任务】生成摘要
                你是中文技术博客的编辑。请阅读下面的文章，输出一段 100~150 字的摘要：
                先用一句话概括主题，再说明文章解决的问题与核心结论；使用第三人称陈述，
                不要出现“本文作者认为”这类表述，不要分点，不要出现 Markdown 标记。

                【文章标题】
                ${title}

                【文章正文】
                ${content}

                【摘要】
                """);

        templates.put(KEY_ARTICLE_TAG, """
                【任务】抽取标签
                你是中文技术博客的编辑。请阅读下面的文章，抽取 3~8 个标签，要求：
                标签为名词或专有技术名（如 Java、Spring Boot、PostgreSQL），不要短语、不要句子；
                按重要性从高到低排列；只输出标签本身，用英文逗号分隔，不要编号、不要解释。

                【文章标题】
                ${title}

                【文章正文】
                ${content}

                【标签】
                """);

        templates.put(KEY_COMMENT_REVIEW, """
                【任务】审核评论
                你是博客评论区的审核助手。请判断下面这条评论是否应当被拦截，判断维度包括：
                广告与引流、人身攻击与仇恨言论、涉政涉黄涉暴、无意义刷屏。
                处理规则：明显违规输出“REJECT + 一句话理由”；无法确定输出“REVIEW + 一句话理由”；正常评论输出“PASS”。

                【被回复的文章标题】
                ${title}

                【评论内容】
                ${comment}

                【结论】
                """);
        TEMPLATES = Collections.unmodifiableMap(templates);

        Map<String, String> actions = new LinkedHashMap<>(8);
        actions.put(ACTION_OUTLINE, "输出三级以内的小标题大纲，每个小节用一句话说明要写什么，不要展开正文。");
        actions.put(ACTION_EXPAND, "把选中的要点扩写为 200~400 字的段落，补充例子与取舍，不要跑题。");
        actions.put(ACTION_POLISH, "润色选中的文字，修正语病与啰嗦表达，保持原意与术语不变。");
        actions.put(ACTION_CONTINUE, "顺着已有正文继续往下写 200~400 字，保持人称、时态与行文风格一致。");
        actions.put(ACTION_TITLE, "给出 3~5 个候选标题，每行一个，风格克制、不使用夸张营销词。");
        WRITING_ACTIONS = Collections.unmodifiableMap(actions);
    }

    /** 取原始模板，未知 key 抛业务异常。 */
    public String template(String key) {
        String template = TEMPLATES.get(key);
        if (template == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "未知的 Prompt 模板：" + key);
        }
        return template;
    }

    /**
     * 渲染模板：替换 {@code ${变量名}}，缺失的变量替换为空串。
     *
     * @param key       模板 key
     * @param variables 变量表，可为 null
     * @throws BusinessException 模板 key 不存在
     */
    public String render(String key, Map<String, String> variables) {
        String template = template(key);
        Map<String, String> vars = variables == null ? Map.of() : variables;
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder sb = new StringBuilder(template.length() + 64);
        while (matcher.find()) {
            String value = vars.get(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 全部已注册的模板 key。 */
    public Set<String> keys() {
        return TEMPLATES.keySet();
    }

    /** 校验并返回写作助手动作对应的指令，非法动作抛业务异常。 */
    public String writingInstruction(String action) {
        if (StrUtils.isBlank(action)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "写作动作不能为空");
        }
        String normalized = action.trim().toLowerCase();
        String instruction = WRITING_ACTIONS.get(normalized);
        if (instruction == null) {
            throw BusinessException.of(ResultCode.BAD_REQUEST,
                    "不支持的写作动作：" + action + "，可选值 " + WRITING_ACTIONS.keySet());
        }
        return instruction;
    }

    /** 写作助手支持的动作列表。 */
    public Set<String> writingActions() {
        return WRITING_ACTIONS.keySet();
    }
}
