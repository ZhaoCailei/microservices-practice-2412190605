package com.zjgsu.zcl.lost.client;

import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 跨服务调用的门面：对上层只暴露 {@link #checkUserExists(Long)}，内部用 OpenFeign 声明式客户端。
 *
 * <p>与第七周手工寻址（DiscoveryClient + RestTemplate 自己拼 URI）相比：
 * <ul>
 *   <li>地址解析交给 Feign + LoadBalancer，代码里只写服务名；</li>
 *   <li>失败处理仍然集中在这里，不把 FeignException 泄漏给业务层。</li>
 * </ul>
 *
 * <p>失败语义：
 * <ul>
 *   <li>404（用户不存在）→ 返回 false，交给上层按原有业务语义返回 400；</li>
 *   <li>其他 FeignException（5xx、解码失败、请求构造失败）→ {@link UserServiceUnavailableException}，由控制器返回 503；</li>
 *   <li>负载均衡器找不到实例、连接超时等运行时异常 → 同样返回 503。</li>
 * </ul>
 */
@Component
public class UserClient {

    /** 被调用的服务名，与 user-service 的 spring.application.name 一致 */
    public static final String USER_SERVICE_NAME = "user-service";

    /** user-service 原有的业务路径 */
    public static final String USER_PATH = "/api/users/{id}";

    private final UserServiceClient userServiceClient;

    public UserClient(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    /**
     * 校验用户是否存在。本次请求由 LoadBalancer 实时选实例，只取一个（本周不要求轮询/权重/重试）。
     */
    public boolean checkUserExists(Long userId) {
        try {
            Map<String, Object> user = userServiceClient.findById(userId);
            return user != null && user.get("id") != null;
        } catch (FeignException.NotFound e) {
            // 用户不存在：对方明确的业务答案，保留原有 400 语义，不算依赖不可用
            return false;
        } catch (FeignException e) {
            // status > 0：对方返回了非 2xx；status <= 0：Feign 在通信阶段失败（无实例、连接被拒、超时）
            throw new UserServiceUnavailableException("调用 " + USER_SERVICE_NAME + " 失败："
                    + (e.status() > 0
                    ? "对方返回 HTTP " + e.status()
                    : "无法连接到 " + USER_SERVICE_NAME + "（注册中心无可用实例、连接失败或超时）"), e);
        } catch (RuntimeException e) {
            // 负载均衡器没有可用实例、连接/读取超时等
            throw new UserServiceUnavailableException(
                    "调用 " + USER_SERVICE_NAME + " 失败：" + e.getMessage(), e);
        }
    }
}
