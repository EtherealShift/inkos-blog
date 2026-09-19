package com.inkos.system.dto;

import com.inkos.common.core.validate.ValidGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户新增 / 修改表单。
 *
 * <p>新增时 id 必须为空、密码必填；修改时 id 必填、密码可留空表示不修改 —— 用校验分组表达。
 */
@Getter
@Setter
@ToString
public class SysUserForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Null(groups = ValidGroup.Create.class, message = "新增时不能指定 ID")
    @NotNull(groups = ValidGroup.Update.class, message = "修改时 ID 不能为空")
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在 3~64 之间")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称过长")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱过长")
    private String email;

    @Size(max = 20, message = "手机号过长")
    private String phone;

    /** 明文密码，仅用于入参；新增时必填，修改时留空表示不改密码 */
    @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间")
    private String password;

    @Size(max = 512, message = "头像地址过长")
    private String avatar;

    @Size(max = 512, message = "简介过长")
    private String bio;

    private Integer status = 1;

    /** 关联角色 ID */
    private List<Long> roleIds;
}
