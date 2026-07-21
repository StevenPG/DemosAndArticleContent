package com.stevenpg.gateway.webmvc.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the rate-limiter key resolver — no Redis, no context. Verifies the
 * fairness policy: an authenticated caller is keyed by subject, everyone else by IP.
 */
class RateLimitConfigTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void keysBySubjectWhenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_USER")));

        String key = RateLimitConfig.resolveKey(request("203.0.113.7"));

        assertThat(key).isEqualTo("alice");
    }

    @Test
    void keysByClientIpWhenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        String key = RateLimitConfig.resolveKey(request("203.0.113.7"));

        assertThat(key).isEqualTo("203.0.113.7");
    }

    private static ServerRequest request(String remoteAddr) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/orders");
        servletRequest.setRemoteAddr(remoteAddr);
        return ServerRequest.create(servletRequest, java.util.List.of());
    }
}
