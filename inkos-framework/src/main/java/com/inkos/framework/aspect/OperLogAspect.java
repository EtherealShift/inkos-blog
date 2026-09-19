package com.inkos.framework.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkos.common.annotation.OperLog;
import com.inkos.common.util.StrUtils;
import com.inkos.framework.security.SecurityUtils;
import com.inkos.system.entity.SysOperLog;
import com.inkos.system.service.SysOperLogService;
import com.inkos.system.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 操作日志切面。
 *
 * <p>设计要点：<b>审计失败绝不能影响业务</b>。所有日志写入都包在 try/catch 里，
 * 且放在 finally 中执行，保证异常路径也能留痕。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 请求参数中需要脱敏的字段名 */
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|confirmPassword|token|secret|apiKey)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    /** 单字段最大留存长度，避免大请求体撑爆日志表 */
    private static final int MAX_TEXT_LENGTH = 2000;

    private final SysOperLogService operLogService;
    private final SysUserService userService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                save(joinPoint, operLog, result, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 日志记录失败不影响主流程
                log.warn("操作日志记录失败: {}", e.getMessage());
            }
        }
    }

    private void save(ProceedingJoinPoint joinPoint, OperLog annotation, Object result,
                      Throwable error, long costTime) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        HttpServletRequest request = SecurityUtils.currentRequest();

        SysOperLog operLog = new SysOperLog();
        operLog.setTitle(annotation.title());
        operLog.setBusinessType(annotation.businessType().getDesc());
        operLog.setMethod(signature.getDeclaringTypeName() + "#" + signature.getName());
        operLog.setOperUrl(request == null ? null : StrUtils.abbreviate(request.getRequestURI(), 255));
        operLog.setRequestMethod(request == null ? null : request.getMethod());
        operLog.setOperIp(SecurityUtils.getClientIp());
        operLog.setOperName(resolveOperName());
        operLog.setStatus(error == null ? 1 : 0);
        operLog.setErrorMsg(error == null ? null : StrUtils.abbreviate(error.getMessage(), MAX_TEXT_LENGTH));
        operLog.setCostTime(costTime);
        operLog.setOperTime(LocalDateTime.now());

        if (annotation.saveRequestData()) {
            operLog.setOperParam(sanitize(joinPoint.getArgs()));
        }
        if (annotation.saveResponseData() && result != null) {
            operLog.setJsonResult(sanitize(new Object[]{result}));
        }

        operLogService.record(operLog);
    }

    /**
     * 登录名优先取会话，取不到再回查数据库 —— 会话过期后仍能追溯到操作人。
     */
    private String resolveOperName() {
        Long userId = SecurityUtils.getUserIdOrNull();
        if (userId == null) {
            return "anonymous";
        }
        try {
            var user = userService.getById(userId);
            return user == null ? String.valueOf(userId) : user.getUsername();
        } catch (Exception e) {
            return String.valueOf(userId);
        }
    }

    /**
     * 参数序列化 + 脱敏。过滤掉无法/不应序列化的类型（文件流、Servlet 对象）。
     */
    private String sanitize(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object[] loggable = Arrays.stream(args)
                .filter(arg -> !(arg instanceof MultipartFile)
                        && !(arg instanceof HttpServletRequest)
                        && !(arg instanceof HttpServletResponse))
                .toArray();

        if (loggable.length == 0) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(loggable);
            return StrUtils.abbreviate(SENSITIVE_FIELD.matcher(json).replaceAll("$1\"***\""), MAX_TEXT_LENGTH);
        } catch (Exception e) {
            return StrUtils.abbreviate(Arrays.toString(loggable), MAX_TEXT_LENGTH);
        }
    }
}
