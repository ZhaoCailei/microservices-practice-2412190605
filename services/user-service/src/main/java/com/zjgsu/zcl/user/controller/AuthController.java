package com.zjgsu.zcl.user.controller;

import com.zjgsu.zcl.user.dto.LoginRequest;
import com.zjgsu.zcl.user.service.AuthService;
import com.zjgsu.zcl.user.service.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口。
 *
 * <p>Controller 只负责 HTTP 语义：
 * <ul>
 *   <li>{@code POST /api/auth/login}：成功 200 返回 token；参数缺失 400；凭据错误 401。</li>
 *   <li>{@code GET /api/auth/me}：验证 Bearer Token 的签名；有效 200，缺失/无效/过期 401。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest request) {
        LoginRequest req = request == null ? new LoginRequest(null, null) : request;
        try {
            return ResponseEntity.ok(authService.login(req.username(), req.password()));
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            return ResponseEntity.ok(authService.currentUser(authorization));
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(e.getMessage()));
        }
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
