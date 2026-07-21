package com.stevenpg.gateway.webflux.dev;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A DEMO-ONLY convenience: mints a valid HS256 access token signed with the same
 * shared secret the gateway validates against, so the whole demo is runnable with
 * curl and no external identity provider.
 *
 * <p><b>This is not how production works.</b> In production a dedicated
 * authorization server issues tokens and the gateway only ever <i>validates</i>
 * them. This controller only exists so you can do:
 * <pre>
 *   TOKEN=$(curl -s localhost:8080/dev/token | jq -r .access_token)
 *   curl -H "Authorization: Bearer $TOKEN" localhost:8080/orders
 * </pre>
 *
 * <p>It is annotated {@code @Profile("dev")} so it simply does not exist unless the
 * app is started with the {@code dev} profile (the run scripts do that for you).
 */
@RestController
@Profile("dev")
public class DevTokenController {

    private final JwtEncoder encoder;

    public DevTokenController(@Value("${demo.jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @GetMapping("/dev/token")
    public Map<String, String> token(@RequestParam(defaultValue = "demo-user") String sub) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("demo-gateway")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject(sub)
                .claim("scope", "orders.read orders.write")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "subject", sub,
                "expires_in", "3600");
    }
}
