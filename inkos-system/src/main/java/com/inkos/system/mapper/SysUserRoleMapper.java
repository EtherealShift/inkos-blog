package com.inkos.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
