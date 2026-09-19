package com.inkos.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.system.entity.SysOperLog;
import com.inkos.system.mapper.SysOperLogMapper;
import com.inkos.system.service.SysOperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现。
 */
@Slf4j
@Service
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements SysOperLogService {

    @Override
    public void record(SysOperLog operLog) {
        if (operLog.getOperTime() == null) {
            operLog.setOperTime(java.time.LocalDateTime.now());
        }
        save(operLog);
    }

    @Override
    public PageResult<SysOperLog> pageLogs(PageQuery query) {
        Page<SysOperLog> page = page(new Page<>(query.safePageNum(), query.safePageSize()),
                Wrappers.<SysOperLog>lambdaQuery()
                        .like(com.inkos.common.util.StrUtils.isNotBlank(query.getKeyword()),
                                SysOperLog::getTitle, query.getKeyword())
                        .orderByDesc(SysOperLog::getOperTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void clean() {
        remove(Wrappers.<SysOperLog>lambdaQuery());
        log.warn("操作日志已被清空");
    }
}
