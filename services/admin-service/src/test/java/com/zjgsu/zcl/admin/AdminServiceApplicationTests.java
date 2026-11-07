package com.zjgsu.zcl.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		// 测试不依赖真实 Nacos：关闭注册与发现
		"spring.cloud.nacos.discovery.enabled=false",
		"spring.cloud.nacos.discovery.register-enabled=false",
		"spring.cloud.service-registry.auto-registration.enabled=false",
		// 测试不依赖真实 MySQL：改用 H2 内存数据库
		"spring.datasource.url=jdbc:h2:mem:admindb;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.sql.init.mode=never"
})
class AdminServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
