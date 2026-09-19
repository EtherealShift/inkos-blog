package com.inkos.ai.client;

import com.inkos.ai.config.AiAutoConfiguration;
import com.inkos.ai.config.AiProperties;
import com.inkos.ai.exception.AiUnavailableException;
import com.inkos.common.util.StrUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议客户端：{@code POST {baseUrl}/chat/completions}。
 *
 * <p>只依赖 Spring {@code RestClient} 与 JDK，不引入任何模型 SDK，
 * DeepSeek / Qwen / 本地 vLLM 等只要兼容该协议即可直接接入。
 *
 * <p>仅在 {@code inkos.ai.enabled=true} 时启用；任何失败统一包装为
 * {@link AiUnavailableException}，由上层降级。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "inkos.ai.enabled", havingValue = "true")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String PROVIDER_FALLBACK = "openai";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public OpenAiCompatibleLlmClient(AiProperties aiProperties,
                                     @Qualifier(AiAutoConfiguration.AI_REST_CLIENT) RestClient restClient) {
        this.aiProperties = aiProperties;
        this.restClient = restClient;
    }

    @Override
    public String provider() {
        return StrUtils.isNotBlank(aiProperties.getProvider()) ? aiProperties.getProvider() : PROVIDER_FALLBACK;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        long start = System.currentTimeMillis();
        AiProperties.Provider provider = resolveProvider();
        String baseUrl = StrUtils.removeEnd(
                StrUtils.defaultIfBlank(provider.getBaseUrl(), DEFAULT_BASE_URL), "/");
        String model = request != null && StrUtils.isNotBlank(request.model())
                ? request.model()
                : StrUtils.defaultIfBlank(provider.getModel(), DEFAULT_MODEL);
        try {
            Map<String, Object> body = buildBody(request, model);
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            long latencyMs = System.currentTimeMillis() - start;
            LlmResponse result = parse(response, model, latencyMs);
            log.info("大模型调用成功 provider={} model={} promptTokens={} completionTokens={} latencyMs={}",
                    provider(), result.model(), result.promptTokens(), result.completionTokens(), latencyMs);
            return result;
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.error("大模型调用失败 provider={} baseUrl={} latencyMs={}", provider(), baseUrl, latencyMs, e);
            throw new AiUnavailableException("调用大模型失败：" + e.getMessage(), e);
        }
    }

    /** 解析当前 provider 的连接配置，缺失即视为不可用。 */
    private AiProperties.Provider resolveProvider() {
        String name = provider();
        AiProperties.Provider provider = aiProperties.findProvider(name);
        if (provider == null) {
            Map<String, AiProperties.Provider> providers = aiProperties.getProviders();
            if (providers != null && providers.size() == 1) {
                // 只配了一家供应商时允许省略 provider 名
                provider = providers.values().iterator().next();
            }
        }
        if (provider == null) {
            throw new AiUnavailableException("未找到供应商配置：inkos.ai.providers." + name);
        }
        if (StrUtils.isBlank(provider.getApiKey())) {
            throw new AiUnavailableException("未配置 API Key：inkos.ai.providers." + name + ".api-key");
        }
        return provider;
    }

    /** 组装 OpenAI 兼容请求体。 */
    private Map<String, Object> buildBody(LlmRequest request, String model) {
        String systemPrompt = request == null ? null : request.systemPrompt();
        String userPrompt = request == null ? null : request.userPrompt();
        List<Map<String, String>> messages = new ArrayList<>(2);
        if (StrUtils.isNotBlank(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", StrUtils.defaultString(userPrompt)));

        Map<String, Object> body = new LinkedHashMap<>(8);
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", request == null ? LlmRequest.DEFAULT_TEMPERATURE : request.temperature());
        body.put("max_tokens", request == null ? LlmRequest.DEFAULT_MAX_TOKENS : request.maxTokens());
        // 骨架阶段只用同步响应，流式由上层按需扩展
        body.put("stream", Boolean.FALSE);
        return body;
    }

    /** 取 {@code choices[0].message.content} 与 {@code usage.*}。 */
    private LlmResponse parse(Map<String, Object> response, String fallbackModel, long latencyMs) {
        if (response == null || response.isEmpty()) {
            throw new AiUnavailableException("大模型返回空响应");
        }
        String content = "";
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice
                && choice.get("message") instanceof Map<?, ?> message) {
            content = StrUtils.defaultString(asString(message.get("content")));
        }
        if (StrUtils.isBlank(content)) {
            throw new AiUnavailableException("大模型返回内容为空");
        }
        Map<?, ?> usage = response.get("usage") instanceof Map<?, ?> map ? map : Map.of();
        String model = StrUtils.defaultIfBlank(asString(response.get("model")), fallbackModel);
        return new LlmResponse(content, model,
                asInt(usage.get("prompt_tokens")), asInt(usage.get("completion_tokens")), latencyMs);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
