package com.inkos.system.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.system.entity.SysLoginLog;

/**
 * 登录日志服务。
 */
public interface SysLoginLogService extends IService<SysLoginLog> {

    /**
     * 记录一次登录尝试（无论成功失败）。
     */
    void record(String username, boolean success, String msg, String ip, String userAgent);

    PageResult<SysLoginLog> pageLogs(PageQuery query);
}
