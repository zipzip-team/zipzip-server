package org.zipzip.zipzipserver.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class PublicEndpointRequestMatcher {

    private static final List<RequestMatcher> MATCHERS =
            List.of(
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.POST, "/api/v1/auth/apple"),
                    PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"),
                    PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
                    PathPatternRequestMatcher.withDefaults().matcher("/v3/api-docs/**"),
                    PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"));

    private PublicEndpointRequestMatcher() {}

    public static boolean matches(HttpServletRequest request) {
        return MATCHERS.stream().anyMatch(matcher -> matcher.matches(request));
    }
}
