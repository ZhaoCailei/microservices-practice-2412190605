package com.zjgsu.zcl.user.service;

import com.zjgsu.zcl.user.dto.LoginResponse;
import com.zjgsu.zcl.user.dto.UserInfo;
import com.zjgsu.zcl.user.model.User;
import com.zjgsu.zcl.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证服务测试：登录成功/失败、JWT 签发、验签（含被篡改的 Token）。
 *
 * <p>用 H2 内存库，密码在测试里现算 BCrypt 哈希入库，不依赖真实 MySQL / Nacos。
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:userdb_auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        // 测试专用密钥（32 个 0 的 Base64），不是生产密钥
        "app.jwt.secret=MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
})
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createUser(String username, String rawPassword, String role) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    @Test
    void login_shouldReturnTokenAndUserInfo() {
        createUser("auth_ok", "Secret@123", "USER");

        LoginResponse response = authService.login("auth_ok", "Secret@123");

        assertNotNull(response);
        assertEquals("Bearer", response.tokenType());
        assertTrue(response.expiresIn() > 0);
        assertEquals(3, response.token().split("\\.").length, "JWT 应由 header.payload.signature 三段组成");
        assertEquals("auth_ok", response.user().username());
        assertEquals("USER", response.user().role());
    }

    @Test
    void login_shouldRejectWrongPassword() {
        createUser("auth_wrong", "Secret@123", "USER");

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login("auth_wrong", "not-the-password"));
    }

    @Test
    void login_shouldRejectUnknownUser() {
        assertThrows(InvalidCredentialsException.class,
                () -> authService.login("nobody", "whatever"));
    }

    @Test
    void login_shouldRejectBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> authService.login("", "x"));
        assertThrows(IllegalArgumentException.class, () -> authService.login("x", null));
    }

    @Test
    void me_shouldReturnClaimsForValidToken() {
        createUser("auth_me", "Secret@123", "ADMIN");
        String token = authService.login("auth_me", "Secret@123").token();

        UserInfo info = authService.currentUser("Bearer " + token);

        assertEquals("auth_me", info.username());
        assertEquals("ADMIN", info.role());
    }

    @Test
    void me_shouldRejectTamperedToken() {
        createUser("auth_tampered", "Secret@123", "USER");
        String token = authService.login("auth_tampered", "Secret@123").token();
        // 改动签名段的最后一个字符，签名校验必然失败
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThrows(InvalidCredentialsException.class,
                () -> authService.currentUser("Bearer " + tampered));
    }

    @Test
    void me_shouldRejectMissingOrMalformedHeader() {
        assertThrows(InvalidCredentialsException.class, () -> authService.currentUser(null));
        assertThrows(InvalidCredentialsException.class, () -> authService.currentUser("Basic abc"));
    }
}
