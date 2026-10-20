package org.skilltransfer.icnsubway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * HS256 토큰 발급·검증. 키는 JWT_SECRET(Base64) → data/.jwt-secret 파일 → 새 32바이트 난수 순으로 정한다.
 * 서명은 암호화가 아니므로 페이로드에는 사용자 id·이름만 넣는다.
 */
@Service
public class JwtService {
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final long ttlSeconds;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.secret-file}") Path secretFile,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.ttl-seconds}") long ttlSeconds) throws IOException {
        this.key = Keys.hmacShaKeyFor(loadOrCreateKey(secret, secretFile));
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 서명·발급자·만료·알고리즘을 확인하고 사용자 id를 돌려준다. 실패하면 JwtException. */
    public long verify(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token);
        if (!"HS256".equals(jws.getHeader().getAlgorithm())) throw new JwtException("unexpected algorithm");
        try {
            return Long.parseLong(jws.getPayload().getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("bad subject");
        }
    }

    public long ttlSeconds() { return ttlSeconds; }

    private static byte[] loadOrCreateKey(String secret, Path file) throws IOException {
        byte[] bytes;
        if (secret != null && !secret.isBlank()) {
            bytes = Base64.getDecoder().decode(secret.trim());
        } else if (Files.exists(file)) {
            bytes = Base64.getDecoder().decode(Files.readString(file).trim());
        } else {
            bytes = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(bytes);
            if (file.toAbsolutePath().getParent() != null) Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, Base64.getEncoder().encodeToString(bytes));
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            }
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT 키는 32바이트 이상이어야 합니다. JWT_SECRET 또는 키 파일을 확인하세요.");
        }
        return bytes;
    }
}
