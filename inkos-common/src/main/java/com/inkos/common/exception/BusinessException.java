package com.inkos.common.exception;

import com.inkos.common.core.enums.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>约定：能被 {@code GlobalExceptionHandler} 捕获并原样透出 message 的异常，
 * 都必须是本类或其子类；其它未捕获异常一律按 5xxxx 处理且不泄漏堆栈细节给前端。
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAIL.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.code = resultCode.getCode();
    }

    // ==================== 便捷工厂 ====================

    public static BusinessException of(String message) {
        return new BusinessException(message);
    }

    public static BusinessException of(ResultCode resultCode) {
        return new BusinessException(resultCode);
    }

    public static BusinessException of(ResultCode resultCode, String message) {
        return new BusinessException(resultCode, message);
    }

    /** 条件成立时抛出，用于替代大量 if-throw 样板 */
    public static void throwIf(boolean condition, ResultCode resultCode) {
        if (condition) {
            throw new BusinessException(resultCode);
        }
    }

    public static void throwIf(boolean condition, String message) {
        if (condition) {
            throw new BusinessException(message);
        }
    }

    /**
     * 业务异常不需要堆栈，重写以降低高频抛出场景的开销。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
