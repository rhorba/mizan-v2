import { expect, test } from '@playwright/test';
import { grantAdmin } from './support/keycloak';
import { signInAgain, signUpNewUser } from './support/session';

/**
 * Sprint 3 against the real stack: only admins see and open the admin screens (statistics since
 * Story 5.2, then the failed-analyses DLT list). The real-model flow moved to
 * results-and-stats.spec.ts, which follows it live (Story 3.4) instead of reloading.
 */
test('only admins reach the statistics and failed analyses screens', async ({ page, request }) => {
  const step = test.step.bind(test);
  const { email } = await signUpNewUser(page, request);

  await step('a regular user has no admin link and is sent back from the page', async () => {
    await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0);
    await page.goto('/admin/dead-letters');
    await expect(page).toHaveURL('http://localhost:4200/contracts');
    await page.goto('/admin');
    await expect(page).toHaveURL('http://localhost:4200/contracts');
  });

  await step('granted the admin role, the user signs in again and sees the screen', async () => {
    await grantAdmin(request, email);
    await signInAgain(page, email);

    // Story 5.2: the admin landing page is the statistics overview.
    await page.getByRole('link', { name: 'Administration' }).click();
    await expect(page).toHaveURL('http://localhost:4200/admin');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Statistiques');
    await expect(page.locator('.tile')).toHaveCount(4);
    await expect(page.locator('svg .hit')).toHaveCount(14);
    await expect(page.locator('[role=alert]')).toHaveCount(0);

    await page.getByRole('link', { name: 'Analyses en échec (DLT)' }).click();
    await expect(page).toHaveURL('http://localhost:4200/admin/dead-letters');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Analyses en échec (DLT)');
    // Either the list or the empty state: both mean the API accepted the admin token.
    await expect(page.locator('table, .empty')).toBeVisible();
    await expect(page.locator('[role=alert]')).toHaveCount(0);
  });
});
