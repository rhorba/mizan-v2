import { APIRequestContext, expect } from '@playwright/test';

const KEYCLOAK = process.env['E2E_KEYCLOAK_URL'] ?? 'http://localhost:8180';
// Local compose defaults from .env.example; never a real environment.
const ADMIN_USER = process.env['KEYCLOAK_ADMIN'] ?? 'admin';
const ADMIN_PASSWORD = process.env['KEYCLOAK_ADMIN_PASSWORD'] ?? 'admin'; // gitleaks:allow

/**
 * Gives a test user the realm role "admin" through the Keycloak admin API, the way an operator
 * would in the console (the role is assigned manually only). Takes effect at the next login.
 */
export async function grantAdmin(request: APIRequestContext, email: string): Promise<void> {
  const token = await request.post(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
    form: {
      grant_type: 'password',
      client_id: 'admin-cli',
      username: ADMIN_USER,
      password: ADMIN_PASSWORD,
    },
  });
  expect(token.ok()).toBe(true);
  const headers = { authorization: `Bearer ${(await token.json()).access_token}` };
  const base = `${KEYCLOAK}/admin/realms/mizan`;

  const users = await request.get(`${base}/users`, {
    headers,
    params: { email, exact: 'true' },
  });
  const [user] = await users.json();
  const role = await (await request.get(`${base}/roles/admin`, { headers })).json();
  const grant = await request.post(`${base}/users/${user.id}/role-mappings/realm`, {
    headers,
    data: [role],
  });
  expect(grant.ok()).toBe(true);
}
