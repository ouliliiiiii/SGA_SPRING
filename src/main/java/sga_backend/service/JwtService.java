package sga_backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${sga.jwt.secret}")
    private String secret;

    @Value("${sga.jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String genererToken(String login) {
        return Jwts.builder()
                .subject(login)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extraireLogin(String token) {
        return extraireClaim(token, Claims::getSubject);
    }

    public Date extraireExpiration(String token) {
        return extraireClaim(token, Claims::getExpiration);
    }

    public <T> T extraireClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    public boolean estExpire(String token) {
        return extraireExpiration(token).before(new Date());
    }

    public boolean estValide(String token, String login) {
        final String loginDuToken = extraireLogin(token);
        return loginDuToken.equals(login) && !estExpire(token);
    }
}