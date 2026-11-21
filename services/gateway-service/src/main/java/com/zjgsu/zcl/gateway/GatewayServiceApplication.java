package com.zjgsu.zcl.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关：统一对外端口 8080，按路径前缀把请求转发给各微服务。
 *
 * <p>路由配置在 application.yml 中，目标使用服务名（{@code lb://user-service} 等），
 * 由 LoadBalancer + Nacos 解析为具体实例，网关代码里不写任何实例 IP。
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
