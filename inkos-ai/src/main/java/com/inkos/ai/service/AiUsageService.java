package com.inkos.ai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.inkos.ai.enums.AiScene;
import com.inkos.ai.entity.AiUsage;

/**
 * AI 用量计量与配额。
 *
 * <p>计量必须落库（微元整数），配额判断基于当天的累计用量，
 * 这是「成本可控」这条非功能需求的落点。
 */
public interface AiUsageService extends IService<AiUsage> {

    /**
     * 累加一次调用用量（同一用户 + 日期 + 场景 + 模型累加到同一行）。
     *
     * @param userId           用户 id，可为 null（匿名/内部任务）
     * @param scene            场景
     * @param model            模型名
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     * @param costMicro        估算成本（微元）
     */
    void record(Long userId, AiScene scene, String model, int promptTokens, int completionTokens, long costMicro);

    /** 当天累计 token 数（输入 + 输出）。 */
    long todayTokenUsage(Long userId);

    /** 当天累计调用次数。 */
    long todayRequestCount(Long userId);

    /**
     * 校验配额，超限抛 {@code BusinessException.of(ResultCode.AI_QUOTA_EXCEEDED)}。
     *
     * @param userId 用户 id，为 null 时跳过用户级校验
     * @param scene  场景
     */
    void checkQuota(Long userId, AiScene scene);
}
