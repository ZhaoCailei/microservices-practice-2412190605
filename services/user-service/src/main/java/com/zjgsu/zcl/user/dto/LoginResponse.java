package com.zjgsu.zcl.user.dto;

/** 登录成功响应：token + 类型 + 有效期（秒）+ 当前用户信息 */
public record LoginResponse(String token, String tokenType, long expiresIn, UserInfo user) {
}
