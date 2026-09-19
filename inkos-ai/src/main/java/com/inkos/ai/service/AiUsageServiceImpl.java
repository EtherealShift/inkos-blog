package com.inkos.ai.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.ai.config.AiProperties;
import com.inkos.ai.entity.AiUsage;
import com.inkos.ai.enums.AiScene;
import com.inkos.ai.mapper.AiUsageMapper;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 用量计量实现。
 *
 * <p>说明：统计口径为「当天 + 用户」，行数极少（场景 × 模型量级），
 * 因此直接查当天明细在内存中汇总，避免在 Mapper 里写方言相关的聚合 SQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl extends ServiceImpl<AiUsageMapper, AiUsage> implements AiUsageService {

    /** 未知模型名占位 */
    private static final String UNKNOWN_MODEL = "unknown";

    private final AiProperties aiProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(Long userId, AiScene scene, String model, int promptTokens, int completionTokens, long costMicro) {
        AiScene targetScene = scene == null ? AiScene.QA : scene;
        String modelName = StrUtils.defaultIfBlank(model, UNKNOWN_MODEL);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        AiUsage usage = findRow(userId, today, targetScene.getCode(), modelName);
        if (usage == null) {
            usage = new AiUsage();
            usage.setUserId(userId);
            usage.setUsageDate(today);
            usage.setScene(targetScene.getCode());
            usage.setModel(modelName);
            usage.setRequestCount(1);
            usage.setPromptTokens((long) promptTokens);
            usage.setCompletionTokens((long) completionTokens);
            usage.setCostMicro(costMicro);
            usage.setCreateTime(now);
            usage.setUpdateTime(now);
            save(usage);
        } else {
            usage.setRequestCount(nvlInt(usage.getRequestCount()) + 1);
            usage.setPromptTokens(nvl(usage.getPromptTokens()) + promptTokens);
            usage.setCompletionTokens(nvl(usage.getCompletionTokens()) + completionTokens);
            usage.setCostMicro(nvl(usage.getCostMicro()) + costMicro);
            usage.setUpdateTime(now);
            updateById(usage);
        }
        log.debug("AI 计量落库：user={} scene={} model={} tokens={}+{} costMicro={}",
                userId, targetScene.getCode(), modelName, promptTokens, completionTokens, costMicro);
    }

    @Override
    public long todayTokenUsage(Long userId) {
        return todayRows(userId).stream()
                .mapToLong(row -> nvl(row.getPromptTokens()) + nvl(row.getCompletionTokens()))
                .sum();
    }

    @Override
    public long todayRequestCount(Long userId) {
        return todayRows(userId).stream()
                .mapToLong(row -> nvl(row.getRequestCount()))
                .sum();
    }

    @Override
    public void checkQuota(Long userId, AiScene scene) {
        // 未登录（或内部任务）不做用户级配额校验
        if (userId == null) {
            return;
        }
        AiProperties.Quota quota = aiProperties.getQuota();
        if (quota == null) {
            return;
        }
        List<AiUsage> rows = todayRows(userId);
        long costMicro = rows.stream().mapToLong(row -> nvl(row.getCostMicro())).sum();
        if (quota.getDailyCostLimitMicro() > 0 && costMicro >= quota.getDailyCostLimitMicro()) {
            log.warn("AI 日成本配额已用尽：user={} costMicro={} limitMicro={}",
                    userId, costMicro, quota.getDailyCostLimitMicro());
            throw BusinessException.of(ResultCode.AI_QUOTA_EXCEEDED);
        }
        if (scene == AiScene.QA && quota.getQaPerUserPerDay() > 0) {
            long used = rows.stream()
                    .filter(row -> AiScene.QA.getCode().equals(row.getScene()))
                    .mapToLong(row -> nvl(row.getRequestCount()))
                    .sum();
            if (used >= quota.getQaPerUserPerDay()) {
                log.warn("AI 问答次数配额已用尽：user={} used={} limit={}",
                        userId, used, quota.getQaPerUserPerDay());
                throw BusinessException.of(ResultCode.AI_QUOTA_EXCEEDED);
            }
        }
    }

    /** 查当天指定维度的计量行，不存在返回 null。 */
    private AiUsage findRow(Long userId, LocalDate date, String scene, String model) {
        List<AiUsage> rows = baseQuery(userId, date)
                .eq(AiUsage::getScene, scene)
                .eq(AiUsage::getModel, model)
                .list();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 当天计量明细。 */
    private List<AiUsage> todayRows(Long userId) {
        return baseQuery(userId, LocalDate.now()).list();
    }

    /** 拼装「用户 + 日期」条件；用户为空时必须用 IS NULL，否则 SQL 恒不成立。 */
    private LambdaQueryChainWrapper<AiUsage> baseQuery(Long userId, LocalDate date) {
        LambdaQueryChainWrapper<AiUsage> wrapper = lambdaQuery().eq(AiUsage::getUsageDate, date);
        return userId == null ? wrapper.isNull(AiUsage::getUserId) : wrapper.eq(AiUsage::getUserId, userId);
    }

    private long nvl(Number value) {
        return value == null ? 0L : value.longValue();
    }

    /** Integer 字段专用：避免 long 到 Integer 的显式转换散落在业务代码里。 */
    private int nvlInt(Integer value) {
        return value == null ? 0 : value;
    }
}
