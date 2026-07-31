package com.sxw.sxwaiagent.common.api;

/**
 * 统一响应体。
 *
 * @param code    业务码，0 表示成功，非 0 表示错误
 * @param message 描述信息
 * @param data    业务数据
 * @param <T>     数据类型
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
