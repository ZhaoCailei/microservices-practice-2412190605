package com.zjgsu.zcl.user.dto;

/** 登录请求：用户名 + 明文密码（仅用于本次请求的哈希比对，不落库） */
public record LoginRequest(String username, String password) {
}
