package com.zjgsu.zcl.user.security;

import com.zjgsu.zcl.user.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * JWT 签发与验签。
 *
 * <p>使用 HMAC-SHA256（对称密钥）：
 * <ul>
 *   <li>签发：把用户 id（subject）、用户名、角色写进 claims，并设置签发时间与过期时间；</li>
 *   <li>验签：由 {@link JwtDecoder} 校验签名与过期时间，任何篡改都会抛异常。</li>
 * </ul>
 * 密钥不写在代码里，由 {@link JwtConfig} 从环境变量 {@code JWT_SECRET} 读取。
 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expirationSeconds;

    public JwtTokenService(JwtEncoder encoder,
                           JwtDecoder decoder,
                           @Value("${app.jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.expirationSeconds = expirationSeconds;
    }

    /** 为登录成功的用户签发 Token：subject=用户 id，claims 里带 username 与 role */
    public String issue(User user) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("user-service")
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 验签：签名不对、格式不对、已过期都会抛 JwtException，由调用方翻译成 401 */
    public Jwt parse(String token) {
        return decoder.decode(token);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
