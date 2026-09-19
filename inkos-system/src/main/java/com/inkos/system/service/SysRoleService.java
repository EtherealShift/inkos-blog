package com.inkos.system.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.system.entity.SysRole;

import java.util.List;

/**
 * 角色服务。
 */
public interface SysRoleService extends IService<SysRole> {

    List<SysRole> listEnabled();

    PageResult<SysRole> pageRoles(PageQuery query);

    Long createRole(SysRole role);

    void updateRole(SysRole role);

    void deleteRole(Long id);

    List<Long> listMenuIdsByRoleId(Long roleId);

    void assignMenus(Long roleId, List<Long> menuIds);
}
