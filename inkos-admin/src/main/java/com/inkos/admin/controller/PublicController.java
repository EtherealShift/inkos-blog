package com.inkos.admin.controller;

import com.inkos.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公共探活接口。
 *
 * <p>放在白名单内，用于部署后的存活验证与前后端联调自检：
 * 无需登录即可确认「应用起了、路由通了、DB 连上了（通过 /actuator/health）」。
 */
@Tag(name = "00. 公共", description = "无需登录的探活与基础接口")
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    @Value("${spring.application.name:inkos-blog}")
    private String applicationName;

    @Operation(summary = "探活")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", applicationName);
        data.put("status", "UP");
        data.put("time", LocalDateTime.now());
        return Result.ok("pong", data);
    }
}
