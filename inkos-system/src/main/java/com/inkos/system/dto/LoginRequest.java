package com.inkos.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 */
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名过长")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间")
        String password,

        /** 记住我：延长 Token 有效期 */
        Boolean rememberMe
) {
    public boolean remember() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
