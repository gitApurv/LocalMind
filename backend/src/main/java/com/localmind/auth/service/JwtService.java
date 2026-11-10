package com.localmind.auth.service;

import com.localmind.common.security.CurrentUserIdArgumentResolver;
import com.localmind.common.security.JwtProperties;
import com.localmind.user.entity.User;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed access tokens. Validation is handled by the Spring Security resource server.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String ISSUER = "localmind";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenExpiration())).subject(user.getId())
                .claim(CurrentUserIdArgumentResolver.USER_ID_CLAIM, user.getId()).claim("email", user.getEmail())
                .claim("fullName", Objects.requireNonNullElse(user.getFullName(), "")).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
