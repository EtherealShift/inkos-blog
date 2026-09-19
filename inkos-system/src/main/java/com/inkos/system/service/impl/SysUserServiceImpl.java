package com.inkos.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.constant.CommonConstants;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import com.inkos.system.domain.LoginUser;
import com.inkos.system.dto.SysUserForm;
import com.inkos.system.dto.SysUserQuery;
import com.inkos.system.entity.SysRole;
import com.inkos.system.entity.SysUser;
import com.inkos.system.entity.SysUserRole;
import com.inkos.system.mapper.SysRoleMapper;
import com.inkos.system.mapper.SysUserMapper;
import com.inkos.system.mapper.SysUserRoleMapper;
import com.inkos.system.service.SysPermissionService;
import com.inkos.system.service.SysUserService;
import com.inkos.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PasswordEncoder passwordEncoder;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionService permissionService;

    @Override
    public PageResult<SysUserVO> pageUsers(SysUserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .like(StrUtils.isNotBlank(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StrUtils.isNotBlank(query.getNickname()), SysUser::getNickname, query.getNickname())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = page(new Page<>(query.safePageNum(), query.safePageSize()), wrapper);

        // 批量装配角色名，避免逐行查询造成 N+1
        Map<Long, List<String>> roleNameMap = loadRoleNames(page.getRecords());
        List<SysUserVO> records = page.getRecords().stream()
                .map(u -> toVO(u, roleNameMap.getOrDefault(u.getId(), List.of())))
                .toList();

        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SysUserVO getUserDetail(Long id) {
        SysUser user = getById(id);
        BusinessException.throwIf(user == null, ResultCode.NOT_FOUND);
        return toVO(user, loadRoleNames(List.of(user)).getOrDefault(id, List.of()));
    }

    @Override
    public SysUser getByUsername(String username) {
        if (StrUtils.isBlank(username)) {
            return null;
        }
        // 逻辑删除由 @TableLogic 自动附加 deleted = 0
        return getOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username), false);
    }

    @Override
    public LoginUser authenticate(String username, String rawPassword) {
        SysUser user = getByUsername(username);

        // 账号不存在与密码错误返回同一错误码，避免账号枚举攻击
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setRoles(permissionService.getRoleCodes(user.getId()));
        loginUser.setPermissions(permissionService.getPermissionCodes(user.getId()));
        loginUser.setLoginTime(LocalDateTime.now());
        return loginUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(SysUserForm form) {
        BusinessException.throwIf(StrUtils.isBlank(form.getPassword()), "新增用户时密码不能为空");
        BusinessException.throwIf(isUsernameTaken(form.getUsername(), null), ResultCode.USERNAME_EXISTS);
        if (StrUtils.isNotBlank(form.getEmail())) {
            BusinessException.throwIf(isEmailTaken(form.getEmail(), null), ResultCode.EMAIL_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(form.getUsername());
        user.setNickname(form.getNickname());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setAvatar(form.getAvatar());
        user.setBio(form.getBio());
        user.setStatus(form.getStatus() == null ? CommonConstants.STATUS_ENABLED : form.getStatus());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        save(user);

        assignRoles(user.getId(), form.getRoleIds());
        log.info("新增用户成功 userId={} username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(SysUserForm form) {
        SysUser existing = getById(form.getId());
        BusinessException.throwIf(existing == null, ResultCode.NOT_FOUND);
        BusinessException.throwIf(isUsernameTaken(form.getUsername(), form.getId()), ResultCode.USERNAME_EXISTS);

        SysUser user = new SysUser();
        user.setId(form.getId());
        user.setUsername(form.getUsername());
        user.setNickname(form.getNickname());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setAvatar(form.getAvatar());
        user.setBio(form.getBio());
        user.setStatus(form.getStatus());
        // 密码留空表示不修改
        if (StrUtils.isNotBlank(form.getPassword())) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        updateById(user);

        if (form.getRoleIds() != null) {
            assignRoles(form.getId(), form.getRoleIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        BusinessException.throwIf(CommonConstants.SUPER_ADMIN_ID.equals(id), "超级管理员账号不允许删除");
        SysUser user = getById(id);
        BusinessException.throwIf(user == null, ResultCode.NOT_FOUND);
        removeById(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
    }

    @Override
    public void resetPassword(Long id, String rawPassword) {
        BusinessException.throwIf(StrUtils.isBlank(rawPassword) || rawPassword.length() < 6, "密码长度不能少于 6 位");
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(rawPassword));
        updateById(user);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        BusinessException.throwIf(CommonConstants.SUPER_ADMIN_ID.equals(id), "超级管理员账号不允许停用");
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        updateById(user);
    }

    @Override
    public void updateLastLogin(Long userId, String ip) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        updateById(user);
    }

    @Override
    public List<Long> listRoleIdsByUserId(Long userId) {
        return userRoleMapper
                .selectList(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            userRoleMapper.insert(new SysUserRole(userId, roleId));
        }
    }

    // ==================== 内部方法 ====================

    private boolean isUsernameTaken(String username, Long excludeId) {
        return exists(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
    }

    private boolean isEmailTaken(String email, Long excludeId) {
        return exists(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmail, email)
                .ne(excludeId != null, SysUser::getId, excludeId));
    }

    /**
     * 批量查询用户角色名：2 次 SQL 覆盖整页，避免 N+1。
     */
    private Map<Long, List<String>> loadRoleNames(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> userIds = users.stream().map(SysUser::getId).toList();

        List<SysUserRole> relations = userRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getUserId, userIds));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).distinct().toList();
        Map<Long, String> roleNameById = roleMapper.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName, (a, b) -> a));

        Map<Long, List<String>> result = new java.util.HashMap<>();
        for (SysUserRole relation : relations) {
            String roleName = roleNameById.get(relation.getRoleId());
            if (roleName != null) {
                result.computeIfAbsent(relation.getUserId(), k -> new ArrayList<>()).add(roleName);
            }
        }
        return result;
    }

    private SysUserVO toVO(SysUser user, List<String> roleNames) {
        return new SysUserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getBio(),
                user.getStatus(),
                user.getLastLoginIp(),
                user.getLastLoginTime() == null ? null : DATE_TIME.format(user.getLastLoginTime()),
                roleNames,
                user.getCreateTime() == null ? null : DATE_TIME.format(user.getCreateTime()));
    }
}
