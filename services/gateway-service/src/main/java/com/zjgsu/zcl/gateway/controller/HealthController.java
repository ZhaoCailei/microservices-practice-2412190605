package com.zjgsu.zcl.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关自身的健康检查入口：{@code GET /api/health}。
 *
 * <p>区别于 {@code /actuator/health}（Actuator 提供，含组件明细）：
 * 这个接口只回答“网关进程是否在运行”，返回固定 JSON，方便脚本和前端探活。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "gateway-service");
        body.put("port", 8080);
        return body;
    }
}
