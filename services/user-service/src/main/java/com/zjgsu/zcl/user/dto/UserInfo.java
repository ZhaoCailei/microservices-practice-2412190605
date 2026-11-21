package com.zjgsu.zcl.user.dto;

import com.zjgsu.zcl.user.model.User;

/** 对外返回的用户信息：只暴露 id / 用户名 / 角色，不含密码哈希 */
public record UserInfo(Long id, String username, String role) {

    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getRole());
    }
}
