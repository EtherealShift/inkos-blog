package com.inkos.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.inkos.common.core.domain.Result;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>两条原则：
 * <ol>
 *   <li><b>业务异常透出 message，系统异常不透出</b> —— 未预期的异常可能包含表名、SQL 片段、
 *       内部路径等敏感信息，只回一句通用提示，细节写日志。</li>
 *   <li><b>HTTP 状态码与业务码双轨</b> —— HTTP 状态码便于网关/监控识别，业务码便于前端分支处理。</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} -> code={} message={}", request.getMethod(), request.getRequestURI(),
                e.getCode(), e.getMessage());
        return build(e.getCode(), e.getMessage());
    }

    // ==================== 认证与授权 ====================

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e, HttpServletRequest request) {
        log.warn("未登录访问 [{}] {} type={}", request.getMethod(), request.getRequestURI(), e.getType());
        return build(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage());
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<Result<Void>> handleNoPermission(RuntimeException e, HttpServletRequest request) {
        log.warn("权限不足 [{}] {} message={}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return build(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage());
    }

    // ==================== 参数校验 ====================

    /** {@code @RequestBody + @Valid} 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(StrUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining("；"));
        return build(ResultCode.BAD_REQUEST.getCode(), StrUtils.defaultIfBlank(message, "请求参数校验失败"));
    }

    /** 表单/查询参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(StrUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining("；"));
        return build(ResultCode.BAD_REQUEST.getCode(), StrUtils.defaultIfBlank(message, "请求参数校验失败"));
    }

    /** 方法参数上的 {@code @Validated} 约束失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(StrUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining("；"));
        return build(ResultCode.BAD_REQUEST.getCode(), StrUtils.defaultIfBlank(message, "请求参数校验失败"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return build(ResultCode.BAD_REQUEST.getCode(), "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ResultCode.BAD_REQUEST.getCode(), "参数类型不正确：" + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return build(ResultCode.BAD_REQUEST.getCode(), "请求体格式不正确");
    }

    // ==================== 其它框架异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(ResultCode.METHOD_NOT_ALLOWED.getCode(),
                "不支持的请求方法：" + e.getMethod());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return build(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMessage());
    }

    // ==================== 兜底 ====================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.error("系统异常 traceId={} [{}] {}", traceId, request.getMethod(), request.getRequestURI(), e);

        Result<Void> body = Result.<Void>fail(ResultCode.INTERNAL_ERROR).withTraceId(traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ==================== 内部方法 ====================

    private ResponseEntity<Result<Void>> build(int code, String message) {
        return ResponseEntity.status(resolveHttpStatus(code)).body(Result.fail(code, message));
    }

    /**
     * 业务码 → HTTP 状态码。
     *
     * <p>约定领域错误码为「HTTP 状态码 × 100 + 序号」（如 40101、40402），
     * 因此直接除以 100 即可还原出语义接近的 HTTP 状态码；无法映射的统一按 200 返回，
     * 由前端读取 {@code code} 字段判断。
     */
    private HttpStatus resolveHttpStatus(int code) {
        int candidate = code >= 10000 ? code / 100 : code;
        HttpStatus status = HttpStatus.resolve(candidate);
        return (status != null && status.isError()) ? status : HttpStatus.OK;
    }
}
