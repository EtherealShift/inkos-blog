package com.inkos.system.dto;

import com.inkos.common.core.domain.PageQuery;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 用户分页查询条件。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class SysUserQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;

    private String nickname;

    private Integer status;

    private Long roleId;
}
