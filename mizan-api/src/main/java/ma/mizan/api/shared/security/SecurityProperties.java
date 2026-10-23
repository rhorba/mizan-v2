package ma.mizan.api.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token and CORS settings, bound from {@code mizan.security.*}.
 *
 * @param issuerUri expected {@code iss} claim, e.g. http://localhost:8180/realms/mizan
 * @param jwkSetUri where signing keys are fetched (may differ from the issuer host inside Compose)
 * @param audience expected {@code aud} value (the Keycloak audience mapper adds it)
 * @param corsAllowedOrigins browser origins allowed to call /api/**
 */
@ConfigurationProperties("mizan.security")
record SecurityProperties(
    String issuerUri, String jwkSetUri, String audience, List<String> corsAllowedOrigins) {}
