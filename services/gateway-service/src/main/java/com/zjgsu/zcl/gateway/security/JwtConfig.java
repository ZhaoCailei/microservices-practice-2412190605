package com.zjgsu.zcl.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 网关侧 JWT 验签配置。
 *
 * <p>与 user-service 共用同一个 {@code JWT_SECRET}：用户服务签发，网关只验签，不签发。
 * 密钥缺失时启动失败，避免网关“不验签”静默放行。
 */
@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret:}") String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT_SECRET：网关需要用它与 user-service 共用同一密钥验签");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET 不是合法的 Base64 字符串", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 解码后不足 32 字节，HS256 要求至少 256 位密钥");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
