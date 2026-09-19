package com.inkos.ai.client;

/**
 * 一次大模型调用的入参（与具体厂商无关）。
 *
 * <p>{@code temperature} / {@code maxTokens} 允许为 null，紧凑构造器会补默认值，
 * 调用方只关心业务提示词时可以直接使用 {@link #of(String)} 或 {@link #builder()}。
 *
 * @param model        模型名，为空时由客户端按 provider 配置决定
 * @param systemPrompt 系统提示词，可为空
 * @param userPrompt   用户提示词（业务渲染后的完整 Prompt）
 * @param temperature  采样温度，默认 {@value #DEFAULT_TEMPERATURE}
 * @param maxTokens    最大生成 token 数，默认 {@value #DEFAULT_MAX_TOKENS}
 */
public record LlmRequest(String model,
                         String systemPrompt,
                         String userPrompt,
                         Double temperature,
                         Integer maxTokens) {

    /** 默认采样温度：偏低，保证技术内容稳定 */
    public static final double DEFAULT_TEMPERATURE = 0.3D;

    /** 默认最大生成 token 数 */
    public static final int DEFAULT_MAX_TOKENS = 2048;

    public LlmRequest {
        temperature = temperature == null ? DEFAULT_TEMPERATURE : temperature;
        maxTokens = maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens;
    }

    /** 只给用户提示词的便捷构造。 */
    public static LlmRequest of(String userPrompt) {
        return new LlmRequest(null, null, userPrompt, null, null);
    }

    /** 系统 + 用户提示词的便捷构造。 */
    public static LlmRequest of(String systemPrompt, String userPrompt) {
        return new LlmRequest(null, systemPrompt, userPrompt, null, null);
    }

    /** 链式构造器，未设置的字段走默认值。 */
    public static Builder builder() {
        return new Builder();
    }

    /** {@link LlmRequest} 链式构造器。 */
    public static final class Builder {

        private String model;
        private String systemPrompt;
        private String userPrompt;
        private Double temperature;
        private Integer maxTokens;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(model, systemPrompt, userPrompt, temperature, maxTokens);
        }
    }
}
