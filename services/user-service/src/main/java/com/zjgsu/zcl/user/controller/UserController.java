package com.zjgsu.zcl.user.controller;

import com.zjgsu.zcl.user.model.User;
import com.zjgsu.zcl.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户服务对外接口。Controller 只做 HTTP 层的事：接参、调 Service、翻译状态码。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 供失物服务远程调用的接口：根据 ID 查询用户（不存在返回 404） */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 查询全部用户 */
    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userService.findAll());
    }
}
