package com.inkos.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志。与操作日志分开存储：登录是未认证请求，且量大、保留期短。
 */
@Getter
@Setter
@ToString
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String ip;

    private String browser;

    private String os;

    /** 1 成功 / 0 失败 */
    private Integer status;

    /** 提示信息，如「登录成功」「密码错误」 */
    private String msg;

    private LocalDateTime loginTime;
}
