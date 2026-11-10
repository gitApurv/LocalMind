package com.localmind.common.security;

import com.localmind.common.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUserId} from the JWT {@code userId} claim, falling back to the token subject.
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_CLAIM = "userId";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public String resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return resolveUserId(SecurityContextHolder.getContext().getAuthentication());
    }

    private static String resolveUserId(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedException("No authenticated user in security context");
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString(USER_ID_CLAIM);
            return userId != null && !userId.isBlank() ? userId : jwt.getSubject();
        }
        if (authentication.getName() != null) {
            return authentication.getName();
        }
        throw new UnauthorizedException("No authenticated user in security context");
    }
}
