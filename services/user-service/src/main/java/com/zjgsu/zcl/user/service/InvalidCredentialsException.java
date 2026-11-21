package com.zjgsu.zcl.user.service;

/** 认证失败（用户名/密码错误、Token 无效）：Controller 统一翻译成 401 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
