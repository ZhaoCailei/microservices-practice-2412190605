package com.zjgsu.zcl.lost.client;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 服务发现寻址的单元测试：用 Mockito 模拟 DiscoveryClient、用 MockRestServiceServer 模拟下游 HTTP，
 * 全程不连接真实 Nacos，也不连接真实 user-service。
 */
class UserClientTest {

    private final DiscoveryClient discoveryClient = mock(DiscoveryClient.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    private final UserClient userClient = new UserClient(restTemplate, discoveryClient);

    private ServiceInstance instance() {
        return new DefaultServiceInstance("inst-1", UserClient.USER_SERVICE_NAME, "10.0.0.5", 8081, false);
    }

    @Test
    void throwsWhenInstanceListIsEmpty() {
        when(discoveryClient.getInstances(UserClient.USER_SERVICE_NAME)).thenReturn(Collections.emptyList());

        UserServiceUnavailableException ex = assertThrows(UserServiceUnavailableException.class,
                () -> userClient.checkUserExists(1L));

        assertTrue(ex.getMessage().contains("no available instance"));
        // 实例列表为空时不应发出任何 HTTP 请求
        server.verify();
    }

    @Test
    void usesDiscoveredInstanceUriAndOriginalPath() {
        when(discoveryClient.getInstances(UserClient.USER_SERVICE_NAME)).thenReturn(List.of(instance()));

        server.expect(requestTo("http://10.0.0.5:8081/api/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":1,\"username\":\"student_zhang\"}", MediaType.APPLICATION_JSON));

        assertTrue(userClient.checkUserExists(1L));
        server.verify();
    }

    @Test
    void returnsFalseWhenUserNotFoundButInstancesExist() {
        when(discoveryClient.getInstances(UserClient.USER_SERVICE_NAME)).thenReturn(List.of(instance()));

        server.expect(requestTo("http://10.0.0.5:8081/api/users/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // 用户不存在属于正常业务结果，不能当作依赖不可用
        assertFalse(userClient.checkUserExists(999L));
        server.verify();
    }

    @Test
    void queriesDiscoveryOnEveryCall() {
        when(discoveryClient.getInstances(UserClient.USER_SERVICE_NAME)).thenReturn(List.of(instance()));

        server.expect(requestTo("http://10.0.0.5:8081/api/users/1"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://10.0.0.5:8081/api/users/1"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        assertTrue(userClient.checkUserExists(1L));
        assertTrue(userClient.checkUserExists(1L));

        // 每次业务调用都重新查一次当前实例，而不是启动时缓存地址
        verify(discoveryClient, times(2)).getInstances(UserClient.USER_SERVICE_NAME);
        server.verify();
    }
}
