package com.zjgsu.zcl.user.controller;

import com.zjgsu.zcl.user.model.User;
import com.zjgsu.zcl.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务对外接口。Controller 只做 HTTP 层的事：接参、调 Service、翻译状态码。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    /** 实例标识：优先用 compose 注入的 INSTANCE_ID，容器里退化为 HOSTNAME，本地运行时为 local */
    private final String instanceId = System.getenv().getOrDefault("INSTANCE_ID",
            System.getenv().getOrDefault("HOSTNAME", "local"));

    /** 本实例收到的查询次数，便于在日志里核对负载均衡是否真的分发到两个实例 */
    private final AtomicLong requestCounter = new AtomicLong();

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 供失物服务远程调用的接口：根据 ID 查询用户（不存在返回 404） */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        long count = requestCounter.incrementAndGet();
        // 每次调用都留下“哪个实例、第几次”的痕迹，用于验证负载均衡
        log.info("instance={} 本实例第 {} 次查询，userId={}", instanceId, count, id);

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
