import { expect, test } from '@playwright/test';
import { verificationLink } from './support/mailpit';

const API_URL = process.env['E2E_API_URL'] ?? 'http://localhost:8080';
const PASSWORD = 'Mizan-e2e-Contrat-2026'; // gitleaks:allow (throwaway local E2E account)
/** E2E_PAUSE (ms) holds each step on screen for the sprint recording; 0 in CI. */
const PAUSE = Number(process.env['E2E_PAUSE'] ?? 0);

/**
 * Story 1.4 acceptance:
 *   Given the realm "mizan" with self-registration and verify-email on
 *   When a visitor signs up, verifies the e-mail and logs in from the SPA
 *   Then the SPA holds an access token in memory and GET /api/v1/me/quota returns 200
 *   And a call without a token returns 401, and a "user" token on /api/v1/admin/stats returns 403
 */
test('visitor signs up, verifies the e-mail, sees the quota, and logs out', async ({
  page,
  request,
}) => {
  const email = `e2e-${Date.now()}@mizan.test`;
  const step = async (title: string, body: () => Promise<void>) => {
    await test.step(title, body);
    if (PAUSE) {
      await page.waitForTimeout(PAUSE);
    }
  };

  await step('landing → "Essayer gratuitement" opens Keycloak sign-up', async () => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('avant de le signer');
    await page.getByRole('button', { name: 'Essayer gratuitement' }).click();
    await expect(page).toHaveURL(/localhost:8180\/realms\/mizan\/.*/);
  });

  // Keycloak 26.8 with verify-email: no password at sign-up; it is set after the e-mail is
  // verified (VERIFY_EMAIL + UPDATE_PASSWORD), so nobody can pre-claim someone else's address.
  await step('sign-up asks only for e-mail and name, then for e-mail verification', async () => {
    await page.fill('#email', email);
    await page.fill('#firstName', 'Karim');
    await page.fill('#lastName', 'E2E');
    await page.locator('[type=submit]').click();
    await expect(page.locator('body')).toContainText(email);
  });

  // The SPA's quota call is the only place the in-memory token is observable.
  const quotaRequest = page.waitForRequest((r) => r.url() === `${API_URL}/api/v1/me/quota`);

  await step('the link from the e-mail verifies the address and asks for a password', async () => {
    await page.goto(await verificationLink(request, email));
    await expect(page.locator('#password-new')).toBeVisible();
  });

  await step('a common password is refused (blacklist)', async () => {
    await page.fill('#password-new', 'password123');
    await page.fill('#password-confirm', 'password123');
    await page.locator('[type=submit]').click();
    await expect(page.getByText("ce mot de passe n'est pas autorisé")).toBeVisible();
  });

  await step('a strong password signs in and lands on /contracts with the quota', async () => {
    await page.fill('#password-new', PASSWORD);
    await page.fill('#password-confirm', PASSWORD);
    await page.locator('[type=submit]').click();
    await expect(page).toHaveURL('http://localhost:4200/contracts');
    await expect(page.locator('.quota-chip')).toHaveText("5 analyses restantes aujourd'hui");
  });

  const authorization = (await quotaRequest).headers()['authorization'];

  await step('tokens stay in memory, never in web storage', async () => {
    expect(authorization).toMatch(/^Bearer ey/);
    const stored = await page.evaluate(() =>
      [...Object.values(localStorage), ...Object.values(sessionStorage)].join(' '),
    );
    expect(stored).not.toContain(authorization.slice('Bearer '.length));
  });

  await step('API: 200 with the token, 401 without, 403 for a user on /admin', async () => {
    const quota = await request.get(`${API_URL}/api/v1/me/quota`, {
      headers: { Authorization: authorization },
    });
    expect(quota.status()).toBe(200);
    expect(await quota.json()).toEqual({ limit: 5, used: 0, remaining: 5 });

    const anonymous = await request.get(`${API_URL}/api/v1/me/quota`);
    expect(anonymous.status()).toBe(401);
    expect(anonymous.headers()['content-type']).toContain('application/problem+json');

    const admin = await request.get(`${API_URL}/api/v1/admin/stats`, {
      headers: { Authorization: authorization },
    });
    expect(admin.status()).toBe(403);
  });

  await step('Arabic: right-to-left with the Arabic plural', async () => {
    await page.getByRole('button', { name: 'العربية Afficher en arabe' }).click();
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.locator('.quota-chip')).toHaveText('5 تحليلات متبقية اليوم');
  });

  await step('logout ends the Keycloak session', async () => {
    await page.getByRole('button', { name: 'تسجيل الخروج' }).click();
    await expect(page).toHaveURL('http://localhost:4200/');
    await page.goto('/contracts');
    await expect(page.locator('#kc-login')).toBeVisible();
  });
});
