package com.zjgsu.zcl.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // 测试不依赖真实 Nacos：关闭注册与发现
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        // 测试专用密钥（不是生产密钥）：JWT 验签 + 网关身份头签名
        "app.jwt.secret=MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=",
        "app.gateway.shared-secret=test-gateway-shared-secret"
})
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
        // 验证路由配置（含鉴权过滤器）能被正确解析、上下文能正常加载
    }
}
