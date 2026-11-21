package com.zjgsu.zcl.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * “当前用户”接口：演示下游服务只信任网关。
 *
 * <p>它不验 JWT，而是校验 {@code X-Gateway-Signature}：
 * 网关用共享密钥对 {@code userId:role} 做 HMAC-SHA256 后放在请求头里，
 * 客户端直接访问 8081 或自己伪造 {@code X-User-Role: ADMIN} 都无法算出正确签名，
 * 因此只认“经网关转发”的请求。
 */
@RestController
@RequestMapping("/api/users")
public class CurrentUserController {

    private static final String HEADER_SIGNATURE = "X-Gateway-Signature";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-Username";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final String sharedSecret;

    public CurrentUserController(@Value("${app.gateway.shared-secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @GetMapping("/current")
    public ResponseEntity<?> current(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_NAME, required = false) String username,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String role,
            @RequestHeader(value = HEADER_SIGNATURE, required = false) String signature) {

        if (sharedSecret == null || sharedSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(error("服务未配置 GATEWAY_SHARED_SECRET"));
        }
        if (userId == null || role == null || signature == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("只接受来自网关的请求：缺少网关身份头"));
        }

        String expected = sign(userId, role);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("身份头签名无效：请求不是网关转发，或身份头被伪造"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        try {
            body.put("id", Long.valueOf(userId));
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("身份头非法"));
        }
        body.put("username", username);
        body.put("role", role);
        body.put("trustedSource", "gateway");
        return ResponseEntity.ok(body);
    }

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

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
