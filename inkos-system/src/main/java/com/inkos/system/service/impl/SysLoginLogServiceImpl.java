package com.inkos.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.util.StrUtils;
import com.inkos.system.entity.SysLoginLog;
import com.inkos.system.mapper.SysLoginLogMapper;
import com.inkos.system.service.SysLoginLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录日志服务实现。
 */
@Slf4j
@Service
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog>
        implements SysLoginLogService {

    @Override
    public void record(String username, boolean success, String msg, String ip, String userAgent) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUsername(username);
        loginLog.setStatus(success ? 1 : 0);
        loginLog.setMsg(msg);
        loginLog.setIp(ip);
        loginLog.setBrowser(parseBrowser(userAgent));
        loginLog.setOs(parseOs(userAgent));
        loginLog.setLoginTime(LocalDateTime.now());
        save(loginLog);
    }

    @Override
    public PageResult<SysLoginLog> pageLogs(PageQuery query) {
        Page<SysLoginLog> page = page(new Page<>(query.safePageNum(), query.safePageSize()),
                Wrappers.<SysLoginLog>lambdaQuery()
                        .like(StrUtils.isNotBlank(query.getKeyword()), SysLoginLog::getUsername, query.getKeyword())
                        .orderByDesc(SysLoginLog::getLoginTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 极简 UA 解析。生产环境建议换成 Yauaa 等成熟库，此处避免为骨架引入额外依赖。
     */
    private String parseBrowser(String userAgent) {
        if (StrUtils.isBlank(userAgent)) {
            return "Unknown";
        }
        if (userAgent.contains("Edg/")) {
            return "Edge";
        }
        if (userAgent.contains("Chrome/")) {
            return "Chrome";
        }
        if (userAgent.contains("Safari/")) {
            return "Safari";
        }
        if (userAgent.contains("Firefox/")) {
            return "Firefox";
        }
        return "Other";
    }

    private String parseOs(String userAgent) {
        if (StrUtils.isBlank(userAgent)) {
            return "Unknown";
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("Mac OS")) {
            return "macOS";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            return "iOS";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        return "Other";
    }
}
