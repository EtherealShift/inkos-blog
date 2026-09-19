package com.inkos.ai.config;

import com.inkos.ai.enums.AiScene;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能层配置，前缀 {@code inkos.ai}。
 *
 * <pre>
 * inkos:
 *   ai:
 *     enabled: false                 # 默认关闭，走 Mock，无需任何 API Key
 *     provider: mock                 # 当前使用的供应商标识
 *     providers:
 *       deepseek: { base-url: https://api.deepseek.com/v1, api-key: sk-xxx, model: deepseek-chat }
 *     route:
 *       qa:     [deepseek, mock]     # 场景 -> 供应商优先链
 *       summary:[mock]
 *     quota:
 *       qa-per-user-per-day: 50
 *       daily-cost-limit-micro: 50000000
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "inkos.ai")
public class AiProperties {

    /** 是否启用真实大模型调用；false 时使用 MockLlmClient */
    private boolean enabled = false;

    /** 当前供应商名，需与 {@link #providers} 的 key 一致 */
    private String provider = "mock";

    /** 供应商配置表：key 为供应商标识 */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    /** 场景路由表：key 为 {@link AiScene#getCode()}，value 为按优先级排列的供应商标识 */
    private Map<String, List<String>> route = new LinkedHashMap<>();

    /** 配额与成本控制 */
    private Quota quota = new Quota();

    /**
     * 取场景对应的供应商优先链，未配置时返回空列表（由路由器回退到首个可用客户端）。
     */
    public List<String> routeOf(AiScene scene) {
        if (scene == null || route == null) {
            return List.of();
        }
        return route.getOrDefault(scene.getCode(), List.of());
    }

    /** 按名称取供应商配置，不存在返回 null。 */
    public Provider findProvider(String name) {
        if (name == null || providers == null) {
            return null;
        }
        return providers.get(name);
    }

    /**
     * 单个供应商连接信息。OpenAI 兼容协议，任何支持 {@code /chat/completions} 的服务都可接入。
     */
    @Getter
    @Setter
    public static class Provider {

        /** 接口基础地址，如 https://api.deepseek.com/v1 */
        private String baseUrl;

        /** API Key，走环境变量注入，不要硬编码 */
        private String apiKey;

        /** 默认模型名，请求未指定 model 时使用 */
        private String model;
    }

    /**
     * 配额与成本阈值。金额一律用「微元」整数表示（1 元 = 1_000_000 微元），避免浮点误差。
     */
    @Getter
    @Setter
    public static class Quota {

        /** 每个用户每天站内问答次数上限 */
        private int qaPerUserPerDay = 50;

        /** 每个用户每天成本上限（微元），默认 50 元 */
        private long dailyCostLimitMicro = 50_000_000L;
    }
}
