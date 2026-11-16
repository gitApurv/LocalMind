package com.localmind.auth.service;

import com.localmind.common.security.CurrentUserIdArgumentResolver;
import com.localmind.common.security.JwtProperties;
import com.localmind.common.security.SecurityConfig;
import com.localmind.user.entity.User;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed access tokens. Validation is handled by the Spring Security resource server. */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        String fullName = Objects.requireNonNullElse(user.getFullName(), "");
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(SecurityConfig.TOKEN_ISSUER).issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenExpiration())).subject(user.getId())
                .claim(CurrentUserIdArgumentResolver.USER_ID_CLAIM, user.getId()).claim("email", user.getEmail())
                .claim("fullName", fullName).build();
        JwtEncoderParameters parameters = JwtEncoderParameters.from(claims);
        return jwtEncoder.encode(parameters).getTokenValue();
    }
}
