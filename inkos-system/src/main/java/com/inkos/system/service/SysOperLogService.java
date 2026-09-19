package com.inkos.system.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.system.entity.SysOperLog;

/**
 * 操作日志服务。
 */
public interface SysOperLogService extends IService<SysOperLog> {

    void record(SysOperLog operLog);

    PageResult<SysOperLog> pageLogs(PageQuery query);

    /**
     * 清空日志。内置管理操作，需谨慎授权。
     */
    void clean();
}
