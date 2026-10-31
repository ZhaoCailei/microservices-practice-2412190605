package com.zjgsu.zcl.lost.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 调用 user-service 的 HTTP 客户端。
 *
 * 为了不引入任何额外 jar，这里只用了两类依赖：
 * - spring-web（RestTemplate / 请求工厂）：和 user-service 里用的 spring-web 是同一个 jar，必然存在；
 * - JDK 自带的 java.util.logging（从 JDK 1.4 起就有），不用 slf4j、也不用 commons-logging。
 *
 * 功能上保留三个关键点：
 * 1. 地址可配置：application.yml 里的 user-service.url，换端口 / 进 Docker 只改配置；
 * 2. 有超时：默认 RestTemplate 无超时，user-service 卡住时 lost-service 会一直挂着；
 * 3. 区分「用户不存在(404)」和「服务不可用」：前者返回 false，后者抛
 *    {@link UserServiceUnavailableException}，由 Controller 转成 503。
 */
@Component
public class UserClient {

    private static final Logger log = Logger.getLogger(UserClient.class.getName());

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UserClient(@Value("${user-service.url:http://localhost:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));   // 建立连接超时
        factory.setReadTimeout(Duration.ofSeconds(3));      // 等待响应超时
        this.restTemplate = new RestTemplate(factory);
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "http://localhost:8081"
                : baseUrl.replaceAll("/+$", "");
    }

    /**
     * 用户存在返回 true；user-service 明确返回 404 时返回 false；
     * 服务不可用（连不上 / 超时 / 5xx）时抛 {@link UserServiceUnavailableException}。
     */
    public boolean checkUserExists(Long userId) {
        if (userId == null) {
            return false;
        }
        String url = baseUrl + "/api/users/" + userId;
        try {
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            return response != null && response.get("id") != null;
        } catch (HttpClientErrorException.NotFound e) {
            log.info("用户 " + userId + " 不存在：user-service 返回 404");
            return false;
        } catch (RestClientException e) {
            log.log(Level.WARNING, "调用 user-service 失败，url=" + url, e);
            throw new UserServiceUnavailableException("user-service 调用失败：" + url, e);
        }
    }
}
