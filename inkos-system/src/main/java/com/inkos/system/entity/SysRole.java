package com.inkos.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.inkos.common.core.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 角色。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 角色名称，如「超级管理员」 */
    private String roleName;

    /** 角色编码，如 ROLE_ADMIN —— Sa-Token 的鉴权主体 */
    private String roleCode;

    private Integer sortOrder;

    /** 1 正常 / 0 停用 */
    private Integer status;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
