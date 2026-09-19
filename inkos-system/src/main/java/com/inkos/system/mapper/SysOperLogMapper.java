package com.inkos.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
