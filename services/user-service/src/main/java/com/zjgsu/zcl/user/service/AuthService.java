package com.zjgsu.zcl.user.service;

import com.zjgsu.zcl.user.dto.LoginResponse;
import com.zjgsu.zcl.user.dto.UserInfo;
import com.zjgsu.zcl.user.model.User;
import com.zjgsu.zcl.user.repository.UserRepository;
import com.zjgsu.zcl.user.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务：登录校验、JWT 签发、请求携带的 Token 验签。
 *
 * <p>密码安全：
 * <ul>
 *   <li>数据库只存 BCrypt 哈希，不存明文；</li>
 *   <li>比对用 {@link PasswordEncoder#matches}，登录过程中不还原明文；</li>
 *   <li>用户名不存在与密码错误返回同一句提示，避免暴露账号是否存在。</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** 登录：校验用户名/密码，成功后签发 JWT */
    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new InvalidCredentialsException("用户名或密码错误"));

        String hash = user.getPasswordHash();
        if (hash == null || !passwordEncoder.matches(password, hash)) {
            throw new InvalidCredentialsException("用户名或密码错误");
        }

        String token = tokenService.issue(user);
        return new LoginResponse(token, "Bearer", tokenService.getExpirationSeconds(), UserInfo.from(user));
    }

    /**
     * 解析并验签请求里的 Token（{@code Authorization: Bearer <token>}）。
     *
     * <p>验签通过后直接使用 claims 中的身份信息，不再查库；
     * 签名错误、格式错误、已过期都视为认证失败。
     */
    public UserInfo currentUser(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        try {
            Jwt jwt = tokenService.parse(token);
            return new UserInfo(
                    Long.valueOf(jwt.getSubject()),
                    jwt.getClaimAsString("username"),
                    jwt.getClaimAsString("role"));
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidCredentialsException("Token 无效或已过期");
        }
    }

    private String extractBearerToken(String header) {
        if (header == null || header.isBlank()) {
            throw new InvalidCredentialsException("缺少 Authorization: Bearer <token>");
        }
        if (!header.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Authorization 格式应为 Bearer <token>");
        }
        return header.substring("Bearer ".length()).trim();
    }
}
