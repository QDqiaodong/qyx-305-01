package com.risk.exception;

/**
 * 业务校验失败异常：用于放行/排班等“必须明确说清原因”的失败。
 * 由 GlobalExceptionHandler 统一转成 400 + 明确原因，避免悄悄返回或出绿单。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
