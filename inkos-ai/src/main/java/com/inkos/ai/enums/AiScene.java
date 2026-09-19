package com.inkos.ai.enums;

import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import lombok.Getter;

import java.util.Arrays;

/**
 * AI 业务场景。
 *
 * <p>{@code code} 同时用于三处，必须保持一致：
 * <ul>
 *   <li>配置项 {@code inkos.ai.route.<code>} 的路由键；</li>
 *   <li>数据库 {@code ai_conversation.scene} / {@code ai_usage.scene} 列；</li>
 *   <li>日志与计量维度。</li>
 * </ul>
 */
@Getter
public enum AiScene {

    QA("qa", "站内问答"),
    WRITING("writing", "写作助手"),
    SUMMARY("summary", "摘要生成"),
    TAG("tag", "标签抽取"),
    COMMENT_REVIEW("comment", "评论审核");

    private final String code;
    private final String desc;

    AiScene(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 解析场景（忽略大小写与首尾空白）。
     *
     * @throws BusinessException 入参为空或未知场景时抛出 400 业务异常
     */
    public static AiScene of(String code) {
        if (StrUtils.isBlank(code)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "AI 场景不能为空");
        }
        String normalized = code.trim();
        return Arrays.stream(values())
                .filter(scene -> scene.code.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> BusinessException.of(ResultCode.BAD_REQUEST, "未知的 AI 场景：" + code));
    }
}
