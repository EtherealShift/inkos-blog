package com.inkos.ai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.inkos.ai.client.LlmResponse;
import com.inkos.ai.entity.AiConversation;
import com.inkos.ai.enums.AiRole;
import com.inkos.ai.enums.AiScene;

/**
 * AI 会话与消息存储。
 *
 * <p>会话按 {@code sessionId} 唯一：前端首次提问可不带 sessionId，由服务端生成后回传，
 * 后续轮次带上同一个 sessionId 即可续接上下文。
 */
public interface AiConversationService extends IService<AiConversation> {

    /**
     * 按 sessionId 找会话，不存在则创建。
     *
     * @param userId    用户 id，可为 null
     * @param sessionId 会话标识，为空时自动生成
     * @param scene     场景
     * @param title     会话标题，一般取首轮问题
     * @return 会话主键 id
     */
    Long ensureConversation(Long userId, String sessionId, AiScene scene, String title);

    /**
     * 追加一条消息。
     *
     * @param conversationId 会话 id，为 null 时跳过落库
     * @param role           角色
     * @param content        正文
     * @param citations      引用来源 JSON 文本，可为 null
     * @param resp           模型响应（用户消息传 null），用于回填模型与 token 信息
     */
    void appendMessage(Long conversationId, AiRole role, String content, String citations, LlmResponse resp);
}
