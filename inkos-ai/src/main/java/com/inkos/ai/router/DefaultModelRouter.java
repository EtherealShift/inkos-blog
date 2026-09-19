package com.inkos.ai.router;

import com.inkos.ai.client.LlmClient;
import com.inkos.ai.config.AiProperties;
import com.inkos.ai.enums.AiScene;
import com.inkos.ai.exception.AiUnavailableException;
import com.inkos.common.util.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认路由器：按配置的优先级链匹配客户端，匹配不到则回退到首个可用客户端。
 *
 * <p>路由只认 {@link LlmClient#provider()} 这个名字，不认具体实现类，
 * 因此新增供应商无需改动本类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultModelRouter implements ModelRouter {

    private final List<LlmClient> clients;
    private final AiProperties aiProperties;

    @Override
    public LlmClient route(AiScene scene) {
        if (clients == null || clients.isEmpty()) {
            throw new AiUnavailableException("没有可用的 LLM 客户端，请检查 inkos.ai.enabled 配置");
        }
        AiScene target = scene == null ? AiScene.QA : scene;
        if (scene == null) {
            log.warn("路由场景为空，按默认场景 {} 处理", target.getCode());
        }
        List<String> preferred = aiProperties.routeOf(target);
        for (String provider : preferred) {
            for (LlmClient client : clients) {
                if (StrUtils.isNotBlank(provider) && provider.trim().equalsIgnoreCase(client.provider())) {
                    log.info("模型路由：场景={} 命中配置路由 provider={}", target.getCode(), client.provider());
                    return client;
                }
            }
        }
        LlmClient fallback = clients.get(0);
        if (preferred.isEmpty()) {
            log.info("模型路由：场景={} 未配置路由，回退到首个可用客户端 provider={}", target.getCode(), fallback.provider());
        } else {
            log.warn("模型路由：场景={} 配置的路由 {} 均无对应客户端，回退到 provider={}",
                    target.getCode(), preferred, fallback.provider());
        }
        return fallback;
    }
}
