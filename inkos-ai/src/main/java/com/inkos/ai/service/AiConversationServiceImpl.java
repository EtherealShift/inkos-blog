package com.inkos.ai.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.ai.client.LlmResponse;
import com.inkos.ai.entity.AiConversation;
import com.inkos.ai.entity.AiMessage;
import com.inkos.ai.enums.AiRole;
import com.inkos.ai.enums.AiScene;
import com.inkos.ai.mapper.AiConversationMapper;
import com.inkos.ai.mapper.AiMessageMapper;
import com.inkos.common.util.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI 会话存储实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation>
        implements AiConversationService {

    /** 结束原因：正常结束（骨架阶段不区分 length / tool_calls） */
    private static final String FINISH_STOP = "stop";

    private final AiMessageMapper aiMessageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureConversation(Long userId, String sessionId, AiScene scene, String title) {
        String sid = StrUtils.isBlank(sessionId) ? randomSessionId() : sessionId.trim();
        AiScene targetScene = scene == null ? AiScene.QA : scene;
        LocalDateTime now = LocalDateTime.now();

        AiConversation existing = find(sid, userId);
        if (existing != null) {
            existing.setUpdateTime(now);
            updateById(existing);
            return existing.getId();
        }
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setSessionId(sid);
        conversation.setScene(targetScene.getCode());
        conversation.setTitle(StrUtils.abbreviate(title, 100));
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        save(conversation);
        log.info("创建 AI 会话：id={} sessionId={} userId={} scene={}",
                conversation.getId(), sid, userId, targetScene.getCode());
        return conversation.getId();
    }

    @Override
    public void appendMessage(Long conversationId, AiRole role, String content, String citations, LlmResponse resp) {
        if (conversationId == null) {
            log.warn("会话 id 为空，跳过消息落库");
            return;
        }
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole((role == null ? AiRole.ASSISTANT : role).getCode());
        // content 列 NOT NULL，兜底为空串
        message.setContent(StrUtils.defaultString(content));
        message.setCitations(citations);
        if (resp != null) {
            message.setModel(resp.model());
            message.setPromptTokens(resp.promptTokens());
            message.setCompletionTokens(resp.completionTokens());
            message.setLatencyMs((int) Math.min(Integer.MAX_VALUE, Math.max(0L, resp.latencyMs())));
            message.setFinishReason(FINISH_STOP);
        }
        message.setCreateTime(LocalDateTime.now());
        aiMessageMapper.insert(message);
    }

    /** 同一 sessionId 在不同用户之间互不可见：登录用户按 userId 隔离，匿名会话按 NULL 匹配。 */
    private AiConversation find(String sessionId, Long userId) {
        List<AiConversation> rows = baseQuery(sessionId, userId).list();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LambdaQueryChainWrapper<AiConversation> baseQuery(String sessionId, Long userId) {
        LambdaQueryChainWrapper<AiConversation> wrapper = lambdaQuery().eq(AiConversation::getSessionId, sessionId);
        return userId == null ? wrapper.isNull(AiConversation::getUserId) : wrapper.eq(AiConversation::getUserId, userId);
    }

    private String randomSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
