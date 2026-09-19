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
 * 操作日志。由框架层切面异步写入，故意不继承 BaseEntity：
 * 日志只需记录「谁在何时做了什么」，不需要 update 审计字段。
 */
@Getter
@Setter
@ToString
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 模块标题 */
    private String title;

    /** 业务类型，见 LogBusinessType.desc */
    private String businessType;

    /** 被调用的方法全限定名 */
    private String method;

    private String requestMethod;

    private String operName;

    private String operUrl;

    private String operIp;

    /** 请求参数（已脱敏、已截断） */
    private String operParam;

    private String jsonResult;

    /** 1 成功 / 0 失败 */
    private Integer status;

    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costTime;

    private LocalDateTime operTime;
}
