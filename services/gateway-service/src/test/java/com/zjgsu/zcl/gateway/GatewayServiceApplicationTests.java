package com.zjgsu.zcl.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // 测试不依赖真实 Nacos：关闭注册与发现
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
        // 验证路由配置能被正确解析、上下文能正常加载
    }
}
