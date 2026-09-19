package com.inkos.system.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.inkos.common.core.domain.PageResult;
import com.inkos.system.domain.LoginUser;
import com.inkos.system.dto.SysUserForm;
import com.inkos.system.dto.SysUserQuery;
import com.inkos.system.entity.SysUser;
import com.inkos.system.vo.SysUserVO;

import java.util.List;

/**
 * 用户服务。
 *
 * <p>注意：本接口只负责「凭证校验」与「用户信息装配」，不涉及任何 Token 签发 ——
 * 会话与 Token 属于框架层职责，system 层不应依赖 Sa-Token。
 */
public interface SysUserService extends IService<SysUser> {

    PageResult<SysUserVO> pageUsers(SysUserQuery query);

    SysUserVO getUserDetail(Long id);

    SysUser getByUsername(String username);

    /**
     * 校验账号密码并装配登录用户上下文。
     *
     * @throws com.inkos.common.exception.BusinessException 账号不存在 / 密码错误 / 账号禁用
     */
    LoginUser authenticate(String username, String rawPassword);

    Long createUser(SysUserForm form);

    void updateUser(SysUserForm form);

    void deleteUser(Long id);

    void resetPassword(Long id, String rawPassword);

    void changeStatus(Long id, Integer status);

    void updateLastLogin(Long userId, String ip);

    List<Long> listRoleIdsByUserId(Long userId);

    void assignRoles(Long userId, List<Long> roleIds);
}
