/** Local development (docker compose + ng serve). Preprod values arrive with Story 7.2. */
export const environment = {
  apiUrl: 'http://localhost:8080',
  /** OTLP/HTTP collector for browser spans (Story 6.1); empty turns browser tracing off. */
  otlpUrl: 'http://localhost:4318',
  auth: {
    issuer: 'http://localhost:8180/realms/mizan',
    clientId: 'mizan-web',
  },
};
