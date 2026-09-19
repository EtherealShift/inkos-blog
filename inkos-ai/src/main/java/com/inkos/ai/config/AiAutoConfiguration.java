package com.inkos.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 智能层自动装配。
 *
 * <p>这里只做最小装配：绑定配置 + 提供一个不预设 baseUrl 的 {@link RestClient}，
 * 具体 URL 由 {@code OpenAiCompatibleLlmClient} 按供应商配置拼接。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    /** 智能层专用 RestClient 的 Bean 名，避免与应用其它 RestClient 冲突 */
    public static final String AI_REST_CLIENT = "aiRestClient";

    @Bean(AI_REST_CLIENT)
    public RestClient aiRestClient() {
        return RestClient.builder().build();
    }
}
