package com.zjgsu.zcl.lost.client;

import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserClient 的单元测试：用 Mockito 模拟 Feign 客户端，不连接真实 Nacos，也不连接真实 user-service。
 *
 * <p>覆盖四种情况：用户存在、用户不存在（404）、对方 5xx、负载均衡器找不到实例（依赖不可用）。
 */
class UserClientTest {

    private final UserServiceClient feignClient = mock(UserServiceClient.class);

    private final UserClient userClient = new UserClient(feignClient);

    @Test
    void returnsTrueWhenUserExists() {
        when(feignClient.findById(1L)).thenReturn(Map.of("id", 1, "username", "student_zhang"));

        assertTrue(userClient.checkUserExists(1L));
        verify(feignClient).findById(1L);
    }

    @Test
    void returnsFalseWhenUserNotFound() {
        when(feignClient.findById(999L)).thenThrow(feignException(404));

        // 用户不存在属于正常业务结果，不能当作依赖不可用
        assertFalse(userClient.checkUserExists(999L));
    }

    @Test
    void throwsUnavailableWhenRemoteReturnsServerError() {
        when(feignClient.findById(1L)).thenThrow(feignException(500));

        UserServiceUnavailableException ex = assertThrows(UserServiceUnavailableException.class,
                () -> userClient.checkUserExists(1L));

        assertTrue(ex.getMessage().contains("HTTP 500"), "错误信息应包含对方状态码：" + ex.getMessage());
    }

    @Test
    void throwsUnavailableWhenNoInstanceAvailable() {
        // 模拟负载均衡器找不到 user-service 实例（例如服务刚下线、实例列表为空）
        when(feignClient.findById(1L)).thenThrow(new IllegalStateException(
                "Load balancer does not contain an instance for the service " + UserClient.USER_SERVICE_NAME));

        UserServiceUnavailableException ex = assertThrows(UserServiceUnavailableException.class,
                () -> userClient.checkUserExists(1L));

        assertTrue(ex.getMessage().contains("user-service"));
    }

    /** 用 Feign 的官方工厂构造指定状态码的异常，语义与真实调用一致（404 会落在 FeignException.NotFound）。 */
    private FeignException feignException(int status) {
        String url = "http://" + UserClient.USER_SERVICE_NAME + "/api/users/999";
        Request request = Request.create(Request.HttpMethod.GET, url,
                new LinkedHashMap<String, Collection<String>>(), null, StandardCharsets.UTF_8);

        Response response = Response.builder()
                .request(request)
                .status(status)
                .reason("mock-" + status)
                .headers(new LinkedHashMap<String, Collection<String>>())
                .body("{\"error\":\"mock\"}", StandardCharsets.UTF_8)
                .build();

        return FeignException.errorStatus("UserServiceClient#findById(Long)", response);
    }
}
