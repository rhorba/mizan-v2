import { expect, test } from '@playwright/test';
import { textPdf } from './support/files';
import { grantAdmin } from './support/keycloak';
import { PASSWORD, signUpNewUser } from './support/session';

/**
 * Sprint 3 against the real stack (compose profile "apps" with ANTHROPIC_API_KEY set):
 *   an uploaded contract is analyzed by Claude through Kafka and its status turns COMPLETE;
 *   only admins see and open the failed-analyses (DLT) screen.
 * The first test makes one real model call (a few cents on Opus), so it only runs with E2E_CLAUDE=1
 * and a real key in .env; CI never calls Claude.
 */

const CONTRACT =
  'CONTRAT DE PRESTATION. Le Prestataire realise un site web pour 20 000 MAD. ' +
  'Paiement a 120 jours. Penalite de 5 % du prix par jour de retard, sans plafond. ' +
  'Le Client peut resilier a tout moment sans preavis ni indemnite.';

test('an uploaded contract is analyzed by Claude and shows as complete', async ({
  page,
  request,
}) => {
  test.skip(!process.env['E2E_CLAUDE'], 'opt-in: E2E_CLAUDE=1 with ANTHROPIC_API_KEY in .env');
  test.setTimeout(240_000);
  const step = test.step.bind(test);
  await signUpNewUser(page, request);

  await step('upload a French contract', async () => {
    await page.getByRole('link', { name: 'Analyser un contrat' }).click();
    await page.locator('input[type=file]').setInputFiles(textPdf('Prestation web.pdf', CONTRACT));
    await page.getByRole('button', { name: 'Analyser' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Prestation web.pdf');
  });

  await step('the history shows it pending, then complete once the analyzer answers', async () => {
    await page.getByRole('link', { name: 'Mes contrats' }).first().click();
    const row = page.locator('.history .row', { hasText: 'Prestation web.pdf' });
    await expect(row).toBeVisible();
    // No live status before Story 3.4: reload until the result is applied.
    await expect
      .poll(
        async () => {
          await page.reload();
          return (await row.locator('app-status-chip').textContent())?.trim();
        },
        { timeout: 180_000, intervals: [5_000] },
      )
      .toBe('Terminée');
  });
});

test('only admins reach the failed analyses screen', async ({ page, request }) => {
  const step = test.step.bind(test);
  const { email } = await signUpNewUser(page, request);

  await step('a regular user has no admin link and is sent back from the page', async () => {
    await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0);
    await page.goto('/admin/dead-letters');
    await expect(page).toHaveURL('http://localhost:4200/contracts');
  });

  await step('granted the admin role, the user signs in again and sees the screen', async () => {
    await grantAdmin(request, email);
    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page).toHaveURL('http://localhost:4200/');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await page.fill('#username', email);
    await page.fill('#password', PASSWORD);
    await page.locator('[type=submit]').click();
    await expect(page).toHaveURL('http://localhost:4200/contracts');

    await page.getByRole('link', { name: 'Administration' }).click();
    await expect(page).toHaveURL('http://localhost:4200/admin/dead-letters');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Analyses en échec (DLT)');
    // Either the list or the empty state: both mean the API accepted the admin token.
    await expect(page.locator('table, .empty')).toBeVisible();
    await expect(page.locator('[role=alert]')).toHaveCount(0);
  });
});
