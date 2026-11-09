package com.campus.kb.auth;

import com.campus.kb.config.AppProperties;
import com.campus.kb.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private final SecretKey key;
    private final Duration expire;

    public JwtService(AppProperties properties) {
        String secret = properties.getAuth().getJwtSecret();
        if (secret == null || secret.isBlank()) {
            secret = java.util.UUID.randomUUID().toString().repeat(2);
            log.warn("未配置 JWT_SECRET，已生成临时密钥；服务重启后登录态会失效");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            try {
                bytes = MessageDigest.getInstance("SHA-256").digest(bytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("无法初始化 JWT 密钥", e);
            }
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expire = Duration.ofHours(Math.max(1, properties.getAuth().getTokenExpireHours()));
    }

    public String generate(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire.toMillis()))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}