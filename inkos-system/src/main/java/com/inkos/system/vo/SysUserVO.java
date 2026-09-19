package com.inkos.system.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户列表视图对象。刻意不含 password 字段，从类型上杜绝密码泄漏。
 */
public record SysUserVO(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        String bio,
        Integer status,
        String lastLoginIp,
        String lastLoginTime,
        List<String> roleNames,
        String createTime
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
