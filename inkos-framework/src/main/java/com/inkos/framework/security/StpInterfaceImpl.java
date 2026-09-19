package com.inkos.framework.security;

import cn.dev33.satoken.stp.StpInterface;
import com.inkos.system.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限数据源。
 *
 * <p>Sa-Token 在每次 {@code @SaCheckPermission} / {@code @SaCheckRole} 校验时会回调本类。
 * 这里把「权限从哪来」与「权限怎么用」解耦：system 层负责查询，框架层只做适配。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysPermissionService permissionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>(permissionService.getPermissionCodes(toUserId(loginId)));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>(permissionService.getRoleCodes(toUserId(loginId)));
    }

    private Long toUserId(Object loginId) {
        return loginId == null ? null : Long.valueOf(loginId.toString());
    }
}
