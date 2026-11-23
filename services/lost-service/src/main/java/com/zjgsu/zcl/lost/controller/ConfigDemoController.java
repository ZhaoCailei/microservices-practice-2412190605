package com.zjgsu.zcl.lost.controller;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置中心验证端点：证明 lost-service 的 Feign 超时、日志级别确实来自 Nacos。
 */
@RefreshScope
@RestController
@RequestMapping("/api/lost-items")
public class ConfigDemoController {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger("com.zjgsu.zcl.lost");

    private final long connectTimeout;
    private final long readTimeout;
    private final int poolSize;

    public ConfigDemoController(
            @Value("${spring.cloud.openfeign.client.config.default.connect-timeout:3000}") long connectTimeout,
            @Value("${spring.cloud.openfeign.client.config.default.read-timeout:3000}") long readTimeout,
            @Value("${app.demo.pool-size:3}") int poolSize) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.poolSize = poolSize;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("feignConnectTimeout", connectTimeout);
        body.put("feignReadTimeout", readTimeout);
        body.put("poolSize", poolSize);
        body.put("logLevel", currentLogLevel());
        body.put("source", "nacos:lost-service.yaml");
        return body;
    }

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
