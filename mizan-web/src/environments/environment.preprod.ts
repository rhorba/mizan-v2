/**
 * Preprod on OCI k3s (Story 7.2). The app origin serves / (web), /api and /otlp (browser spans,
 * same origin closes R-OTLP); Keycloak has its own host because http-relative-path is build-time.
 */
const origin = 'https://144.24.200.83.sslip.io';

export const environment = {
  apiUrl: origin,
  /** OTLP/HTTP collector for browser spans (Story 6.1); empty turns browser tracing off. */
  otlpUrl: `${origin}/otlp`,
  auth: {
    issuer: 'https://auth.144.24.200.83.sslip.io/realms/mizan',
    clientId: 'mizan-web',
  },
};
