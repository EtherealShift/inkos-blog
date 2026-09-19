package com.inkos.ai.router;

import com.inkos.ai.client.LlmClient;
import com.inkos.ai.enums.AiScene;

/**
 * 模型路由器：把业务场景翻译成一个可用的模型客户端。
 *
 * <p>负责「选谁调用」，不负责重试与熔断（后续可在此接口实现上叠加）。
 */
public interface ModelRouter {

    /**
     * 按场景选择客户端。
     *
     * @param scene 业务场景
     * @return 可用的客户端实现
     */
    LlmClient route(AiScene scene);
}
