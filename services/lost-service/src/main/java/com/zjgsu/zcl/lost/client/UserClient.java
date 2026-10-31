package com.zjgsu.zcl.lost.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Component
public class UserClient {

    private final RestTemplate restTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    public UserClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean checkUserExists(Long userId) {
        String url = userServiceUrl + "/" + userId;
        try {
            Map response = restTemplate.getForObject(url, Map.class);
            return response != null && response.containsKey("id");
        } catch (Exception e) {
            return false; // 捕获超时或404异常
        }
    }
}