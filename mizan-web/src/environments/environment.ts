/** Local development (docker compose + ng serve). Preprod values arrive with Story 7.2. */
export const environment = {
  apiUrl: 'http://localhost:8080',
  auth: {
    issuer: 'http://localhost:8180/realms/mizan',
    clientId: 'mizan-web',
  },
};
