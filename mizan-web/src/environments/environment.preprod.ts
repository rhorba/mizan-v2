/**
 * Preprod on OCI k3s (Story 7.2): one origin, Traefik routes by path —
 * / web, /api API, /auth Keycloak, /otlp browser spans (same origin closes R-OTLP).
 */
const origin = 'https://144.24.200.83.sslip.io';

export const environment = {
  apiUrl: origin,
  /** OTLP/HTTP collector for browser spans (Story 6.1); empty turns browser tracing off. */
  otlpUrl: `${origin}/otlp`,
  auth: {
    issuer: `${origin}/auth/realms/mizan`,
    clientId: 'mizan-web',
  },
};
