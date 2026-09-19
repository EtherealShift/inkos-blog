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
import java.time.LocalDateTime;

/**
 * 系统用户。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录账号，全局唯一 */
    private String username;

    /** 显示昵称 */
    private String nickname;

    private String email;

    private String phone;

    /** BCrypt 密文，严禁出参 */
    private String password;

    private String avatar;

    /** 个人简介 */
    private String bio;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
