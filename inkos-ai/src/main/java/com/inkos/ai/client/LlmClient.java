package com.inkos.ai.client;

/**
 * 大模型客户端抽象。
 *
 * <p>这是智能层唯一与「模型」耦合的接口：业务代码只依赖
 * {@code com.inkos.ai.facade.AiFacade}，由 {@code ModelRouter} 选择具体实现，
 * 因此换模型 / 换厂商只需要新增一个实现 + 改配置，不动业务代码。
 *
 * <p>注意：实现类不应抛出未包装的第三方异常，上游只识别
 * {@code AiUnavailableException}。
 */
public interface LlmClient {

    /**
     * 发起一次对话补全调用（同步）。
     *
     * @param request 调用入参
     * @return 模型响应
     */
    LlmResponse chat(LlmRequest request);

    /**
     * 供应商标识，与 {@code inkos.ai.route} 中配置的名字对应（如 mock / deepseek / qwen）。
     */
    String provider();
}
