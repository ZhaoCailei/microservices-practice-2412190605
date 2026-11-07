package com.zjgsu.zcl.lost.client;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 通过 Nacos 服务发现获取 user-service 的实例地址，再调用其原有业务接口 GET /api/users/{id}。
 *
 * <p>要点：
 * <ul>
 *   <li>每次调用都向 {@link DiscoveryClient} 取“当前已知实例”，不在启动时缓存地址；</li>
 *   <li>实例列表为空说明依赖服务不可用，抛 {@link UserServiceUnavailableException}，由控制器返回 503；</li>
 *   <li>对方返回 404（用户不存在）属于正常业务结果，返回 false，保留原有业务响应；</li>
 *   <li>连接失败 / 超时 / 5xx 同样视为依赖不可用。</li>
 * </ul>
 */
@Component
public class UserClient {

    /** 被调用的服务名，与 user-service 的 spring.application.name 一致 */
    public static final String USER_SERVICE_NAME = "user-service";

    /** user-service 原有的业务路径 */
    public static final String USER_PATH = "/api/users/{id}";

    private final RestTemplate restTemplate;

    private final DiscoveryClient discoveryClient;

    public UserClient(RestTemplate restTemplate, DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
    }

    /**
     * 校验用户是否存在：本次请求实时解析实例地址，只选第一个实例（本周不要求轮询/权重/重试）。
     */
    public boolean checkUserExists(Long userId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(USER_SERVICE_NAME);
        if (instances == null || instances.isEmpty()) {
            throw new UserServiceUnavailableException(
                    "no available instance of service '" + USER_SERVICE_NAME + "'", null);
        }

        ServiceInstance instance = instances.get(0);
        URI uri = UriComponentsBuilder.fromUri(instance.getUri())
                .path(USER_PATH)
                .buildAndExpand(userId)
                .encode()
                .toUri();

        try {
            Map<?, ?> response = restTemplate.getForObject(uri, Map.class);
            return response != null && response.containsKey("id");
        } catch (HttpClientErrorException.NotFound e) {
            // 用户不存在：保留原有业务语义（由上层返回原有的 400 业务响应），不算依赖不可用
            return false;
        } catch (RestClientException e) {
            // 连不上、超时、对方 5xx 等：基础设施层面故障
            throw new UserServiceUnavailableException(
                    "call " + USER_SERVICE_NAME + " failed: " + e.getMessage(), e);
        }
    }
}
