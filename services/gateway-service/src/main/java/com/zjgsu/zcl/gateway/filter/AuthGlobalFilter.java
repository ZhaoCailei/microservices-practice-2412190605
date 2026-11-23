package com.zjgsu.zcl.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Set;

/**
 * 网关鉴权全局过滤器。
 *
 * <p>规则：
 * <ol>
 *   <li>公开路径放行：{@code /api/health}、{@code /actuator/health}、{@code /api/auth/login}；</li>
 *   <li>其余路径必须带合法 {@code Authorization: Bearer <JWT>}，否则 401；</li>
 *   <li>{@code /api/admin/**} 额外要求 {@code role=ADMIN}，否则 403；</li>
 *   <li>验签通过后，<b>先删除</b>客户端可能伪造的身份头（X-User-Id/X-User-Role/X-Username），
 *       再写入网关自己算出的身份头和 HMAC 签名，下游只认这个签名。</li>
 * </ol>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-Username";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_SIGNATURE = "X-Gateway-Signature";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
//白名单：自定义健康检查，健康检查（服务是否还活着），登录
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/health",
            "/actuator/health",
            "/api/auth/login");
    //身份解析器
    private final JwtDecoder jwtDecoder;
    //防伪私匙
    private final String sharedSecret;

    public AuthGlobalFilter(JwtDecoder jwtDecoder,
                            @Value("${app.gateway.shared-secret:}") String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalStateException("未配置 GATEWAY_SHARED_SECRET：下游服务要靠它校验请求确实来自网关");
        }
        this.jwtDecoder = jwtDecoder;
        this.sharedSecret = sharedSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 1) 健康检查、登录等公开入口不拦
        if (PUBLIC_PATHS.contains(path) || "OPTIONS".equals(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        // 2) 必须有 Bearer Token，否则401
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "未登录：缺少 Authorization: Bearer <token>");
        }

        // 3) 验签（签名错误、格式错误、过期都会失败）
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()).trim());
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Token 无效或已过期");
        }

        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("username");
        String role = jwt.getClaimAsString("role");

        // 4) 角色校验：/api/admin/** 只有 ADMIN 能过，如果 JWT 里的角色不是 ADMIN，直接返回 HTTP 403（禁止访问）。
        if (path.startsWith(ADMIN_PATH_PREFIX) && !"ADMIN".equals(role)) {
            return reject(exchange, HttpStatus.FORBIDDEN, "角色不足：该接口需要 ADMIN");
        }

        // 5) 覆盖伪造的身份头，写入网关验证过的身份 + HMAC 签名
        String signature = sign(userId, role);
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    //删除客户端带来的所有身份头
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_NAME);
                    headers.remove(HEADER_USER_ROLE);
                    headers.remove(HEADER_SIGNATURE);
                    // 重新塞入网关验证过的身份信息

                    headers.set(HEADER_USER_ID, userId);
                    headers.set(HEADER_USER_NAME, username);
                    headers.set(HEADER_USER_ROLE, role);
                    //塞入防伪签名
                    headers.set(HEADER_SIGNATURE, signature);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /** 下游按同样算法校验：HMAC-SHA256(sharedSecret, "userId:role") */
    private String sign(String userId, String role) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((userId + ":" + role).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("计算网关签名失败", e);
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        // 早于路由与转发过滤器执行
        return -100;
    }
}
