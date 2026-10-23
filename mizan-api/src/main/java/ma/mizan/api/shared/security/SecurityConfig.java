package ma.mizan.api.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless OAuth2 resource server for Keycloak access tokens (ADR-3, docs/security §3–4).
 *
 * <p>CSRF protection is off because the API only accepts bearer tokens in the Authorization header
 * and never reads cookies, so a cross-site request cannot carry credentials.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http, ProblemDetailsAuthHandler problemDetails, SecurityProperties properties)
      throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("admin")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    // RFC 9728 metadata, advertised in WWW-Authenticate: point clients at Keycloak.
                    .protectedResourceMetadata(
                        metadata ->
                            metadata.protectedResourceMetadataCustomizer(
                                m ->
                                    m.authorizationServer(properties.issuerUri())
                                        .tlsClientCertificateBoundAccessTokens(false)))
                    .authenticationEntryPoint(problemDetails)
                    .accessDeniedHandler(problemDetails))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(problemDetails).accessDeniedHandler(problemDetails))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder(SecurityProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    decoder.setJwtValidator(
        AccessTokenValidators.create(properties.issuerUri(), properties.audience()));
    return decoder;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.corsAllowedOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
    return converter;
  }

  /** JSON only: no scripts, frames or embedding. */
  private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
}
