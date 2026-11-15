package com.zjgsu.zcl.lost.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * user-service 的声明式 HTTP 客户端。
 *
 * <p>关键点：这里的 {@code name = "user-service"} 是<b>服务名</b>，不是容器 IP 或固定端口。
 * Spring Cloud LoadBalancer 会从注册中心（Nacos / 分组 ZCL_GROUP）取出该服务名下的实例，
 * 选择一个作为本次请求的目标地址，所以服务换 IP、扩副本都不需要改代码。
 *
 * <p>{@code path = "/api/users"} 是 user-service 原有的业务路径前缀，方法上的
 * {@code @GetMapping("/{id}")} 与之拼成完整的 {@code GET /api/users/{id}}。
 */
@FeignClient(name = UserClient.USER_SERVICE_NAME, path = UserServiceClient.BASE_PATH)
public interface UserServiceClient {

    /** user-service 的业务路径前缀 */
    String BASE_PATH = "/api/users";

    /**
     * 查询用户。用户存在返回 200 + JSON；用户不存在返回 404（Feign 会把非 2xx 转成 FeignException）。
     */
    @GetMapping("/{id}")
    Map<String, Object> findById(@PathVariable("id") Long id);
}
