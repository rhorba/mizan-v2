import { APIRequestContext, Page, expect } from '@playwright/test';
import { verificationLink } from './mailpit';

export const API_URL = process.env['E2E_API_URL'] ?? 'http://localhost:8080';
export const PASSWORD = 'Mizan-e2e-Contrat-2026'; // gitleaks:allow (throwaway local E2E account)

/**
 * Signs up a brand-new verified user through Keycloak (sign-up → e-mail link via Mailpit → set
 * password) and returns the bearer token the SPA sends, captured on the wire because tokens never
 * reach web storage. sign-up-and-login.spec.ts covers these steps one by one.
 */
export async function signUpNewUser(
  page: Page,
  request: APIRequestContext,
): Promise<{ email: string; authorization: string }> {
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 6)}@mizan.test`;
  await page.goto('/');
  await page.getByRole('button', { name: 'Essayer gratuitement' }).click();
  await page.fill('#email', email);
  await page.fill('#firstName', 'Fatima Zahra');
  await page.fill('#lastName', 'E2E');
  await page.locator('[type=submit]').click();
  await expect(page.locator('body')).toContainText(email);

  const quota = page.waitForRequest((r) => r.url() === `${API_URL}/api/v1/me/quota`);
  await page.goto(await verificationLink(request, email));
  await page.fill('#password-new', PASSWORD);
  await page.fill('#password-confirm', PASSWORD);
  await page.locator('[type=submit]').click();
  await expect(page).toHaveURL('http://localhost:4200/contracts');
  return { email, authorization: (await quota).headers()['authorization'] };
}
