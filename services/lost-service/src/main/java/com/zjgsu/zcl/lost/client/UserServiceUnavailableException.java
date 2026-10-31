package com.zjgsu.zcl.lost.client;

/**
 * 调用 user-service 出现“基础设施层面”故障时抛出（连不上、超时、对方 5xx 等）。
 * 注意：与“用户不存在（404）”区分开，404 属于正常业务结果，不抛这个异常。
 */
public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
