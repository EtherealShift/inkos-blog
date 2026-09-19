package com.inkos.framework.security;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录成功返回的凭证信息。
 *
 * @param tokenName  令牌名（响应头/参数名），前端据此决定放在哪里
 * @param tokenValue 令牌值
 * @param expiresIn  有效期（秒），-1 表示永不过期
 */
public record LoginResult(String tokenName, String tokenValue, long expiresIn) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
