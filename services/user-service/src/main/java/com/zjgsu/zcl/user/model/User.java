package com.zjgsu.zcl.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * 用户实体。
 *
 * <p>密码只存哈希（BCrypt，形如 {@code $2a$10$...}），不存明文；
 * 哈希字段用 {@link JsonIgnore} 屏蔽，避免任何接口把密码哈希序列化出去。
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt 哈希；允许为空是为了兼容历史数据，登录时会明确拒绝无密码的账号 */
    @Column(name = "password_hash", length = 100)
    @JsonIgnore
    private String passwordHash;

    /** 角色：USER（普通用户）/ ADMIN（管理员） */
    @Column(length = 20)
    private String role;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
