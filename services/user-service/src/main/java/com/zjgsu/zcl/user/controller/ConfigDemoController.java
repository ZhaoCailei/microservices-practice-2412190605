package com.zjgsu.zcl.user.controller;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置中心验证端点：把从 Nacos 读到的非敏感配置暴露出来，供 scripts/config-test.sh 断言。
 *
 * <p>{@code @RefreshScope} 让本 Bean 在 Nacos 配置变更后重建，重新注入 {@code @Value}，
 * 从而做到“改配置不重启服务就生效”。
 */
@RefreshScope
@RestController
@RequestMapping("/api/users")
public class ConfigDemoController {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger("com.zjgsu.zcl.user");

    private final long jwtExpirationSeconds;
    private final int poolSize;

    public ConfigDemoController(
            @Value("${app.jwt.expiration-seconds:3600}") long jwtExpirationSeconds,
            @Value("${app.demo.pool-size:5}") int poolSize) {
        this.jwtExpirationSeconds = jwtExpirationSeconds;
        this.poolSize = poolSize;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jwtExpirationSeconds", jwtExpirationSeconds);
        body.put("poolSize", poolSize);
        body.put("logLevel", currentLogLevel());
        body.put("source", "nacos:user-service.yaml");
        return body;
    }

    /** 当前生效的日志级别（Nacos 的 logging.level.* 变更后由 LoggingRebinder 刷新） */
    private String currentLogLevel() {
        if (log.isDebugEnabled()) {
            return "DEBUG";
        }
        if (log.isInfoEnabled()) {
            return "INFO";
        }
        if (log.isWarnEnabled()) {
            return "WARN";
        }
        return "ERROR";
    }
}
