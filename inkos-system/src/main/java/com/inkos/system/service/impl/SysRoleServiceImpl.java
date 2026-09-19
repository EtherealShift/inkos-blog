package com.inkos.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.constant.CommonConstants;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.system.entity.SysRole;
import com.inkos.system.entity.SysRoleMenu;
import com.inkos.system.entity.SysUserRole;
import com.inkos.system.mapper.SysRoleMapper;
import com.inkos.system.mapper.SysRoleMenuMapper;
import com.inkos.system.mapper.SysUserRoleMapper;
import com.inkos.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<SysRole> listEnabled() {
        return list(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getStatus, CommonConstants.STATUS_ENABLED)
                .orderByAsc(SysRole::getSortOrder));
    }

    @Override
    public PageResult<SysRole> pageRoles(PageQuery query) {
        Page<SysRole> page = page(new Page<>(query.safePageNum(), query.safePageSize()),
                Wrappers.<SysRole>lambdaQuery()
                        .like(com.inkos.common.util.StrUtils.isNotBlank(query.getKeyword()),
                                SysRole::getRoleName, query.getKeyword())
                        .orderByAsc(SysRole::getSortOrder));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public Long createRole(SysRole role) {
        BusinessException.throwIf(isRoleCodeTaken(role.getRoleCode(), null), "角色编码已存在");
        role.setId(null);
        if (role.getStatus() == null) {
            role.setStatus(CommonConstants.STATUS_ENABLED);
        }
        save(role);
        return role.getId();
    }

    @Override
    public void updateRole(SysRole role) {
        BusinessException.throwIf(role.getId() == null, ResultCode.BAD_REQUEST);
        BusinessException.throwIf(isRoleCodeTaken(role.getRoleCode(), role.getId()), "角色编码已存在");
        updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        // 有用户关联的角色不允许直接删除，否则用户的权限会静默丢失
        Long bound = userRoleMapper.selectCount(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
        BusinessException.throwIf(bound != null && bound > 0, "该角色下仍有用户，无法删除");

        removeById(id);
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
    }

    @Override
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return roleMenuMapper
                .selectList(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            roleMenuMapper.insert(new SysRoleMenu(roleId, menuId));
        }
        log.info("角色授权完成 roleId={} menuCount={}", roleId, menuIds.size());
    }

    private boolean isRoleCodeTaken(String roleCode, Long excludeId) {
        return exists(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeId != null, SysRole::getId, excludeId));
    }
}
